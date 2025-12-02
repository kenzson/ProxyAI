package ee.carlrobert.codegpt.codecompletions

import com.intellij.codeInsight.inline.completion.InlineCompletionInsertEnvironment
import com.intellij.codeInsight.inline.completion.InlineCompletionInsertHandler
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import ee.carlrobert.codegpt.CodeGPTKeys
import ee.carlrobert.codegpt.autoimport.AutoImportOrchestrator
import ee.carlrobert.codegpt.codecompletions.edit.GrpcClientService
import ee.carlrobert.codegpt.nextedit.NextEditCoordinator
import ee.carlrobert.codegpt.nextedit.NextEditDiffViewer
import ee.carlrobert.service.NextEditResponse
import kotlinx.coroutines.*

class CodeCompletionInsertHandler : InlineCompletionInsertHandler {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun afterInsertion(
        environment: InlineCompletionInsertEnvironment,
        elements: List<InlineCompletionElement>
    ) {
        coroutineScope.launch {
            val completion = elements.first().text
            val editor = environment.editor
            acceptCompletion(completion, editor)

            val currentContent = runReadAction { editor.document.text }
            val caretOffset = runReadAction { editor.caretModel.offset }

            val completionRange = runReadAction { editor.getUserData(CodeGPTKeys.RECENT_COMPLETION_RANGE) }
            val contentWithImports = AutoImportOrchestrator.previewImports(editor, completionRange)
            if (contentWithImports == null) {
                NextEditCoordinator.requestNextEdit(editor, currentContent, caretOffset, false)
            } else {
                withContext(Dispatchers.EDT) {
                    if (contentWithImports.isNotEmpty() && contentWithImports != currentContent) {
                        showImportDiffViewer(editor, currentContent, contentWithImports)
                    }
                }
            }
        }
    }

    private fun acceptCompletion(completion: String, editor: Editor) {
        val caretOffset = runReadAction { editor.caretModel.offset }
        val startLine = runReadAction { editor.document.getLineNumber(caretOffset - completion.length) }
        val start = runReadAction { editor.document.getLineStartOffset(startLine) }
        CodeGPTKeys.RECENT_COMPLETION_TEXT.set(editor, completion)
        CodeGPTKeys.RECENT_COMPLETION_RANGE.set(editor, TextRange(start, caretOffset))

        val responseId = CodeGPTKeys.LAST_COMPLETION_RESPONSE_ID.get(editor)
        if (responseId != null) {
            editor.project?.service<GrpcClientService>()
                ?.acceptCodeCompletion(responseId, completion)
            CodeGPTKeys.LAST_COMPLETION_RESPONSE_ID.set(editor, null)
        }
    }

    private fun showImportDiffViewer(
        editor: Editor,
        oldRevision: String,
        contentWithImports: String
    ) {
        val importResponse = NextEditResponse.newBuilder()
            .setId("import-${System.currentTimeMillis()}")
            .setOldRevision(oldRevision)
            .setNextRevision(contentWithImports)
            .build()

        NextEditDiffViewer.displayNextEdit(editor, importResponse)
    }
}
