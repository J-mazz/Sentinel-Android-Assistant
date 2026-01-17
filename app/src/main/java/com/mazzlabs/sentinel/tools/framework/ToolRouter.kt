package com.mazzlabs.sentinel.tools.framework

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

/**
 * ToolRouter - Central hub for tool discovery and execution
 * 
 * Responsibilities:
 * 1. Registry of all available tool modules
 * 2. Schema generation for model prompts
 * 3. Permission checking before execution
 * 4. Routing calls to appropriate modules
 */
class ToolRouter(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolRouter"
    }
    
    private val modules = mutableMapOf<String, ToolModule>()
    private val mutex = Mutex()
    
    /**
     * Register a tool module
     */
    fun register(module: ToolModule) {
        modules[module.moduleId] = module
        Log.i(TAG, "Registered module: ${module.moduleId} with ${module.operations.size} operations")
    }
    
    /**
     * Get all registered modules
     */
    fun getModules(): List<ToolModule> = modules.values.toList()
    
    /**
     * Get available modules (device supports + permissions granted)
     */
    fun getAvailableModules(): List<ToolModule> {
        return modules.values.filter { module ->
            module.isAvailable(context) && hasPermissions(module.requiredPermissions)
        }
    }
    
    /**
     * Get modules that need permission grants
     */
    fun getModulesNeedingPermissions(): Map<ToolModule, List<String>> {
        return modules.values
            .filter { it.isAvailable(context) }
            .associateWith { getMissingPermissions(it.requiredPermissions) }
            .filter { it.value.isNotEmpty() }
    }
    
    /**
     * Generate full tool schema for model context
     */
    fun generateSchema(): String {
        val sb = StringBuilder()
        sb.appendLine("# Available Tools")
        sb.appendLine()
        sb.appendLine("You can use these tools to help the user. Call a tool by outputting JSON:")
        sb.appendLine("```json")
        sb.appendLine("{\"tool\": \"module.operation\", \"params\": {...}}")
        sb.appendLine("```")
        sb.appendLine()
        
        for (module in getAvailableModules()) {
            sb.append(module.toSchemaString())
            sb.appendLine("---")
            sb.appendLine()
        }
        
        // Note unavailable modules
        val unavailable = modules.values.filter { !it.isAvailable(context) || !hasPermissions(it.requiredPermissions) }
        if (unavailable.isNotEmpty()) {
            sb.appendLine("## Unavailable Tools (permissions needed)")
            for (module in unavailable) {
                val missing = getMissingPermissions(module.requiredPermissions)
                sb.appendLine("- ${module.moduleId}: needs ${missing.joinToString()}")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * Generate compact tool list for smaller context
     */
    fun generateCompactSchema(): String {
        val sb = StringBuilder()
        sb.appendLine("Tools: ${getAvailableModules().joinToString { it.moduleId }}")
        sb.appendLine()
        
        for (module in getAvailableModules()) {
            val ops = module.operations.joinToString { "${it.operationId}(${it.parameters.filter { p -> p.required }.joinToString { p -> p.name }})" }
            sb.appendLine("${module.moduleId}: $ops")
        }
        
        return sb.toString()
    }
    
    /**
     * Execute a tool call
     * 
     * @param toolCall Format: "module.operation" or just "operation" if unambiguous
     * @param params Parameters for the operation
     */
    suspend fun execute(toolCall: String, params: Map<String, Any?>): ToolResponse {
        return mutex.withLock {
            val (moduleId, operationId) = parseToolCall(toolCall)
                ?: return@withLock ToolResponse.Error(
                    moduleId = "unknown",
                    operationId = toolCall,
                    errorCode = ErrorCode.INVALID_PARAMS,
                    message = "Invalid tool call format: $toolCall"
                )
            
            val module = modules[moduleId]
                ?: return@withLock ToolResponse.Error(
                    moduleId = moduleId,
                    operationId = operationId,
                    errorCode = ErrorCode.NOT_FOUND,
                    message = "Module not found: $moduleId"
                )
            
            // Check permissions
            val missingPerms = getMissingPermissions(module.requiredPermissions)
            if (missingPerms.isNotEmpty()) {
                return@withLock ToolResponse.PermissionRequired(
                    moduleId = moduleId,
                    operationId = operationId,
                    permissions = missingPerms
                )
            }
            
            // Check availability
            if (!module.isAvailable(context)) {
                return@withLock ToolResponse.Error(
                    moduleId = moduleId,
                    operationId = operationId,
                    errorCode = ErrorCode.NOT_AVAILABLE,
                    message = "Module not available on this device"
                )
            }
            
            // Execute
            try {
                Log.d(TAG, "Executing $moduleId.$operationId with params: $params")
                val result = withTimeoutOrNull(30.seconds) {
                    module.execute(operationId, params, context)
                }
                result ?: ToolResponse.Error(
                    moduleId = moduleId,
                    operationId = operationId,
                    errorCode = ErrorCode.TIMEOUT,
                    message = "Tool execution timed out after 30 seconds"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error executing $moduleId.$operationId", e)
                ToolResponse.Error(
                    moduleId = moduleId,
                    operationId = operationId,
                    errorCode = ErrorCode.SYSTEM_ERROR,
                    message = "Execution failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Parse tool call string into module and operation
     */
    private fun parseToolCall(toolCall: String): Pair<String, String>? {
        // Format: "module.operation"
        if (toolCall.contains(".")) {
            val parts = toolCall.split(".", limit = 2)
            if (parts.size == 2) {
                return parts[0] to parts[1]
            }
        }
        
        // Try to find operation across all modules
        for ((moduleId, module) in modules) {
            if (module.operations.any { it.operationId == toolCall }) {
                return moduleId to toolCall
            }
        }
        
        return null
    }
    
    private fun hasPermissions(permissions: List<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun getMissingPermissions(permissions: List<String>): List<String> {
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}
