package ee.carlrobert.codegpt.autoimport

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference

data class UnresolvedSymbol(
    val name: String,
    val reference: PsiReference,
    val range: TextRange,
)

data class ImportCandidate(
    val fqn: String,
)

object AutoImportOrchestrator {

    private val logger = thisLogger()

    private val resolvers: List<AutoImportResolver> = listOf(
        JavaResolver(),
        KtResolver(),
    )

    /**
     * Preview imports by cloning the current editor file's PSI, finding unresolved imports within [range],
     * applying the best candidates to the clone, and returning the resulting content and list of added import FQNs.
     * Note: This does not modify the original editor file.
     */
    fun previewImports(editor: Editor, range: TextRange? = null): String? {
        val project = editor.project ?: return null
        val psiFile =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        val clonedPsiFile = psiFile.copy() as? PsiFile ?: return null
        val rangeToUse = range ?: runReadAction { TextRange(0, editor.document.textLength) }
        val resolver = resolvers.firstOrNull { it.supports(clonedPsiFile) } ?: return null

        runReadAction { resolver.getUnresolvedImports(psiFile, rangeToUse) }
            .forEach {
                WriteCommandAction.runWriteCommandAction(project) {
                    if (!resolver.applyImport(clonedPsiFile, it)) {
                        logger.warn("Failed to apply import: $it")
                    }
                }
            }

        return runReadAction { clonedPsiFile.text }
    }
}
