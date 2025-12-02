package ee.carlrobert.codegpt.autoimport

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.ImportPath

internal class KtResolver : AutoImportResolver {
    override fun supports(file: PsiFile): Boolean = file is KtFile

    override fun getUnresolvedImports(
        file: PsiFile,
        searchRange: TextRange
    ): List<String> {
        val result = mutableListOf<String>()
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (element is KtReferenceExpression) {
                    val references = element.references
                    if (references.isEmpty()) return
                    references.forEach { ref ->
                        if (!isInRange(ref, searchRange)) return@forEach

                        if (ref.resolve() == null) {
                            val name = ref.canonicalText.substringAfterLast('.')
                            if (name.isNotBlank()) {
                                val range = element.textRange ?: TextRange.EMPTY_RANGE
                                val symbol = UnresolvedSymbol(name, ref, range)
                                bestCandidateFor(symbol, file)?.let {
                                    result.add(it)
                                }
                            }
                        }
                    }
                }
            }
        })
        return result.distinctBy { it }
    }

    override fun applyImport(file: PsiFile, importFqn: String): Boolean {
        if (file !is KtFile) return false

        val importList = file.importList ?: return false
        if (file.importDirectives.any { !it.isAllUnder && it.importedFqName?.asString() == importFqn }) return false

        val project = file.project
        val directive = KtPsiFactory(project).createImportDirective(
            ImportPath(FqName(importFqn), false)
        )
        importList.add(directive)

        val endOffset = importList.nextSibling?.textRange?.startOffset?.let { it + 1 }
            ?: importList.textRange.endOffset

        CodeStyleManager.getInstance(project).reformatRange(
            file,
            importList.textRange.startOffset,
            endOffset.coerceAtMost(file.textRange.endOffset)
        )

        return true
    }

    private fun bestCandidateFor(symbol: UnresolvedSymbol, file: PsiFile): String? {
        if (file !is KtFile) return null
        val project = file.project
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val name = symbol.name
        val classes = facade.findClasses(name, scope)
        val alreadyImported = file.importDirectives
            .mapNotNull { if (!it.isAllUnder) it.importedFqName?.asString() else null }
            .toSet()

        val fromJavaFacade = classes.mapNotNull { it.qualifiedName }

        val fromShortNamesCache = PsiShortNamesCache.getInstance(project)
            .getClassesByName(name, scope)
            .mapNotNull { it.qualifiedName }

        val fqns = (fromJavaFacade + fromShortNamesCache)
            .filter { qn -> qn !in alreadyImported }
            .distinct()
            .sorted()

        return fqns.asSequence().firstOrNull()
    }

    private fun isInRange(ref: PsiReference, searchRange: TextRange?): Boolean {
        searchRange ?: return true
        val range = ref.element.textRange ?: return false
        return range.intersects(searchRange)
    }
}
