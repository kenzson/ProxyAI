package ee.carlrobert.codegpt.settings.mcp.form.details

import ee.carlrobert.codegpt.settings.mcp.McpServerDetailsState
import javax.swing.JComponent

interface McpDetailsPanel {
    fun getPanel(): JComponent
    fun updateData(details: McpServerDetails)
    fun remove(details: McpServerDetails)
}

data class McpServerDetails(
    val id: Long,
    var name: String,
    var command: String,
    var arguments: MutableList<String>,
    var environmentVariables: MutableMap<String, String>
) {
    constructor(state: McpServerDetailsState) : this(
        id = state.id,
        name = state.name ?: "New MCP Server",
        command = state.command ?: "npx",
        arguments = state.arguments.toMutableList(),
        environmentVariables = state.environmentVariables.toMutableMap()
    )
}