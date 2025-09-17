package ee.carlrobert.codegpt.inlineedit

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import ee.carlrobert.codegpt.CodeGPTKeys
import ee.carlrobert.codegpt.ReferencedFile
import ee.carlrobert.codegpt.codecompletions.CompletionProgressNotifier
import ee.carlrobert.codegpt.completions.CompletionRequestService
import ee.carlrobert.codegpt.completions.InlineEditCompletionParameters
import ee.carlrobert.codegpt.conversations.Conversation
import ee.carlrobert.codegpt.ui.InlineEditPopover
import ee.carlrobert.codegpt.ui.ObservableProperties
import ee.carlrobert.codegpt.ui.OverlayUtil
import okhttp3.sse.EventSource
import java.util.concurrent.atomic.AtomicReference

class InlineEditSubmissionHandler(
    private val editor: Editor,
    private val observableProperties: ObservableProperties,
) {

    private val previousSourceRef = AtomicReference<String?>(null)
    private val previousPromptRef = AtomicReference<String?>(null)
    private val currentEventSourceRef = AtomicReference<EventSource?>(null)
    private val logger = Logger.getInstance(InlineEditSubmissionHandler::class.java)

    fun handleSubmit(
        userPrompt: String,
        referencedFiles: List<ReferencedFile>? = null,
        gitDiff: String? = null,
        conversationHistory: List<Conversation>? = null,
        diagnosticsInfo: String? = null
    ) {
        editor.project?.let {
            CompletionProgressNotifier.Companion.update(it, true)
        }

        observableProperties.loading.set(true)
        observableProperties.submitted.set(true)

        previousPromptRef.getAndSet(userPrompt)
        previousSourceRef.getAndSet(editor.document.text)

        runInEdt { editor.selectionModel.removeSelection() }

        val file = FileDocumentManager.getInstance().getFile(editor.document)
        val editorEx = editor as? EditorEx ?: return
        val parameters = InlineEditCompletionParameters(
            userPrompt,
            runReadAction { editor.selectionModel.selectedText },
            file?.path,
            file?.extension,
            editor.project?.basePath,
            referencedFiles,
            gitDiff,
            InlineEditConversationManager.getOrCreate(editorEx),
            conversationHistory,
            diagnosticsInfo
        )

        val requestId = System.nanoTime()
        editorEx.putUserData(InlineEditSearchReplaceListener.REQUEST_ID_KEY, requestId)

        val listener = InlineEditSearchReplaceListener(
            editorEx,
            observableProperties,
            TextRange(
                runReadAction { editor.selectionModel.selectionStart },
                runReadAction { editor.selectionModel.selectionEnd },
            ),
            requestId,
            userPrompt
        )

        editorEx.putUserData(InlineEditSearchReplaceListener.LISTENER_KEY, listener)

        listener.showHint("Submitting inline edit…")

        editorEx.getUserData(InlineEditPopover.Companion.POPOVER_KEY)?.apply {
            setInlineEditControlsVisible(false)
            setThinkingVisible(true)
        }

        try {
            currentEventSourceRef.getAndSet(null)?.cancel()

            val eventSource = service<CompletionRequestService>().getInlineEditCompletionAsync(
                parameters,
                listener
            )
            currentEventSourceRef.set(eventSource)

        } catch (ex: Exception) {
            logger.warn("InlineEdit: request dispatch failed", ex)
            runInEdt {
                OverlayUtil.showNotification(
                    ex.message ?: "Inline Edit request failed",
                    NotificationType.ERROR
                )
                observableProperties.loading.set(false)
                observableProperties.submitted.set(false)
                editorEx.getUserData(InlineEditPopover.Companion.POPOVER_KEY)
                    ?.setThinkingVisible(false)
            }
        }
    }

    fun handleReject(clearConversation: Boolean = false) {
        cancelActiveRequest()
        (editor as? EditorEx)?.getUserData(InlineEditPopover.Companion.POPOVER_KEY)?.setThinkingVisible(false)
        val prevSource = previousSourceRef.get()
        if (!observableProperties.accepted.get() && prevSource != null) {
            revertAllChanges(prevSource)
        }

        restorePreviousPrompt()
        runInEdt {
            val editorEx = editor as? EditorEx
            editorEx?.getUserData(CodeGPTKeys.EDITOR_INLINE_EDIT_SESSION)?.dispose()
            editorEx?.putUserData(CodeGPTKeys.EDITOR_INLINE_EDIT_SESSION, null)
            editorEx?.getUserData(CodeGPTKeys.EDITOR_INLINE_EDIT_RENDERER)?.dispose()
            editorEx?.putUserData(CodeGPTKeys.EDITOR_INLINE_EDIT_RENDERER, null)
            if (clearConversation) {
                editorEx?.let { InlineEditConversationManager.clear(it) }
            }
            editorEx?.putUserData(InlineEditSearchReplaceListener.LISTENER_KEY, null)

            observableProperties.loading.set(false)
            observableProperties.submitted.set(false)
            editor.project?.let { project ->
                CompletionProgressNotifier.Companion.update(project, false)
            }
            editorEx?.getUserData(InlineEditPopover.Companion.POPOVER_KEY)?.onCompletionFinished()
        }
    }

    private fun cancelActiveRequest() {
        val editorEx = editor as? EditorEx
        val newRequestId = System.nanoTime()
        editorEx?.putUserData(InlineEditSearchReplaceListener.REQUEST_ID_KEY, newRequestId)

        currentEventSourceRef.getAndSet(null)?.cancel()
    }

    private fun revertAllChanges(prevSource: String) {
        editor.project?.let { project ->
            WriteCommandAction.runWriteCommandAction(project) {
                editor.document.replaceString(
                    0,
                    editor.document.textLength,
                    StringUtil.convertLineSeparators(prevSource)
                )
            }
        }
    }

    fun restorePreviousPrompt() {
        val prevPrompt = previousPromptRef.get()
        if (prevPrompt != null) {
            (editor as? EditorEx)
                ?.getUserData(InlineEditPopover.Companion.POPOVER_KEY)
                ?.restorePromptAndFocus(prevPrompt)
        }
    }

}