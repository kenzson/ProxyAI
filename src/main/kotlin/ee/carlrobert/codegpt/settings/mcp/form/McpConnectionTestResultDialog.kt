package ee.carlrobert.codegpt.settings.mcp.form

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import ee.carlrobert.codegpt.settings.mcp.McpClientManager
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JProgressBar
import javax.swing.ListSelectionModel

class McpConnectionTestResultDialog(
    private val serverName: String,
    private var result: McpClientManager.ConnectionTestResult?,
    private val serverCommand: String? = null
) : DialogWrapper(true) {

    private var centerPanel: JComponent? = null

    init {
        title = "Testing MCP Connection"
        init()
    }

    override fun createCenterPanel(): JComponent {
        centerPanel = when {
            result == null -> createLoadingPanel()
            result!!.success -> createSuccessPanel()
            else -> createErrorPanel()
        }
        return centerPanel!!
    }

    fun updateResult(newResult: McpClientManager.ConnectionTestResult) {
        result = newResult
        title = if (newResult.success) "Connection Test Successful" else "Connection Test Failed"

        val newPanel = if (newResult.success) {
            createSuccessPanel()
        } else {
            createErrorPanel()
        }

        val contentPane = contentPanel
        contentPane.removeAll()
        contentPane.add(newPanel)
        contentPane.revalidate()
        contentPane.repaint()

        pack()
    }

    override fun createActions() = arrayOf(okAction)

    private fun createLoadingPanel(): JComponent {
        return panel {
            row {
                icon(AnimatedIcon.Default()).gap(RightGap.SMALL)
                label("Testing connection to '${serverName}'...")
                    .applyToComponent {
                        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
                    }
            }.bottomGap(BottomGap.NONE)

            serverInformation()

            group("Connection Status") {
                row {
                    val progressBar = JProgressBar().apply {
                        isIndeterminate = true
                        preferredSize = Dimension(400, 20)
                    }
                    cell(progressBar)
                        .align(Align.FILL)
                }
                row {
                    label("Initializing connection and discovering capabilities...")
                        .applyToComponent {
                            font = JBUI.Fonts.label().deriveFont(Font.ITALIC)
                        }
                }
            }

            row {
                icon(AllIcons.General.Information)
                cell(
                    JBLabel(
                        """
                    <html>
                    This may take a few seconds while the server starts up and responds.
                    </html>
                """.trimIndent()
                    )
                )
                    .applyToComponent {
                        font = JBUI.Fonts.label()
                    }
            }.topGap(TopGap.SMALL)
        }.apply {
            preferredSize = Dimension(650, 300)
            minimumSize = Dimension(650, 250)
            maximumSize = Dimension(650, 350)
        }
    }

    private fun createSuccessPanel(): JComponent {
        return panel {
            row {
                icon(AllIcons.General.InspectionsOK).gap(RightGap.SMALL)
                label("Connection test successful!")
                    .applyToComponent {
                        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
                    }
            }.bottomGap(BottomGap.SMALL)

            serverInformation()

            if (result!!.tools.isNotEmpty()) {
                group("Available Tools (${result!!.tools.size})") {
                    row {
                        val toolsWithDescriptions = result!!.tools.map { tool ->
                            val description =
                                result!!.toolDescriptions[tool.name] ?: "No description available"
                            ToolInfo(tool.name, description)
                        }

                        val toolsList = JBList(CollectionListModel(toolsWithDescriptions)).apply {
                            selectionMode = ListSelectionModel.SINGLE_SELECTION
                            cellRenderer = object : ColoredListCellRenderer<ToolInfo>() {
                                override fun customizeCellRenderer(
                                    list: JList<out ToolInfo>,
                                    value: ToolInfo,
                                    index: Int,
                                    selected: Boolean,
                                    hasFocus: Boolean
                                ) {
                                    icon = AllIcons.Nodes.Function
                                    append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                                    if (value.description.isNotBlank() && value.description != "No description available") {
                                        append(" - ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                                        val truncatedDesc = if (value.description.length > 80) {
                                            value.description.take(77) + "..."
                                        } else {
                                            value.description
                                        }
                                        append(
                                            truncatedDesc,
                                            SimpleTextAttributes.GRAYED_ATTRIBUTES
                                        )
                                    }

                                    if (value.description.length > 80) {
                                        toolTipText =
                                            "<html><b>${value.name}</b><br/>${value.description}</html>"
                                    }
                                }
                            }
                            visibleRowCount = minOf(6, result!!.tools.size)
                            fixedCellWidth = 550
                        }

                        cell(JBScrollPane(toolsList))
                            .align(Align.FILL)
                            .resizableColumn()
                    }.resizableRow()
                        .rowComment("These tools will be available for use with the AI assistant")
                }
            }

            if (result!!.resources.isNotEmpty()) {
                group("Available Resources (${result!!.resources.size})") {
                    row {
                        val resourcesList = JBList(CollectionListModel(result!!.resources)).apply {
                            selectionMode = ListSelectionModel.SINGLE_SELECTION
                            cellRenderer = object : ColoredListCellRenderer<String>() {
                                override fun customizeCellRenderer(
                                    list: JList<out String>,
                                    value: String,
                                    index: Int,
                                    selected: Boolean,
                                    hasFocus: Boolean
                                ) {
                                    icon = AllIcons.Nodes.DataTables
                                    append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                                }
                            }
                            visibleRowCount = minOf(6, result!!.resources.size)
                        }

                        cell(JBScrollPane(resourcesList))
                            .align(Align.FILL)
                            .resizableColumn()
                    }.resizableRow()
                        .rowComment("These resources can be accessed by the AI assistant")
                }
            }

            if (result!!.tools.isEmpty() && result!!.resources.isEmpty()) {
                group("Server Capabilities") {
                    row {
                        icon(AllIcons.General.Information)
                        label("No tools or resources were reported by this server.")
                            .applyToComponent {
                                font = JBUI.Fonts.label().deriveFont(Font.ITALIC)
                            }
                    }
                    row {
                        comment("The server may still provide other capabilities not listed here.")
                    }
                }
            }
        }.apply {
            preferredSize = Dimension(650, 450)
            minimumSize = Dimension(650, 400)
            maximumSize = Dimension(650, 600)
        }
    }

    private data class ToolInfo(val name: String, val description: String)


    private fun Panel.serverInformation() {
        group("Server Information") {
            row("Server Name:") {
                label(serverName)
                    .applyToComponent {
                        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
                    }
            }

            result?.serverName?.let { name ->
                row("MCP Server:") {
                    label("$name${result?.serverVersion?.let { " (v$it)" } ?: ""}")
                }
            }

            serverCommand?.let { command ->
                row("Command:") {
                    label(command)
                }
            }
        }.bottomGap(BottomGap.SMALL).topGap(TopGap.NONE)
    }

    private fun createErrorPanel(): JComponent {
        return panel {
            row {
                icon(AllIcons.General.Error).gap(RightGap.SMALL)
                label("Connection test failed")
                    .applyToComponent {
                        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
                    }
            }.bottomGap(BottomGap.NONE)

            serverInformation()

            result!!.errorMessage?.let { error ->
                group("Error Details") {
                    row {
                        val errorTextArea = com.intellij.ui.components.JBTextArea(error).apply {
                            isEditable = false
                            rows = 3
                            columns = 50
                            lineWrap = true
                            wrapStyleWord = true
                        }

                        cell(JBScrollPane(errorTextArea))
                            .align(Align.FILL)
                            .resizableColumn()
                    }.resizableRow()
                }
            }

            group("Troubleshooting Tips") {
                val tips = listOf(
                    "Verify the command exists and is accessible" + (serverCommand?.let { ": '$it'" }
                        ?: ""),
                    "Check that all command arguments are correct",
                    "Ensure required environment variables are properly set",
                    "Try running the command manually in your terminal"
                )

                tips.forEach { tip ->
                    row {
                        icon(AllIcons.General.BalloonInformation)
                        label(tip)
                    }
                }
            }

            row {
                icon(AllIcons.General.ContextHelp)
                cell(
                    JBLabel(
                        """
                    <html>
                    <b>Need more help?</b> Check the MCP server documentation or test the command in your terminal.
                    </html>
                """.trimIndent()
                    )
                )
                    .applyToComponent {
                        font = JBUI.Fonts.label()
                    }
            }.topGap(TopGap.SMALL)
        }.apply {
            preferredSize = Dimension(650, 400)
            minimumSize = Dimension(650, 350)
            maximumSize = Dimension(650, 500)
        }
    }
}