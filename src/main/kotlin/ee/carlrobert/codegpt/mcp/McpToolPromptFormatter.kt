package ee.carlrobert.codegpt.mcp

class McpToolPromptFormatter {

    /**
     * Formats a list of MCP tools into a structured text prompt that can be included in the system message.
     */
    fun formatToolsForSystemPrompt(tools: List<McpTool>): String {
        if (tools.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine()
            appendLine("## Available Tools")
            appendLine()

            tools.forEach { tool ->
                appendLine("### ${tool.name}")
                appendLine()
                appendLine("**Description:** ${tool.description}")
                appendLine()

                if (tool.schema.isNotEmpty()) {
                    val schema = tool.schema
                    val properties = schema["properties"] as? Map<*, *>
                    val required = schema["required"] as? List<*> ?: emptyList<String>()

                    if (!properties.isNullOrEmpty()) {
                        appendLine("**Parameters:**")
                        properties.forEach { (name, prop) ->
                            val propMap = prop as? Map<*, *>
                            val type = propMap?.get("type") ?: "any"
                            val description = propMap?.get("description") ?: ""
                            val isRequired = required.contains(name)

                            append("- `$name` ($type)")
                            if (isRequired) {
                                append(" [required]")
                            }
                            if (description.toString().isNotEmpty()) {
                                append(": $description")
                            }
                            appendLine()
                        }
                    } else {
                        appendLine("**Parameters:** None")
                    }
                } else {
                    appendLine("**Parameters:** None")
                }
                appendLine()
            }
        }
            .trim() + "\n"
    }
}