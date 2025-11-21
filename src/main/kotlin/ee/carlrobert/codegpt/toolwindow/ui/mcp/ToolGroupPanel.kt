package ee.carlrobert.codegpt.toolwindow.ui.mcp

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import ee.carlrobert.llm.client.openai.completion.response.ToolCall
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

class ToolGroupPanel(
    private val toolCalls: List<Pair<ToolCall, String>>,
    private val onApprove: (ToolCall, CompactToolPanel, Boolean) -> Unit,
    private val onReject: (ToolCall, CompactToolPanel) -> Unit,
    private val onBatchApprove: (List<CompactToolPanel>, autoApprove: Boolean) -> Unit,
    private val onBatchReject: (List<CompactToolPanel>) -> Unit
) : JBPanel<ToolGroupPanel>() {

    private val toolPanels = mutableListOf<CompactToolPanel>()
    private val containerPanel = JBPanel<JBPanel<*>>()

    companion object {
        private const val TOOL_REQUESTS_TEXT = "tool requests from ProxyAI"
        private const val APPROVE_ALL_TEXT = "Approve all"
        private const val REJECT_ALL_TEXT = "Reject all"
        private const val SEPARATOR_TEXT = " | "
    }

    init {
        layout = BorderLayout()
        isOpaque = false
        border = JBUI.Borders.empty()

        setupUI()
    }

    private fun removeToolPanel(toolCall: ToolCall) {
        toolPanels.find { it.toolCallId == toolCall.id }?.let { panel ->
            containerPanel.components
                .filterIsInstance<JPanel>()
                .find { wrapper -> wrapper.components.contains(panel) }
                ?.let { wrapperPanel ->
                    containerPanel.remove(wrapperPanel)
                    containerPanel.revalidate()
                    containerPanel.repaint()
                }
        }
    }

    private fun cleanupGroupPanel() {
        toolPanels.forEach { it.isVisible = false }
        containerPanel.removeAll()
        containerPanel.revalidate()
        containerPanel.repaint()

        if (componentCount > 0) {
            getComponent(0).isVisible = false
        }
    }

    private fun removeSelfFromParent() {
        isVisible = false
        parent?.apply {
            remove(this@ToolGroupPanel)
            revalidate()
            repaint()
        }
    }

    private fun setupUI() {
        if (toolCalls.size > 1) {
            add(createBatchActionsHeader(), BorderLayout.NORTH)
        }

        containerPanel.apply {
            layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 4, true, false)
            isOpaque = false
            border = JBUI.Borders.empty()
        }

        toolCalls.forEach { (toolCall, serverName) ->
            lateinit var panel: CompactToolPanel

            panel = CompactToolPanel(
                toolCall = toolCall,
                serverName = serverName,
                onApprove = { autoApprove ->
                    handleIndividualApproval(toolCall, panel, autoApprove)
                },
                onReject = {
                    handleIndividualRejection(toolCall, panel)
                },
            )

            toolPanels.add(panel)
            containerPanel.add(wrapInBorder(panel))
        }

        add(containerPanel, BorderLayout.CENTER)
    }

    private fun createBatchActionsHeader(): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            isOpaque = false
            border = JBUI.Borders.empty(0, 0, 8, 0)

            val contentPanel = JBPanel<JBPanel<*>>().apply {
                layout = FlowLayout(FlowLayout.LEFT, 0, 0)
                isOpaque = false
            }

            contentPanel.add(JBLabel("${toolCalls.size} $TOOL_REQUESTS_TEXT").apply {
                font = JBUI.Fonts.label()
                foreground = JBUI.CurrentTheme.Label.foreground()
            })

            contentPanel.add(Box.createHorizontalStrut(12))

            actions {
                action(APPROVE_ALL_TEXT) { handleBatchApproval() }
                separator()
                action(REJECT_ALL_TEXT) { handleBatchRejection() }
            }.forEach { contentPanel.add(it) }

            add(contentPanel, BorderLayout.CENTER)
        }
    }

    private fun actions(block: ActionLinksBuilder.() -> Unit): List<JComponent> {
        return ActionLinksBuilder().apply(block).build()
    }

    private class ActionLinksBuilder {
        private val components = mutableListOf<JComponent>()

        fun action(text: String, action: () -> Unit) {
            components.add(ActionLink(text) { action() })
        }

        fun separator() {
            components.add(JBLabel(SEPARATOR_TEXT).apply {
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
        }

        fun build(): List<JComponent> = components
    }

    private fun wrapInBorder(panel: CompactToolPanel): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(4, 0)
            )
            add(panel, BorderLayout.CENTER)
        }
    }

    private fun handleIndividualApproval(toolCall: ToolCall, panel: CompactToolPanel, autoApprove: Boolean) {
        onApprove(toolCall, panel, autoApprove)
        updateHeader()

        if (toolPanels.all { it.isCompleted }) {
            removeSelfFromParent()
        }
    }

    private fun handleIndividualRejection(toolCall: ToolCall, panel: CompactToolPanel) {
        onReject(toolCall, panel)
        removeToolPanel(toolCall)
        updateHeader()

        if (toolPanels.all { it.isCompleted }) {
            removeSelfFromParent()
        }
    }

    private fun handleBatchApproval() {
        onBatchApprove(toolPanels, false)
        updateHeader()

        if (toolPanels.all { it.isCompleted }) {
            removeSelfFromParent()
        }
    }

    private fun handleBatchRejection() {
        cleanupGroupPanel()
        onBatchReject(toolPanels)
        updateHeader()
        removeSelfFromParent()
    }

    private fun updateHeader() {
        if (componentCount == 0) return

        (getComponent(0) as? JPanel)?.let { headerPanel ->
            val remainingCount = toolPanels.count { !it.isCompleted }

            headerPanel.isVisible = remainingCount > 1

            if (remainingCount > 1) {
                (headerPanel.getComponent(0) as? JPanel)?.let { contentPanel ->
                    (contentPanel.getComponent(0) as? JBLabel)?.text = "$remainingCount $TOOL_REQUESTS_TEXT"
                }
            }
        }
    }
}