package ee.carlrobert.codegpt.settings.mcp.form

import ee.carlrobert.codegpt.settings.mcp.McpServerDetailsState
import ee.carlrobert.codegpt.settings.mcp.form.details.McpServerDetails

object McpFormUtil {
    fun McpServerDetails.toState(): McpServerDetailsState {
        val state = McpServerDetailsState()
        state.id = this.id
        state.name = this.name
        state.command = this.command
        state.arguments = this.arguments.toMutableList()
        state.environmentVariables = this.environmentVariables.toMutableMap()
        return state
    }
}