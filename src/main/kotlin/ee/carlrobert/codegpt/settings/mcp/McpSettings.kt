package ee.carlrobert.codegpt.settings.mcp

import com.intellij.openapi.components.*
import ee.carlrobert.codegpt.util.ApplicationUtil

@Service
@State(
    name = "CodeGPT_McpSettings",
    storages = [Storage("CodeGPT_McpSettings.xml")]
)
class McpSettings : SimplePersistentStateComponent<McpSettingsState>(McpSettingsState())

class McpSettingsState : BaseState() {
    var servers by list<McpServerDetailsState>()

    init {
        servers.add(McpServerDetailsState().apply {
            id = 1L
            name = "Everything Server"
            command = "npx"
            arguments = mutableListOf("-y", "@modelcontextprotocol/server-everything")
        })
        servers.add(McpServerDetailsState().apply {
            id = 2L
            name = "File System Server"
            command = "npx"
            arguments = mutableListOf(
                "-y",
                "@modelcontextprotocol/server-filesystem",
                ApplicationUtil.findCurrentProject()?.basePath ?: "/"
            )
        })
        servers.add(McpServerDetailsState().apply {
            id = 3L
            name = "Git Server"
            command = "npx"
            arguments = mutableListOf("-y", "@modelcontextprotocol/server-git")
        })
    }
}

class McpServerDetailsState : BaseState() {
    var id by property(1L)
    var name by string("New MCP Server")
    var command by string("npx")
    var arguments by list<String>()
    var environmentVariables by map<String, String>()
}