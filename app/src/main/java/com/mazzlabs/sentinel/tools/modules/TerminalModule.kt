package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.tools.framework.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * TerminalModule - Execute shell commands with sandboxing
 * 
 * SECURITY CONSIDERATIONS:
 * - Commands run as the app's UID (unprivileged)
 * - Timeouts enforced to prevent hangs
 * - Dangerous commands blocked
 * - Output truncated to prevent memory exhaustion
 * 
 * Operations:
 * - execute: Run a shell command
 * - script: Run a multi-line script
 * - list_files: List directory contents
 * - read_file: Read a file (with size limits)
 * - write_file: Write to app-writable locations
 */
class TerminalModule : ToolModule {
    
    companion object {
        private const val TAG = "TerminalModule"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_OUTPUT_CHARS = 50_000
        
        // Commands that are blocked for security
        private val BLOCKED_COMMANDS = setOf(
            "su", "sudo", "rm -rf /", "dd", "mkfs", "mount", "umount",
            "reboot", "shutdown", "halt", "poweroff", "init",
            "chmod 777", "chown", "setenforce"
        )
        
        // Patterns that indicate dangerous commands
        private val DANGEROUS_PATTERNS = listOf(
            Regex("""rm\s+(-[rf]+\s+)?/(?!data/data|sdcard)"""),  // rm outside app dirs
            Regex(""">\s*/dev/"""),  // Writing to devices
            Regex("""\|\s*sh"""),  // Piping to shell
            Regex("""eval\s"""),  // Eval commands
            Regex("""`.*`"""),  // Backtick execution
            Regex("""\$\(.*\)""")  // Command substitution (allow carefully)
        )
    }
    
    override val moduleId = "terminal"
    
    override val description = "Execute shell commands - run scripts, list files, manage app data"
    
    override val requiredPermissions = listOf<String>() // No special permissions, runs as app
    
