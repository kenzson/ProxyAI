package ee.carlrobert.codegpt.completions.factory

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.components.service
import ee.carlrobert.codegpt.completions.BaseRequestFactory
import ee.carlrobert.codegpt.completions.ChatCompletionParameters
import ee.carlrobert.codegpt.completions.InlineEditCompletionParameters
import ee.carlrobert.codegpt.completions.ToolApprovalMode
import ee.carlrobert.codegpt.credentials.CredentialsStore.CredentialKey
import ee.carlrobert.codegpt.credentials.CredentialsStore.getCredential
import ee.carlrobert.codegpt.mcp.McpToolConverter
import ee.carlrobert.codegpt.settings.service.FeatureType
import ee.carlrobert.codegpt.settings.service.custom.CustomServiceChatCompletionSettingsState
import ee.carlrobert.codegpt.settings.service.custom.CustomServicesSettings
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionMessage
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage
import ee.carlrobert.llm.completion.CompletionRequest
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets

class CustomOpenAIRequest(val request: Request) : CompletionRequest

class CustomOpenAIRequestFactory : BaseRequestFactory() {

    override fun createChatRequest(params: ChatCompletionParameters): CustomOpenAIRequest {
        val serviceState =
            service<CustomServicesSettings>().customServiceStateForFeatureType(params.featureType)
        val messages = OpenAIRequestFactory.buildOpenAIMessages(
            null,
            params,
            params.referencedFiles,
            params.history,
            params.psiStructure
        )
        val request = buildCustomOpenAIChatCompletionRequest(
            serviceState.chatCompletionSettings,
            messages,
            true,
            getCredential(CredentialKey.CustomServiceApiKeyById(requireNotNull(serviceState.id))),
            params
        )
        return CustomOpenAIRequest(request)
    }

    override fun createBasicCompletionRequest(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        stream: Boolean,
        featureType: FeatureType
    ): CompletionRequest {
        val service =
            service<CustomServicesSettings>().customServiceStateForFeatureType(featureType)

        val request = buildCustomOpenAIChatCompletionRequest(
            service.chatCompletionSettings,
            listOf(
                OpenAIChatCompletionStandardMessage("system", systemPrompt),
                OpenAIChatCompletionStandardMessage("user", userPrompt)
            ),
            stream,
            getCredential(CredentialKey.CustomServiceApiKeyById(requireNotNull(service.id)))
        )
        return CustomOpenAIRequest(request)
    }

    override fun createInlineEditRequest(params: InlineEditCompletionParameters): CompletionRequest {
        val service =
            service<CustomServicesSettings>().customServiceStateForFeatureType(FeatureType.INLINE_EDIT)
        val systemPrompt = prepareInlineEditSystemPrompt(params)
        val messages =
            OpenAIRequestFactory.buildInlineEditMessages(systemPrompt, params.conversation)
        val request = buildCustomOpenAIChatCompletionRequest(
            service.chatCompletionSettings,
            messages,
            true,
            getCredential(CredentialKey.CustomServiceApiKeyById(requireNotNull(service.id)))
        )
        return CustomOpenAIRequest(request)
    }

    companion object {
        fun buildCustomOpenAICompletionRequest(
            context: String,
            url: String,
            headers: MutableMap<String, String>,
            body: MutableMap<String, Any>,
            credential: String?
        ): Request {
            val usedSettings = CustomServiceChatCompletionSettingsState()
            usedSettings.body = body
            usedSettings.headers = headers
            usedSettings.url = url
            return buildCustomOpenAIChatCompletionRequest(
                usedSettings,
                listOf(OpenAIChatCompletionStandardMessage("user", context)),
                true,
                credential,
                null
            )
        }

        fun buildCustomOpenAIChatCompletionRequest(
            settings: CustomServiceChatCompletionSettingsState,
            messages: List<OpenAIChatCompletionMessage>,
            streamRequest: Boolean,
            credential: String?,
            params: ChatCompletionParameters? = null
        ): Request {
            val requestBuilder = Request.Builder().url(requireNotNull(settings.url).trim())

            settings.headers.forEach { (key, value) ->
                val headerValue = when {
                    credential != null && value.contains("\$CUSTOM_SERVICE_API_KEY") ->
                        value.replace("\$CUSTOM_SERVICE_API_KEY", credential)

                    else -> value
                }
                requestBuilder.addHeader(key, headerValue)
            }

            val body = settings.body.toMutableMap().apply {
                replaceAll { key, value ->
                    when {
                        !streamRequest && key == "stream" -> false
                        value is String && value.trim() == "\$OPENAI_MESSAGES" -> messages
                        else -> value
                    }
                }

                if (params != null && !params.mcpTools.isNullOrEmpty() && params.toolApprovalMode != ToolApprovalMode.BLOCK_ALL) {
                    val openAITools = params.mcpTools!!.map { mcpTool ->
                        McpToolConverter.convertToOpenAITool(mcpTool)
                    }

                    put("tools", openAITools)

                    when (params.toolApprovalMode) {
                        ToolApprovalMode.AUTO_APPROVE -> {
                            put("tool_choice", "auto")
                        }

                        ToolApprovalMode.REQUIRE_APPROVAL -> {
                            put("tool_choice", "auto")
                        }

                        else -> {}
                    }
                }
            }

            return try {
                val requestBodyString = ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(body)
                val requestBody = requestBodyString
                    .toByteArray(StandardCharsets.UTF_8)
                    .toRequestBody()

                requestBuilder.post(requestBody).build()
            } catch (e: Exception) {
                throw RuntimeException("Failed to build CustomOpenAI request", e)
            }
        }
    }
}
