package ee.carlrobert.codegpt.codecompletions

import com.intellij.codeInsight.inline.completion.InlineCompletionProviderPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import ee.carlrobert.codegpt.Icons
import javax.swing.JComponent
import javax.swing.SwingConstants

class CodeCompletionProviderPresentation : InlineCompletionProviderPresentation {

    override fun getTooltip(project: Project?): JComponent {
        val selectedModelCode =
            project?.service<CodeCompletionService>()?.getSelectedModelCode() ?: ""
        val text = if (selectedModelCode.isNotEmpty()) {
            buildString {
                append("<html>Model: (<strong>$selectedModelCode</strong>)</html>")
            }
        } else {
            "ProxyAI"
        }

        return JBLabel(text, Icons.DefaultSmall, SwingConstants.LEADING)
    }
}