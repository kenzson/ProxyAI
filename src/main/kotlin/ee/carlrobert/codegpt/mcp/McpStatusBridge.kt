package ee.carlrobert.codegpt.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import ee.carlrobert.codegpt.settings.mcp.McpSettings
import ee.carlrobert.codegpt.ui.textarea.UserInputPanel
import ee.carlrobert.codegpt.ui.textarea.header.tag.McpTagDetails
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class McpStatusBridge {

    companion object {
        private val logger = thisLogger()
    }

    private val mcpSettings = ApplicationManager.getApplication().service<McpSettings>()
    private val sessionManager = ApplicationManager.getApplication().service<McpSessionManager>()
    private val statusUpdater = ApplicationManager.getApplication().service<McpTagStatusUpdater>()

    fun handleConnectionError(
        serverId: String,
        conversationId: UUID,
        error: Exception
    ): McpTagDetails {
        return try {
            val serverName = getServerName(serverId)
            val errorTagDetails = McpTagDetails(
                serverId = serverId,
                serverName = serverName,
                connectionStatus = ConnectionStatus.ERROR,
                availableTools = emptyList(),
                availableResources = emptyList(),
                lastError = error.message,
                serverCommand = getServerCommand(serverId),
                connectionTime = null
            )

            val statusUpdater = ApplicationManager.getApplication().service<McpTagStatusUpdater>()
            statusUpdater.updateTagStatus(conversationId, serverId, errorTagDetails)

            logger.warn("Handled connection error for server '$serverId': ${error.message}")
            errorTagDetails
        } catch (e: Exception) {
            logger.error("Error handling connection error for server '$serverId'", e)
            createErrorTagDetails(serverId, getServerName(serverId), e)
        }
    }

    fun createConnectingTagDetails(serverId: String, serverName: String): McpTagDetails {
        return McpTagDetails(
            serverId = serverId,
            serverName = serverName,
            connectionStatus = ConnectionStatus.CONNECTING,
            availableTools = emptyList(),
            availableResources = emptyList(),
            lastError = null,
            serverCommand = getServerCommand(serverId),
            connectionTime = Instant.now()
        )
    }

    fun attachServerAndUpdateUi(
        conversationId: UUID,
        serverId: String,
        serverName: String,
        userInputPanel: UserInputPanel
    ): CompletableFuture<McpSessionAttachment> {
        statusUpdater.registerTagManager(conversationId, userInputPanel.tagManager)
        val connectingTag = createConnectingTagDetails(serverId, serverName)
        userInputPanel.addTag(connectingTag)

        return sessionManager.attachServerToSession(conversationId, serverId)
            .whenComplete { attachment, throwable ->
                if (throwable != null) {
                    handleConnectionError(
                        serverId,
                        conversationId,
                        throwable as? Exception ?: Exception(throwable.message)
                    )
                } else if (attachment != null) {
                    if (attachment.isConnected()) {
                        val connectedTag = McpTagDetails(
                            serverId = attachment.serverId,
                            serverName = attachment.serverName,
                            connectionStatus = attachment.connectionStatus,
                            availableTools = attachment.availableTools,
                            availableResources = attachment.availableResources,
                            lastError = attachment.lastError,
                            serverCommand = getServerCommand(serverId),
                            connectionTime = Instant.ofEpochMilli(attachment.attachedAt)
                        )
                        statusUpdater.updateTagStatus(conversationId, serverId, connectedTag)
                    } else if (attachment.hasError()) {
                        handleConnectionError(
                            serverId,
                            conversationId,
                            Exception(attachment.lastError ?: "Unknown error")
                        )
                    }
                }
            }
    }

    private fun getServerCommand(serverId: String): String? {
        return mcpSettings.state.servers.find { it.id.toString() == serverId }?.command
    }

    private fun getServerName(serverId: String): String {
        return mcpSettings.state.servers.find { it.id.toString() == serverId }?.name
            ?: "Unknown Server"
    }

    private fun createErrorTagDetails(
        serverId: String,
        serverName: String,
        error: Exception
    ): McpTagDetails {
        return McpTagDetails(
            serverId = serverId,
            serverName = serverName,
            connectionStatus = ConnectionStatus.ERROR,
            availableTools = emptyList(),
            availableResources = emptyList(),
            lastError = error.message,
            serverCommand = getServerCommand(serverId),
            connectionTime = null
        )
    }
}

data class ConnectionMetrics(
    val serverId: String,
    val status: ConnectionStatus,
    val toolCount: Int,
    val resourceCount: Int,
    val supportsToolExecution: Boolean,
    val connectionTime: Instant?,
    val lastSyncTime: Instant,
    val errorMessage: String? = null
) {
    companion object {
        fun error(serverId: String, errorMessage: String?): ConnectionMetrics {
            return ConnectionMetrics(
                serverId = serverId,
                status = ConnectionStatus.ERROR,
                toolCount = 0,
                resourceCount = 0,
                supportsToolExecution = false,
                connectionTime = null,
                lastSyncTime = Instant.now(),
                errorMessage = errorMessage
            )
        }
    }
}
