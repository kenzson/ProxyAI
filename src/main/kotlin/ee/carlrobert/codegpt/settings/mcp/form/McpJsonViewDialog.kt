package ee.carlrobert.codegpt.settings.mcp.form

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground
import ee.carlrobert.codegpt.settings.mcp.form.details.McpServerDetails
import ee.carlrobert.codegpt.ui.OverlayUtil
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent

class McpJsonViewDialog(private val server: McpServerDetails) : DialogWrapper(true) {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val project = ProjectManager.getInstance().defaultProject

    private val jsonEditor = EditorTextField(
        "",
        project,
        FileTypeManager.getInstance().getFileTypeByExtension("json")
    ).apply {
        setOneLineMode(false)
        preferredSize = Dimension(700, 450)

        addSettingsProvider { editor ->
            val settings = editor.settings
            settings.isLineNumbersShown = true
            settings.isAutoCodeFoldingEnabled = true
            settings.isFoldingOutlineShown = true
            settings.isAllowSingleLogicalLineFolding = true
            settings.isRightMarginShown = false
            settings.isUseSoftWraps = false
            settings.isWhitespacesShown = false

            editor.colorsScheme.apply {
                editorFontSize = JBUI.Fonts.label().size
                editorFontName = JBUI.Fonts.label().fontName
            }

            editor.isViewer = true
            editor.backgroundColor = editor.colorsScheme.defaultBackground
        }

        text = generateServerJson()
    }

    init {
        title = "JSON Configuration - ${server.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                icon(AllIcons.FileTypes.Json)
                label("JSON configuration for '${server.name}':")
                    .applyToComponent {
                        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
                    }
            }.bottomGap(BottomGap.MEDIUM)

            row {
                cell(com.intellij.ui.components.JBScrollPane(jsonEditor).apply {
                    preferredSize = Dimension(700, 450)
                    border = JBUI.Borders.customLine(separatorForeground())
                })
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()

            separator()

            row {
                button("Copy to Clipboard") {
                    CopyPasteManager.getInstance().setContents(StringSelection(jsonEditor.text))
                    OverlayUtil.showBalloon(
                        "JSON configuration copied to clipboard",
                        MessageType.INFO,
                        it.source as JComponent
                    )
                }.applyToComponent {
                    icon = AllIcons.Actions.Copy
                }
            }.topGap(TopGap.NONE)
        }.apply {
            preferredSize = Dimension(750, 600)
            minimumSize = Dimension(700, 500)
        }
    }

    override fun createActions() = arrayOf(okAction)

    private fun generateServerJson(): String {
        val serverConfig = objectMapper.createObjectNode().apply {
            put("command", server.command)

            if (server.arguments.isNotEmpty()) {
                val argsArray = putArray("args")
                server.arguments.forEach { argsArray.add(it) }
            }

            if (server.environmentVariables.isNotEmpty()) {
                val envObject = putObject("env")
                server.environmentVariables.forEach { (key, value) ->
                    envObject.put(key, value)
                }
            }
        }

        val mcpServers = objectMapper.createObjectNode()
        mcpServers.set<ObjectNode>(server.name, serverConfig)

        val rootObject = objectMapper.createObjectNode()
        rootObject.set<ObjectNode>("mcpServers", mcpServers)

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootObject)
    }
}