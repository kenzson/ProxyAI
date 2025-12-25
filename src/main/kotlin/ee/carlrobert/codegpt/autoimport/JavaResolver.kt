package ee.carlrobert.codegpt.autoimport

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

internal class JavaResolver : AutoImportResolver {
    override fun supports(file: PsiFile): Boolean = file is PsiJavaFile

    override fun getUnresolvedImports(
        file: PsiFile,
        searchRange: TextRange
    ): Map<UnresolvedSymbol, List<String>> {
        val result = mutableMapOf<UnresolvedSymbol, List<String>>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceElement(referenceElement: PsiJavaCodeReferenceElement) {
                super.visitReferenceElement(referenceElement)

                if (!isInRange(referenceElement as PsiReference, searchRange)) return

                val resolved = runReadAction { referenceElement.resolve() }
                if (resolved == null) {
                    val name = referenceElement.referenceName ?: referenceElement.canonicalText
                    if (name.isNotBlank()) {
                        val range = referenceElement.textRange ?: TextRange.EMPTY_RANGE
                        val symbol = UnresolvedSymbol(name, referenceElement as PsiReference, range)
                        result.put(symbol, bestCandidatesFor(symbol, file))
                    }
                }
            }
        })
        return result
    }

    override fun applyImport(file: PsiFile, importFqn: String): Boolean {
        if (file !is PsiJavaFile) return false

        val project = file.project
        val cls = project.service<JavaPsiFacade>().findClass(importFqn, file.resolveScope)
            ?: return false
        val javaCodeStyleManager = project.service<JavaCodeStyleManager>()
        val added = javaCodeStyleManager.addImport(file, cls)
        if (added) {
            val importList = file.importList
            if (importList != null) {
                val endOffset = importList.nextSibling?.textRange?.startOffset?.let { it + 1 }
                    ?: importList.textRange.endOffset

                CodeStyleManager.getInstance(project).reformatRange(
                    file,
                    importList.textRange.startOffset,
                    endOffset.coerceAtMost(file.textRange.endOffset)
                )
            }
        }
        return added
    }

    private fun bestCandidatesFor(symbol: UnresolvedSymbol, file: PsiFile): List<String> {
        if (file !is PsiJavaFile) return emptyList()
        val project = file.project
        val scope = GlobalSearchScope.allScope(project)
        val classes = runReadAction {
            PsiShortNamesCache.getInstance(project).getClassesByName(symbol.name, scope)
        }

        val alreadyImported: Set<String> = buildSet {
            val list = runReadAction { file.importList }
            list?.allImportStatements?.forEach { stmt ->
                val qn = runReadAction { stmt.importReference?.qualifiedName }
                if (qn != null) add(qn)
            }
        }
        val currentPackage = runReadAction { file.packageName }

        return classes.mapNotNull { runReadAction { it.qualifiedName } }
            .filter { qn ->
                val pkg = qn.substringBeforeLast('.', "")
                pkg != currentPackage && qn !in alreadyImported
            }
            .sortedByDescending { fqn -> getPackageScore(fqn) }
            .distinct()
    }

    private fun getPackageScore(fqn: String): Int {
        return when {
            fqn.startsWith("java.") -> 100
            fqn.startsWith("javax.") -> 90
            fqn.startsWith("org.springframework.") -> 80
            else -> 0
        }
    }

    private fun isInRange(ref: PsiReference, searchRange: TextRange): Boolean {
        val refRange = runReadAction { ref.element.textRange } ?: return false
        return refRange.intersects(searchRange)
    }
}
