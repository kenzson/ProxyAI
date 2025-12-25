package ee.carlrobert.codegpt.mcp

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import ee.carlrobert.codegpt.ui.textarea.header.tag.McpTagDetails
import ee.carlrobert.codegpt.ui.textarea.header.tag.TagManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class McpTagStatusUpdater {

    companion object {
        private val logger = thisLogger()
    }

    private val tagManagers = ConcurrentHashMap<UUID, TagManager>()

    fun registerTagManager(conversationId: UUID, tagManager: TagManager) {
        tagManagers[conversationId] = tagManager
    }

    fun updateTagStatus(conversationId: UUID, serverId: String, tagDetails: McpTagDetails) {
        val updateTask = {
            try {
                val tagManager = tagManagers[conversationId]
                if (tagManager != null) {
                    val mcpTags = tagManager.getTags().filterIsInstance<McpTagDetails>()
                    val targetTag = mcpTags.find { it.serverId == serverId }

                    if (targetTag != null) {
                        tagManager.updateTag(targetTag, tagDetails)
                    } else {
                        if (tagDetails.connectionStatus != ConnectionStatus.DISCONNECTED) {
                            tagManager.addTag(tagDetails)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to update MCP tag status", e)
            }
        }

        runInEdt {
            updateTask()
        }
    }
}