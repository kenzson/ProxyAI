package ee.carlrobert.codegpt.toolwindow.ui.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.util.text.StringUtil

object McpParameterPreview {

    private val objectMapper = ObjectMapper()

    fun formatDetailed(jsonArgs: String?): Map<String, String>? {
        if (jsonArgs.isNullOrEmpty() || jsonArgs == "{}") return null

        return try {
            val args = objectMapper.readValue(jsonArgs, Map::class.java) as Map<String, Any>
            args.mapValues { (_, value) -> formatValueDetailed(value) }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatValueDetailed(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            is List<*> -> formatListDetailed(value)
            is Map<*, *> -> formatMapDetailed(value)
            is Array<*> -> formatListDetailed(value.toList())
            else -> value.toString()
        }
    }

    private fun formatListDetailed(list: List<*>): String {
        return if (list.isEmpty()) {
            "[]"
        } else if (list.size <= 3) {
            list.joinToString("\n") { "• ${formatValueDetailed(it)}" }
        } else {
            val preview = list.take(3).joinToString("\n") { "• ${formatValueDetailed(it)}" }
            "$preview\n• ... (${list.size - 3} more items)"
        }
    }

    private fun formatMapDetailed(map: Map<*, *>): String {
        return if (map.isEmpty()) {
            "{}"
        } else {
            map.entries.joinToString("\n") { (k, v) ->
                "${k}: ${formatValueDetailed(v)}"
            }
        }
    }

    fun generateTooltip(jsonArgs: String?): String? {
        if (jsonArgs.isNullOrEmpty() || jsonArgs == "{}") return null

        return try {
            val args = objectMapper.readValue(jsonArgs, Map::class.java) as Map<String, Any>
            if (args.isEmpty()) return null

            "<html><body style='width: 300px;'>" +
                    args.entries.joinToString("<br>") { (key, value) ->
                        "<b>$key:</b> ${formatTooltipValue(value)}"
                    } +
                    "</body></html>"
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTooltipValue(value: Any?): String {
        val formatted = when (value) {
            null -> "null"
            is String -> if (value.length > 100) {
                StringUtil.shortenTextWithEllipsis(value, 100, 10)
            } else value

            is List<*> -> "[${value.size} items]"
            is Map<*, *> -> "{${value.size} properties}"
            else -> value.toString()
        }

        return formatted
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}