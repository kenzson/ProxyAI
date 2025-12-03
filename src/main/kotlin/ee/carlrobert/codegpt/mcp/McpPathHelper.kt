package ee.carlrobert.codegpt.mcp

import com.intellij.openapi.diagnostic.thisLogger
import java.io.File

object McpPathHelper {

    private val logger = thisLogger()

    fun getAdditionalNodePaths(): List<String> {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        val additionalPaths = mutableListOf<String>()

        when {
            osName.contains("mac") -> {
                additionalPaths.addAll(listOf(
                    "/usr/local/bin",
                    "/opt/homebrew/bin",
                    "/usr/bin",
                    "/usr/local/share/npm/bin",
                    "/opt/homebrew/share/npm/bin",

                    "$userHome/.nvm/current/bin",
                    "$userHome/.nvm/versions/node/current/bin",
                    "$userHome/.volta/bin"
                ))
                additionalPaths.addAll(getShellProfilePaths())
            }

            osName.contains("windows") -> additionalPaths.addAll(listOf(
                "C:\\Program Files\\nodejs",
                "${System.getenv("APPDATA")}\\npm"
            ))

            else -> additionalPaths.addAll(listOf(
                "/usr/local/bin",
                "/usr/bin",
                "$userHome/.nvm/current/bin",
                "$userHome/.nvm/versions/node/current/bin"
            ))
        }
        return additionalPaths.distinct()
    }

    private fun getShellProfilePaths(): List<String> {
        val userHome = System.getProperty("user.home") ?: return emptyList()
        val shellProfiles = listOf(
            File(userHome, ".zshrc"),
            File(userHome, ".bash_profile"),
            File(userHome, ".bashrc"),
            File(userHome, ".profile"),
            File(userHome, ".zprofile")
        )

        val pathEntries = mutableSetOf<String>()

        for (profile in shellProfiles) {
            if (profile.exists()) {
                try {
                    val content = profile.readText()
                    val pathRegex = Regex("""(?:export\s+)?PATH\s*=\s*["']?([^"':]+)(?::[^"']*)?["']?""")
                    pathRegex.findAll(content).forEach { match ->
                        val pathValue = match.groupValues[1]
                        pathValue.split(":").forEach { path ->
                            if (path.isNotBlank()) {
                                pathEntries.add(path)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to read shell profile ${profile.absolutePath}: ${e.message}")
                }
            }
        }

        return pathEntries.toList()
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
