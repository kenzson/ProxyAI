package ee.carlrobert.codegpt.settings.mcp.form

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import ee.carlrobert.codegpt.settings.mcp.McpSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

class McpFileProvider {

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(Jdk8Module())
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule.Builder().build())
        .apply { configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) }

    suspend fun writeSettings(path: String, data: McpSettingsState) = withContext(Dispatchers.IO) {
        val serializedData = objectMapper.writeValueAsString(data)
        FileWriter(path).use {
            it.write(serializedData)
        }
    }

    fun readFromFile(path: String): McpSettingsState =
        objectMapper.readValue<McpSettingsState>(File(path))
}