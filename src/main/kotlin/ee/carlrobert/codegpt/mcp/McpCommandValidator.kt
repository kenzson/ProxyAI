package ee.carlrobert.codegpt.mcp

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import java.io.File

object McpCommandValidator {

    private val logger = thisLogger()

    fun resolveCommand(command: String): String? {
        val commandFile = File(command)
        if (commandFile.isAbsolute && commandFile.exists() && commandFile.canExecute()) {
            return command
        }

        return when {
            command == "npx" || command == "node" -> findNodeExecutable(command)
            else -> PathEnvironmentVariableUtil.findInPath(command)?.absolutePath
        }
    }

    private fun findNodeExecutable(command: String): String? {
        PathEnvironmentVariableUtil.findInPath(command)?.let {
            return it.absolutePath
        }

        findInCommonMacOSLocations(command)?.let {
            return it
        }

        findViaNodeVersionManager(command)?.let {
            return it
        }

        findViaEnvironmentHints(command)?.let {
            return it
        }

        logger.warn("$command not found in any location")
        return null
    }

    private fun findInCommonMacOSLocations(command: String): String? {
        if (!SystemInfo.isMac) {
            return null
        }

        val commonLocations = listOf(
            "/usr/local/bin",           // Homebrew (Intel Mac)
            "/opt/homebrew/bin",        // Homebrew (Apple Silicon)
            "/usr/bin",                 // System Node.js
            "/usr/local/share/npm/bin", // npm global bin
            "/opt/homebrew/share/npm/bin" // npm global bin (Apple Silicon)
        )

        for (location in commonLocations) {
            findExecutableInDirectory(File(location), command)?.let { return it }
        }

        return null
    }

    private fun findViaNodeVersionManager(command: String): String? {
        val userHome = System.getProperty("user.home") ?: return null

        System.getenv("NVM_DIR")?.let { nvmDir ->
            findExecutableInDirectory(File(nvmDir, "current/bin"), command)?.let { return it }
        }
        findExecutableInDirectory(File(userHome, ".nvm/current/bin"), command)?.let { return it }
        System.getenv("VOLTA_HOME")?.let { voltaHome ->
            findExecutableInDirectory(File(voltaHome, "bin"), command)?.let { return it }
        }
        findExecutableInDirectory(File(userHome, ".volta/bin"), command)?.let { return it }
        System.getenv("FNM_DIR")?.let { fnmDir ->
            findExecutableInDirectory(File(fnmDir), command)?.let { return it }
        }

        return null
    }

    private fun findViaEnvironmentHints(command: String): String? {
        System.getenv("NODE_PATH")?.let { nodePath ->
            File(nodePath).parent?.let { binDir ->
                findExecutableInDirectory(File(binDir), command)?.let { return it }
            }
        }
        System.getenv("NPM_CONFIG_PREFIX")?.let { prefix ->
            findExecutableInDirectory(File(prefix, "bin"), command)?.let { return it }
        }

        return null
    }

    private fun findExecutableInDirectory(directory: File, command: String): String? {
        if (!directory.exists() || !directory.isDirectory) {
            return null
        }

        val executable = File(directory, command)
        if (executable.exists() && executable.canExecute()) {
            return executable.absolutePath
        }

        if (SystemInfo.isWindows) {
            for (ext in listOf(".exe", ".cmd", ".bat")) {
                val executableWithExt = File(directory, command + ext)
                if (executableWithExt.exists() && executableWithExt.canExecute()) {
                    return executableWithExt.absolutePath
                }
            }
        }

        return null
    }

    fun getCommandNotFoundMessage(command: String): String {
        return buildString {
            append("Command '$command' not found. ")

            when (command) {
                "npx", "node" -> {
                    append(
                        if (command == "npx") "Node.js/npm is required for MCP servers. "
                        else "Node.js is required. "
                    )

                    append("Please ensure Node.js is installed and available in PATH. ")
                    append("Visit https://nodejs.org/ for installation instructions. ")

                    if (SystemInfo.isMac) {
                        append("Common installation methods: Homebrew (brew install node), nvm, or volta.")
                    } else if (SystemInfo.isWindows) {
                        append("Common installation methods: Official installer, winget, or volta.")
                    } else {
                        append("Common installation methods: Package manager, nvm, or volta.")
                    }
                }

                else -> {
                    append("Make sure it's installed and available in PATH. ")
                    append("You can also specify the full path to the executable in the MCP settings.")
                }
            }
        }
    }
}