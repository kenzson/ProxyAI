package ee.carlrobert.codegpt.toolwindow.ui.mcp

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import ee.carlrobert.codegpt.Icons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Duration
import java.time.LocalDateTime
import javax.swing.*

class McpStatusPanel(
    private val toolName: String,
    private val serverName: String,
    initialStatus: String = "Executing...",
) : JBPanel<McpStatusPanel>() {

    private val startTime = LocalDateTime.now()
    private val statusLabel = JBLabel(initialStatus)
    private val spinner = JProgressBar().apply {
        isIndeterminate = true
        preferredSize = JBUI.size(60, 8)
        border = JBUI.Borders.empty()
    }

    private var resultPreviewLabel: JBLabel? = null
    private var resultActionsPanel: JPanel? = null
    private var currentResult: String? = null
    private var currentError: String? = null
    private var isCompleted = false

    init {
        layout = BorderLayout()
        isOpaque = false
        border = JBUI.Borders.empty(8, 0)

        setupUI()
    }

    private fun setupUI() {
        layout = BorderLayout()
        isOpaque = false
        border = JBUI.Borders.empty(8, 0)

        val headerPanel = JBPanel<JBPanel<*>>().apply {
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            isOpaque = false
            border = JBUI.Borders.empty(0, 0, 4, 0)
        }

        headerPanel.add(JBLabel(Icons.MCP))
        headerPanel.add(Box.createHorizontalStrut(8))

        val infoLabel = JBLabel("$toolName • $serverName").apply {
            font = JBUI.Fonts.label()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }
        headerPanel.add(infoLabel)
        headerPanel.add(Box.createHorizontalStrut(12))

        statusLabel.font = JBUI.Fonts.smallFont()
        statusLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        headerPanel.add(statusLabel)
        headerPanel.add(Box.createHorizontalStrut(8))

        headerPanel.add(spinner)

        add(headerPanel, BorderLayout.NORTH)
        add(BorderLayoutPanel(), BorderLayout.CENTER)
    }

    fun complete(success: Boolean, message: String? = null, result: String? = null) {
        SwingUtilities.invokeLater {
            spinner.isVisible = false
            isCompleted = true

            val duration = Duration.between(startTime, LocalDateTime.now())
            val durationText = formatDuration(duration)

            if (success) {
                currentResult = result
                statusLabel.text = "${message ?: "Completed"} • $durationText"
                statusLabel.foreground = JBUI.CurrentTheme.Label.foreground()

                result?.let { showResultPreview(it) }
            } else {
                currentError = message ?: result ?: "Failed"
                statusLabel.text = "Failed • $durationText"
                statusLabel.foreground = JBColor.RED

                currentError?.let { showErrorPreview(it) }
            }

            revalidate()
            repaint()
        }
    }

    private fun showResultPreview(result: String) {
        if (result.isBlank()) return

        val previewText = formatResultPreview(result)
        if (previewText.isNotEmpty()) {
            removeResultComponents()

            val resultPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
                isOpaque = false
                border = JBUI.Borders.empty(8, 24, 4, 0)
            }

            resultPreviewLabel = JBLabel(previewText).apply {
                font = JBUI.Fonts.smallFont()
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            }

            resultActionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 2)).apply {
                isOpaque = false
                border = JBUI.Borders.empty(4, 0, 0, 0)

                add(createShowMoreLink())
                add(Box.createHorizontalStrut(12))
                add(createCopyLink())
            }

            resultPanel.add(resultPreviewLabel!!, BorderLayout.CENTER)
            resultPanel.add(resultActionsPanel!!, BorderLayout.SOUTH)

            val resultContainer = getComponent(1) as JBPanel<*>
            resultContainer.add(resultPanel, BorderLayout.CENTER)
        }
    }

    private fun showErrorPreview(error: String) {
        val previewText = error.trim().take(100).let {
            if (it.length < error.trim().length) "$it..." else it
        }

        removeResultComponents()

        val errorPanel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            isOpaque = false
            border = JBUI.Borders.empty(8, 24, 4, 0)
        }

        resultPreviewLabel = JBLabel("Error: $previewText").apply {
            font = JBUI.Fonts.smallFont()
            foreground = JBColor.RED
        }

        resultActionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 2)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4, 0, 0, 0)

            add(createShowErrorDetailsLink())
            add(Box.createHorizontalStrut(12))
            add(createCopyLink())
        }

        errorPanel.add(resultPreviewLabel!!, BorderLayout.CENTER)
        errorPanel.add(resultActionsPanel!!, BorderLayout.SOUTH)

        val resultContainer = getComponent(1) as JBPanel<*>
        resultContainer.add(errorPanel, BorderLayout.CENTER)
    }

    private fun removeResultComponents() {
        val resultContainer = getComponent(1) as JBPanel<*>
        resultContainer.removeAll()
    }

    private fun createShowMoreLink(): ActionLink {
        return ActionLink("Show details") {
            showFullContent(false)
        }.apply {
            font = JBUI.Fonts.smallFont()
        }
    }

    private fun createShowErrorDetailsLink(): ActionLink {
        return ActionLink("Show details") {
            showFullContent(true)
        }.apply {
            font = JBUI.Fonts.smallFont()
        }
    }

    private fun createCopyLink(): ActionLink {
        return ActionLink("Copy") {
            val content = currentResult ?: currentError ?: return@ActionLink
            val selection = StringSelection(content)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }.apply {
            font = JBUI.Fonts.smallFont()
        }
    }

    private fun showFullContent(isError: Boolean) {
        val content = currentResult ?: currentError ?: return
        val dialog = McpResultDialog(toolName, serverName, content, isError)
        dialog.isVisible = true
    }

    private fun formatResultPreview(result: String): String {
        val cleaned = result
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("  ", " ")
            .trim()

        return when {
            cleaned.contains("search results") || cleaned.contains("results found") -> {
                val countMatch =
                    Regex("(\\d+)\\s*(?:search\\s*)?results?").find(cleaned.lowercase())
                if (countMatch != null) {
                    "Found ${countMatch.groupValues[1]} results"
                } else {
                    "Search completed"
                }
            }

            cleaned.contains("SELECT") || cleaned.contains("INSERT") || cleaned.contains("UPDATE") -> {
                val rowMatch = Regex("(\\d+)\\s*rows?").find(cleaned)
                if (rowMatch != null) {
                    "Query returned ${rowMatch.groupValues[1]} rows"
                } else {
                    "Query executed successfully"
                }
            }

            cleaned.length > 150 -> {
                cleaned.take(150) + "..."
            }

            else -> cleaned
        }
    }

    private fun formatDuration(duration: Duration): String {
        val seconds = duration.toMillis() / 1000.0
        return when {
            seconds < 1 -> "${duration.toMillis()}ms"
            seconds < 60 -> "%.1fs".format(seconds)
            else -> "${duration.toSeconds()}s"
        }
    }

    override fun paintComponent(g: Graphics) {
        paintWithAlpha(g) {
            super.paintComponent(g)
        }
    }
}

