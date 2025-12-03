package ee.carlrobert.codegpt.autoimport

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

interface AutoImportResolver {
    fun supports(file: PsiFile): Boolean
    fun getUnresolvedImports(file: PsiFile, searchRange: TextRange): Map<UnresolvedSymbol, List<String>>
    fun applyImport(file: PsiFile, importFqn: String): Boolean
}
