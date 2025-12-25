package ee.carlrobert.codegpt

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.util.TextRange
import ee.carlrobert.codegpt.codecompletions.CodeCompletionService
import ee.carlrobert.codegpt.nextedit.NextEditCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CodeGPTLookupListener : LookupManagerListener {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
        if (newLookup is LookupImpl) {
            newLookup.addLookupListener(object : LookupListener {
                override fun beforeItemSelected(event: LookupEvent): Boolean {
                    return true
                }

                override fun itemSelected(event: LookupEvent) {
                    val editor = newLookup.editor
                    if (!service<CodeCompletionService>().isCodeCompletionsEnabled()
                        || editor.editorKind != EditorKind.MAIN_EDITOR
                    ) {
                        return
                    }
                    val offset = editor.caretModel.offset
                    val lineNumber = editor.document.getLineNumber(offset)
                    val lineEndOffset = editor.document.getLineEndOffset(lineNumber)

                    if (editor.document.getText(TextRange(offset, lineEndOffset)).isEmpty()) {
                        InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(
                            InlineCompletionEvent.DirectCall(editor, editor.caretModel.currentCaret)
                        )
                    } else {
                        coroutineScope.launch {
                            NextEditCoordinator.requestNextEdit(editor, editor.document.text, offset)
                        }
                    }
                }
            })
        }
    }
}
