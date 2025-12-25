package ee.carlrobert.codegpt.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import ee.carlrobert.llm.client.openai.completion.request.Tool
import ee.carlrobert.llm.client.openai.completion.request.ToolFunction
import ee.carlrobert.llm.client.openai.completion.request.ToolFunctionParameters
import ee.carlrobert.llm.client.openai.completion.response.ToolCall
import java.util.UUID

object McpToolConverter {

    private val objectMapper = ObjectMapper()

    fun convertToOpenAITool(mcpTool: McpTool): Tool {
        val tool = Tool()
        tool.type = "function"
        tool.function = convertToOpenAIFunction(mcpTool)
        return tool
    }

    private fun convertToOpenAIFunction(mcpTool: McpTool): ToolFunction {
        val function = ToolFunction()
        function.name = mcpTool.name
        function.description = mcpTool.description

        function.parameters = if (mcpTool.schema.isNotEmpty()) {
            convertSchemaToParameters(mcpTool.schema)
        } else {
            val emptyParams = ToolFunctionParameters()
            emptyParams.type = "object"
            emptyParams.properties = mutableMapOf<String, Any>()
            emptyParams
        }

        return function
    }

    private fun convertSchemaToParameters(schema: Map<String, Any>): ToolFunctionParameters {
        val parameters = ToolFunctionParameters()
        parameters.type = "object"

        val properties = mutableMapOf<String, Any>()
        val required = mutableListOf<String>()

        when {
            schema.containsKey("type") && schema["type"] == "object" -> {
                val schemaProperties = schema["properties"] as? Map<String, Any>
                if (schemaProperties != null) {
                    properties.putAll(schemaProperties)
                }

                val schemaRequired = schema["required"] as? List<String>
                if (schemaRequired != null) {
                    required.addAll(schemaRequired)
                }
            }

            schema.containsKey("properties") -> {
                val schemaProperties = schema["properties"] as? Map<String, Any>
                if (schemaProperties != null) {
                    properties.putAll(schemaProperties)
                }

                val schemaRequired = schema["required"] as? List<String>
                if (schemaRequired != null) {
                    required.addAll(schemaRequired)
                }
            }

            else -> {
                properties.putAll(schema)
            }
        }

        parameters.properties = properties
        parameters.required = required
        return parameters
    }

    fun toMcpToolCall(toolCall: ToolCall): McpToolCall {
        return McpToolCall(
            id = toolCall.id ?: UUID.randomUUID().toString(),
            name = toolCall.function.name,
            arguments = parseArguments(toolCall.function.arguments)
        )
    }

    private fun parseArguments(argumentsJson: String?): Map<String, Any> {
        if (argumentsJson.isNullOrEmpty()) {
            return emptyMap()
        }

        return try {
            val result = objectMapper.readValue(argumentsJson, Map::class.java) as Map<String, Any>
            val processedResult = result.mapValues { (key, value) ->
                when (value) {
                    is Double -> {
                        if (value % 1 == 0.0) {
                            value.toInt()
                        } else {
                            value
                        }
                    }

                    else -> value
                }
            }

            processedResult
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

data class McpToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>
)
