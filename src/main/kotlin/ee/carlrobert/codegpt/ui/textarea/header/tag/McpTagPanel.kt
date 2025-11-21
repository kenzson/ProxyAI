package ee.carlrobert.codegpt.ui.textarea.header.tag

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ee.carlrobert.codegpt.mcp.ConnectionStatus
import ee.carlrobert.codegpt.ui.textarea.PromptTextField
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.SwingUtilities
import javax.swing.Timer

class McpTagPanel(
    mcpTagDetails: McpTagDetails,
    private val tagManager: TagManager,
    private val promptTextField: PromptTextField,
    project: Project,
) : TagPanel(mcpTagDetails, true, project) {

    private val statusIndicator = StatusIndicator()

    init {
        updateMcpTagDisplay()
        setupTooltip()
        addStatusIndicator()
    }

    override fun onSelect(tagDetails: TagDetails) {
        promptTextField.requestFocus()
    }

    override fun onClose() {
        tagManager.remove(tagDetails)
    }

    override fun getAdditionalWidth(): Int {
        return 16
    }

    fun updateMcpTag(newTagDetails: McpTagDetails) {
        val updateAction = {
            tagDetails = newTagDetails

            updateMcpTagDisplay()

            toolTipText = null
            SwingUtilities.invokeLater {
                toolTipText = newTagDetails.getTooltipText()
            }

            statusIndicator.updateStatus(
                newTagDetails.getStatusColor(),
                newTagDetails.connectionStatus
            )

            invalidate()
            revalidate()
            repaint()

            parent?.let { parent ->
                parent.invalidate()
                parent.revalidate()
                parent.repaint()

                Timer(50) {
                    parent.revalidate()
                    parent.repaint()
                }.apply {
                    isRepeats = false
                    start()
                }
            }

        }

        if (SwingUtilities.isEventDispatchThread()) {
            updateAction()
        } else {
            invokeLater {
                updateAction()
            }
        }
    }

    private fun updateMcpTagDisplay() {
        val mcpTag = tagDetails as McpTagDetails
        update(mcpTag.getDisplayName(), mcpTag.icon)
    }

    private fun setupTooltip() {
        val mcpTag = tagDetails as McpTagDetails
        val newTooltipText = mcpTag.getTooltipText()
        toolTipText = newTooltipText
    }

    private fun addStatusIndicator() {
        val mcpTag = tagDetails as McpTagDetails
        statusIndicator.updateStatus(mcpTag.getStatusColor(), mcpTag.connectionStatus)

        val gbc = GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
            insets = JBUI.insets(0, 4, 0, 2)
        }
        add(statusIndicator, gbc)
    }

    private class StatusIndicator : JBLabel() {
        private var statusColor: Color = JBColor.GRAY
        private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private var pulseTimer: Timer? = null
        private var pulseAlpha = 1.0f
        private var pulseDirection = -0.05f

        init {
            preferredSize = Dimension(10, 10)
            minimumSize = Dimension(10, 10)
            maximumSize = Dimension(10, 10)
            isOpaque = false
            border = BorderFactory.createEmptyBorder()
        }

        fun updateStatus(color: Color, status: ConnectionStatus) {
            statusColor = color
            connectionStatus = status

            pulseTimer?.stop()
            pulseTimer = null
            pulseAlpha = 1.0f

            if (status == ConnectionStatus.CONNECTING) {
                pulseTimer = Timer(50) {
                    pulseAlpha += pulseDirection
                    if (pulseAlpha <= 0.3f || pulseAlpha >= 1.0f) {
                        pulseDirection = -pulseDirection
                    }
                    repaint()
                }.apply { start() }
            }

            invalidate()
            repaint()

            parent?.repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g.create() as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            if (connectionStatus == ConnectionStatus.CONNECTING) {
                g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulseAlpha)
            }

            if (connectionStatus == ConnectionStatus.CONNECTED ||
                connectionStatus == ConnectionStatus.ERROR
            ) {
                g2d.color = Color(statusColor.red, statusColor.green, statusColor.blue, 50)
                val glowSize = 8
                val glowX = (width - glowSize) / 2
                val glowY = (height - glowSize) / 2
                g2d.fillOval(glowX, glowY, glowSize, glowSize)
            }

            g2d.color = statusColor
            val dotSize = 6
            val x = (width - dotSize) / 2
            val y = (height - dotSize) / 2
            g2d.fillOval(x, y, dotSize, dotSize)

            g2d.color = statusColor.darker()
            g2d.stroke = BasicStroke(0.5f)
            g2d.drawOval(x, y, dotSize, dotSize)

            g2d.dispose()
        }
    }
}
