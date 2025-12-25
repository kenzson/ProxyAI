package ee.carlrobert.codegpt.mcp

data class McpSessionAttachment(
    var serverId: String = "",
    var serverName: String = "",
    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    var availableTools: List<McpTool> = emptyList(),
    var availableResources: List<McpResource> = emptyList(),
    var attachedAt: Long = System.currentTimeMillis(),
    var lastError: String? = null
) {
    fun isConnected(): Boolean = connectionStatus == ConnectionStatus.CONNECTED
    fun hasError(): Boolean = connectionStatus == ConnectionStatus.ERROR
}

enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class McpTool(
    var name: String = "",
    var description: String = "",
    var serverId: String = "",
    var schema: MutableMap<String, Any> = mutableMapOf()
)

data class McpResource(
    var uri: String = "",
    var name: String = "",
    var description: String? = null,
    var serverId: String = "",
    var mimeType: String? = null
)
