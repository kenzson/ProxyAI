package ee.carlrobert.codegpt.codecompletions

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.editor.Editor
import ee.carlrobert.codegpt.CodeGPTKeys.REMAINING_EDITOR_COMPLETION

class CodeCompletionSuggestionUpdateAdapter :
    InlineCompletionSuggestionUpdateManager.Default() {

    override fun onDocumentChange(
        event: InlineCompletionEvent.DocumentChange,
        variant: InlineCompletionVariant.Snapshot
    ): UpdateResult {
        updateRemainingCompletion(event.editor, event.typing.typed)
        return super.onDocumentChange(event, variant)
    }

    private fun updateRemainingCompletion(editor: Editor, textToInsert: String) {
        val remainingCompletion = REMAINING_EDITOR_COMPLETION.get(editor) ?: ""
        if (remainingCompletion.isNotEmpty()) {
            REMAINING_EDITOR_COMPLETION.set(editor, remainingCompletion.removePrefix(textToInsert))
        }
    }
}