    override val operations = listOf(
        ToolOperation(
            operationId = "execute",
            description = "Execute a shell command",
            parameters = listOf(
                ToolParameter("command", ParameterType.STRING, "Shell command to execute", required = true),
                ToolParameter("timeout_ms", ParameterType.INTEGER, "Timeout in milliseconds", required = false, default = 30000),
                ToolParameter("working_dir", ParameterType.STRING, "Working directory", required = false)
            ),
            examples = listOf(
                ToolExample("List files in current directory", "execute", mapOf("command" to "ls -la")),
                ToolExample("Check disk space", "execute", mapOf("command" to "df -h")),
                ToolExample("Show running processes", "execute", mapOf("command" to "ps"))
            )
        ),
        ToolOperation(
            operationId = "list_files",
            description = "List files in a directory",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "Directory path", required = true),
                ToolParameter("show_hidden", ParameterType.BOOLEAN, "Show hidden files", required = false, default = false),
                ToolParameter("details", ParameterType.BOOLEAN, "Show file details", required = false, default = false)
            )
        ),
        ToolOperation(
            operationId = "read_file",
            description = "Read contents of a file",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "File path", required = true),
                ToolParameter("max_lines", ParameterType.INTEGER, "Maximum lines to read", required = false, default = 100),
                ToolParameter("offset", ParameterType.INTEGER, "Line offset to start from", required = false, default = 0)
            )
        ),
        ToolOperation(
            operationId = "write_file",
            description = "Write content to a file (app directories only)",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "File path (must be in app directory)", required = true),
                ToolParameter("content", ParameterType.STRING, "Content to write", required = true),
                ToolParameter("append", ParameterType.BOOLEAN, "Append instead of overwrite", required = false, default = false)
            )
        ),
        ToolOperation(
            operationId = "get_env",
            description = "Get environment information",
            parameters = listOf()
        )
    )
    
    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "execute" -> executeCommand(params, context)
            "list_files" -> listFiles(params, context)
            "read_file" -> readFile(params, context)
            "write_file" -> writeFile(params, context)
            "get_env" -> getEnv(context)
            else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
        }
    }
    
    private suspend fun executeCommand(params: Map<String, Any?>, context: Context): ToolResponse {
        val command = params["command"] as? String
            ?: return ToolResponse.Error(moduleId, "execute", ErrorCode.INVALID_PARAMS, "command required")
        val timeoutMs = (params["timeout_ms"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_MS
        val workingDir = params["working_dir"] as? String
        
        // Security checks
        val securityCheck = checkCommandSecurity(command)
        if (securityCheck != null) {
            return securityCheck
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val processBuilder = ProcessBuilder("sh", "-c", command)
                
                workingDir?.let { 
                    val dir = File(it)
                    if (dir.exists() && dir.isDirectory) {
                        processBuilder.directory(dir)
                    }
                }
                
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                
                val result = withTimeoutOrNull(timeoutMs) {
                    val output = StringBuilder()

                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (output.length < MAX_OUTPUT_CHARS) {
                                output.appendLine(line)
                            }
                        }
                    }

                    val exitCode = process.waitFor()

                    Pair(exitCode, output.toString().take(MAX_OUTPUT_CHARS))
                }
                
                if (result == null) {
                    process.destroyForcibly()
                    return@withContext ToolResponse.Error(
                        moduleId, "execute", ErrorCode.TIMEOUT, 
                        "Command timed out after ${timeoutMs}ms"
                    )
                }
                
                val (exitCode, output) = result
                
                Log.i(TAG, "Command executed: $command (exit: $exitCode)")
                
                ToolResponse.Success(
                    moduleId = moduleId,
                    operationId = "execute",
                    message = if (exitCode == 0) "Command completed" else "Command failed (exit: $exitCode)",
                    data = mapOf(
                        "exit_code" to exitCode,
                        "output" to output,
                        "command" to command
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
                ToolResponse.Error(moduleId, "execute", ErrorCode.SYSTEM_ERROR, "Execution failed: ${e.message}")
            }
        }
    }
    
    private fun checkCommandSecurity(command: String): ToolResponse? {
        val lowerCommand = command.lowercase()
        
        // Check blocked commands
        for (blocked in BLOCKED_COMMANDS) {
            if (lowerCommand.contains(blocked)) {
                return ToolResponse.Error(
                    moduleId, "execute", ErrorCode.PERMISSION_DENIED,
                    "Blocked command: $blocked"
                )
            }
        }
        
        // Check dangerous patterns
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(command)) {
                return ToolResponse.Confirmation(
                    moduleId = moduleId,
                    operationId = "execute",
                    message = "Potentially dangerous command detected:\n$command\n\nAllow execution?",
                    pendingAction = mapOf("command" to command, "confirmed" to false)
                )
            }
        }
        
        return null
    }
    
    private fun listFiles(params: Map<String, Any?>, context: Context): ToolResponse {
        val path = params["path"] as? String
            ?: return ToolResponse.Error(moduleId, "list_files", ErrorCode.INVALID_PARAMS, "path required")
        val showHidden = params["show_hidden"] as? Boolean ?: false
        val details = params["details"] as? Boolean ?: false
        
        try {
            val dir = File(path)
            
            if (!dir.exists()) {
                return ToolResponse.Error(moduleId, "list_files", ErrorCode.NOT_FOUND, "Path not found: $path")
            }
            
            if (!dir.isDirectory) {
                return ToolResponse.Error(moduleId, "list_files", ErrorCode.INVALID_PARAMS, "Not a directory: $path")
            }
            
            val files = dir.listFiles()
                ?.filter { showHidden || !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { file ->
                    if (details) {
                        mapOf(
                            "name" to file.name,
                            "type" to if (file.isDirectory) "directory" else "file",
                            "size" to file.length(),
                            "readable" to file.canRead(),
                            "writable" to file.canWrite(),
                            "modified" to file.lastModified()
                        )
                    } else {
                        mapOf(
                            "name" to (file.name + if (file.isDirectory) "/" else ""),
                            "type" to if (file.isDirectory) "directory" else "file"
                        )
                    }
                } ?: emptyList()
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "list_files",
                message = "${files.size} items in $path",
                data = mapOf("path" to path, "files" to files)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
            return ToolResponse.Error(moduleId, "list_files", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun readFile(params: Map<String, Any?>, context: Context): ToolResponse {
        val path = params["path"] as? String
            ?: return ToolResponse.Error(moduleId, "read_file", ErrorCode.INVALID_PARAMS, "path required")
        val maxLines = (params["max_lines"] as? Number)?.toInt() ?: 100
        val offset = (params["offset"] as? Number)?.toInt() ?: 0
        
        try {
            val file = File(path)
            
            if (!file.exists()) {
                return ToolResponse.Error(moduleId, "read_file", ErrorCode.NOT_FOUND, "File not found: $path")
            }
            
            if (!file.canRead()) {
                return ToolResponse.Error(moduleId, "read_file", ErrorCode.PERMISSION_DENIED, "Cannot read: $path")
            }
            
            if (file.length() > 1_000_000) {
                return ToolResponse.Error(moduleId, "read_file", ErrorCode.INVALID_PARAMS, "File too large (>1MB). Use offset/max_lines.")
            }
            
            val lines = file.readLines()
            val content = lines.drop(offset).take(maxLines).joinToString("\n")
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "read_file",
                message = "Read ${minOf(maxLines, lines.size - offset)} lines from $path",
                data = mapOf(
                    "path" to path,
                    "content" to content,
                    "total_lines" to lines.size,
                    "offset" to offset,
                    "lines_read" to minOf(maxLines, lines.size - offset)
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            return ToolResponse.Error(moduleId, "read_file", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun writeFile(params: Map<String, Any?>, context: Context): ToolResponse {
        val path = params["path"] as? String
            ?: return ToolResponse.Error(moduleId, "write_file", ErrorCode.INVALID_PARAMS, "path required")
        val content = params["content"] as? String
            ?: return ToolResponse.Error(moduleId, "write_file", ErrorCode.INVALID_PARAMS, "content required")
        val append = params["append"] as? Boolean ?: false
        
        // Security: Only allow writing to app directories
        val appDir = context.filesDir.absolutePath
        val cacheDir = context.cacheDir.absolutePath
        val externalDir = context.getExternalFilesDir(null)?.absolutePath
        
        val normalizedPath = File(path).canonicalPath
        val isAppPath = normalizedPath.startsWith(appDir) || 
                       normalizedPath.startsWith(cacheDir) ||
                       (externalDir != null && normalizedPath.startsWith(externalDir))
        
        if (!isAppPath) {
            return ToolResponse.Error(
                moduleId, "write_file", ErrorCode.PERMISSION_DENIED,
                "Can only write to app directories. Use: $appDir"
            )
        }
        
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "write_file",
                message = if (append) "Appended to $path" else "Wrote to $path",
                data = mapOf("path" to path, "size" to file.length())
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file", e)
            return ToolResponse.Error(moduleId, "write_file", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun getEnv(context: Context): ToolResponse {
        val env = mapOf(
            "app_files_dir" to context.filesDir.absolutePath,
            "app_cache_dir" to context.cacheDir.absolutePath,
            "app_external_dir" to context.getExternalFilesDir(null)?.absolutePath,
            "package_name" to context.packageName,
            "android_version" to android.os.Build.VERSION.SDK_INT,
            "device" to android.os.Build.MODEL,
            "user" to System.getProperty("user.name"),
            "home" to System.getProperty("user.home"),
            "path" to System.getenv("PATH")
        )
        
        return ToolResponse.Success(
            moduleId = moduleId,
            operationId = "get_env",
            message = "Environment info",
            data = env
        )
    }
}