class McpResultDialog(
    private val toolName: String,
    private val serverName: String,
    private val content: String,
    private val isError: Boolean
) : JDialog() {

    init {
        title = if (isError) "Tool Error: $toolName" else "Tool Result: $toolName"
        isModal = true
        setupUI()
        pack()
        setLocationRelativeTo(null)
    }

    private fun setupUI() {
        layout = BorderLayout()

        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            border = JBUI.Borders.empty()
            isOpaque = false

            add(JLabel(if (isError) AllIcons.General.Error else Icons.MCP))
            add(Box.createHorizontalStrut(8))
            add(JLabel("Tool: $toolName").apply {
                font = JBUI.Fonts.label().asBold()
            })
            add(JLabel(" • ").apply {
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
            add(JLabel(serverName).apply {
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
        }

        val textArea = JTextArea(content).apply {
            isEditable = false
            font = JBUI.Fonts.label()
            lineWrap = true
            wrapStyleWord = true
            caretPosition = 0
            border = JBUI.Borders.empty()
        }

        val scrollPane = JScrollPane(textArea).apply {
            preferredSize = JBUI.size(600, 400)
            border =
                JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            border = JBUI.Borders.empty(8, 0, 0, 0)
            isOpaque = false

            val copyButton = JButton("Copy to Clipboard").apply {
                addActionListener {
                    val selection = StringSelection(content)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                }
            }

            val closeButton = JButton("Close").apply {
                addActionListener { dispose() }
            }

            add(copyButton)
            add(Box.createHorizontalStrut(8))
            add(closeButton)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)

        rootPane.registerKeyboardAction(
            { dispose() },
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )
    }
}