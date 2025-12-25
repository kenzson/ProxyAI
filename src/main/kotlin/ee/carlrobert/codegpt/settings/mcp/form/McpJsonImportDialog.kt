package ee.carlrobert.codegpt.settings.mcp.form

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import ee.carlrobert.codegpt.settings.mcp.form.details.McpServerDetails
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent

class McpJsonImportDialog : DialogWrapper(true) {

    private val project = ProjectManager.getInstance().defaultProject
    private val jsonEditor = EditorTextField(
        "",
        project,
        FileTypeManager.getInstance().getFileTypeByExtension("json")
    ).apply {
        setOneLineMode(false)
        preferredSize = Dimension(580, 250)

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

            if (editor is EditorEx) {
                editor.isViewer = false
                editor.setVerticalScrollbarVisible(true)
                editor.setHorizontalScrollbarVisible(true)
                editor.backgroundColor = editor.colorsScheme.defaultBackground
            }
        }

        text = """
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/directory"]
    }
  }
}
        """.trimIndent()

        addSettingsProvider { editor ->
            editor.caretModel.moveToOffset(0)
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.RELATIVE)
        }
    }

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    var importedServers: List<McpServerDetails> = emptyList()
        private set

    init {
        title = "Import MCP Servers from JSON"
        init()


        UIUtil.invokeLaterIfNeeded {
            jsonEditor.editor?.let { editor ->
                editor.caretModel.moveToOffset(0)
                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label("Paste your MCP server configuration:")
                    .applyToComponent {
                        font = JBUI.Fonts.label().deriveFont(Font.BOLD)
                    }
            }.bottomGap(BottomGap.SMALL)

            row {
                scrollCell(jsonEditor)
                    .align(Align.FILL)
                    .resizableColumn()
            }.resizableRow()
        }.apply {
            preferredSize = Dimension(600, 400)
            minimumSize = Dimension(550, 350)
        }
    }

    override fun doValidate(): ValidationInfo? {
        val jsonText = jsonEditor.text.trim()
        if (jsonText.isEmpty()) {
            return ValidationInfo("JSON content cannot be empty", jsonEditor)
        }

        return try {
            val parsedServers = parseJsonToServers(jsonText)
            if (parsedServers.isEmpty()) {
                ValidationInfo("No valid MCP servers found in the JSON", jsonEditor)
            } else {
                importedServers = parsedServers
                null
            }
        } catch (e: Exception) {
            ValidationInfo("Invalid JSON format: ${e.message}", jsonEditor)
        }
    }

    private fun parseJsonToServers(jsonText: String): List<McpServerDetails> {
        val jsonNode = objectMapper.readTree(jsonText)
        val servers = mutableListOf<McpServerDetails>()
        var nextId = System.currentTimeMillis()

        when {
            jsonNode.has("mcpServers") -> {
                val mcpServers = jsonNode.get("mcpServers")
                mcpServers.fields().forEach { (name, config) ->
                    servers.add(createServerFromConfig(nextId++, name, config))
                }
            }

            jsonNode.has("command") -> {
                servers.add(createServerFromConfig(nextId++, "Imported Server", jsonNode))
            }

            jsonNode.isArray -> {
                jsonNode.forEach { serverNode ->
                    val name =
                        serverNode.get("name")?.asText() ?: "Imported Server ${servers.size + 1}"
                    servers.add(createServerFromConfig(nextId++, name, serverNode))
                }
            }

            else -> {
                jsonNode.fields().forEach { (name, config) ->
                    if (config.isObject) {
                        servers.add(createServerFromConfig(nextId++, name, config))
                    }
                }
            }
        }

        return servers
    }

    private fun createServerFromConfig(id: Long, name: String, config: JsonNode): McpServerDetails {
        val command = config.get("command")?.asText() ?: "npx"
        val args = config.get("args")?.map { it.asText() }?.toMutableList()
            ?: config.get("arguments")?.map { it.asText() }?.toMutableList()
            ?: mutableListOf()

        val env = mutableMapOf<String, String>()
        config.get("env")?.fields()?.forEach { (key, value) ->
            env[key] = value.asText()
        }
        config.get("environment")?.fields()?.forEach { (key, value) ->
            env[key] = value.asText()
        }

        return McpServerDetails(
            id = id,
            name = name,
            command = command,
            arguments = args,
            environmentVariables = env
        )
    }
}