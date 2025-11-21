package ee.carlrobert.codegpt.mcp

import java.io.File

object McpPathHelper {

    fun getAdditionalNodePaths(): List<String> {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            osName.contains("mac") -> listOf(
                "/usr/local/bin",
                "/opt/homebrew/bin",
                "$userHome/.nvm/versions/node/current/bin"
            )

            osName.contains("windows") -> listOf(
                "C:\\Program Files\\nodejs",
                "${System.getenv("APPDATA")}\\npm"
            )

            else ->
                listOf(
                    "/usr/local/bin",
                    "/usr/bin",
                    "$userHome/.nvm/versions/node/current/bin"
                )
        }
    }

    fun createEnvironmentPath(environment: MutableMap<String, String>): MutableMap<String, String> {
        val currentPath = environment["PATH"] ?: ""
        val additionalPaths = getAdditionalNodePaths()
        val pathSeparator = File.pathSeparator

        val enhancedPath = (listOf(currentPath) + additionalPaths)
            .filter { it.isNotEmpty() }
            .joinToString(pathSeparator)

        environment["PATH"] = enhancedPath
        return environment
    }

    fun createEnvironment(serverEnvironmentVariables: Map<String, String>): MutableMap<String, String> {
        val mergedEnv = mutableMapOf<String, String>()
        mergedEnv.putAll(System.getenv())
        mergedEnv.putAll(serverEnvironmentVariables)
        return createEnvironmentPath(mergedEnv)
    }
}