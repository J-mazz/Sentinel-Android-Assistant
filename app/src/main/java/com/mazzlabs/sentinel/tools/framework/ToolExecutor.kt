package com.mazzlabs.sentinel.tools.framework

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.tools.modules.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * ToolExecutor - Bridge between LLM output and tool execution
 * 
 * Responsibilities:
 * 1. Parse LLM tool call output (JSON)
 * 2. Validate parameters against schema
 * 3. Route to appropriate module
 * 4. Format results for LLM context
 */
class ToolExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolExecutor"
    }
    
    private val router = ToolRouter(context)
    
    init {
        // Register all modules
        router.register(CalendarModule())
        router.register(ClockModule())
        router.register(ContactsModule())
        router.register(MessagingModule())
        router.register(NotesModule())
        router.register(TerminalModule())
        
        Log.i(TAG, "ToolExecutor initialized with ${router.getModules().size} modules")
    }
    
    /**
     * Get tool schema for inclusion in system prompt
     */
    fun getToolSchema(compact: Boolean = false): String {
        return if (compact) {
            router.generateCompactSchema()
        } else {
            router.generateSchema()
        }
    }
    
    /**
     * Get list of available module IDs
     */
    fun getAvailableModules(): List<String> {
        return router.getAvailableModules().map { it.moduleId }
    }
    
    /**
     * Get modules that need permissions
     */
    fun getModulesNeedingPermissions(): Map<String, List<String>> {
        return router.getModulesNeedingPermissions().mapKeys { it.key.moduleId }
    }
    
    /**
     * Parse and execute a tool call from LLM output
     * 
     * Expected JSON format:
     * {"tool": "module.operation", "params": {...}}
     * 
     * Or for multiple calls:
     * [{"tool": "...", "params": {...}}, ...]
     */
    suspend fun executeFromJson(json: String): List<ToolResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val trimmed = json.trim()
                
                when {
                    trimmed.startsWith("[") -> {
                        // Array of tool calls
                        val array = JSONArray(trimmed)
                        (0 until array.length()).map { i ->
                            executeSingleCall(array.getJSONObject(i))
                        }
                    }
                    trimmed.startsWith("{") -> {
                        // Single tool call
                        listOf(executeSingleCall(JSONObject(trimmed)))
                    }
                    else -> {
                        listOf(ToolResponse.Error(
                            "unknown", "parse", ErrorCode.INVALID_PARAMS,
                            "Invalid JSON format"
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing tool call JSON", e)
                listOf(ToolResponse.Error(
                    "unknown", "parse", ErrorCode.INVALID_PARAMS,
                    "Failed to parse tool call: ${e.message}"
                ))
            }
        }
    }
    
    private suspend fun executeSingleCall(json: JSONObject): ToolResponse {
        val tool = json.optString("tool", "")
        if (tool.isEmpty()) {
            return ToolResponse.Error("unknown", "parse", ErrorCode.INVALID_PARAMS, "Missing 'tool' field")
        }
        
        val paramsJson = json.optJSONObject("params") ?: JSONObject()
        val params = jsonObjectToMap(paramsJson)
        
        Log.d(TAG, "Executing tool: $tool with params: $params")
        
        return router.execute(tool, params)
    }
    
    /**
     * Execute a tool call directly
     */
    suspend fun execute(tool: String, params: Map<String, Any?>): ToolResponse {
        return router.execute(tool, params)
    }
    
    /**
     * Format tool response for LLM context
     */
    fun formatResponseForLlm(response: ToolResponse): String {
        return when (response) {
            is ToolResponse.Success -> {
                buildString {
                    appendLine("[Tool Result: ${response.moduleId}.${response.operationId}]")
                    appendLine("Status: SUCCESS")
                    appendLine("Message: ${response.message}")
                    if (response.data.isNotEmpty()) {
                        appendLine("Data: ${formatData(response.data)}")
                    }
                }
            }
            is ToolResponse.Error -> {
                buildString {
                    appendLine("[Tool Result: ${response.moduleId}.${response.operationId}]")
                    appendLine("Status: ERROR (${response.errorCode})")
                    appendLine("Message: ${response.message}")
                }
            }
            is ToolResponse.PermissionRequired -> {
                buildString {
                    appendLine("[Tool Result: ${response.moduleId}.${response.operationId}]")
                    appendLine("Status: PERMISSION_REQUIRED")
                    appendLine("Needs: ${response.permissions.joinToString()}")
                }
            }
            is ToolResponse.Confirmation -> {
                buildString {
                    appendLine("[Tool Result: ${response.moduleId}.${response.operationId}]")
                    appendLine("Status: CONFIRMATION_REQUIRED")
                    appendLine("Message: ${response.message}")
                }
            }
        }
    }
    
    private fun formatData(data: Map<String, Any?>, indent: Int = 0): String {
        val prefix = "  ".repeat(indent)
        return data.entries.joinToString("\n") { (key, value) ->
            when (value) {
                is List<*> -> {
                    if (value.isEmpty()) {
                        "$prefix$key: []"
                    } else if (value.first() is Map<*, *>) {
                        // List of objects - format each
                        val items = value.mapIndexed { i, item ->
                            @Suppress("UNCHECKED_CAST")
                            "$prefix  [$i]: ${formatData(item as Map<String, Any?>, indent + 2)}"
                        }.joinToString("\n")
                        "$prefix$key:\n$items"
                    } else {
                        "$prefix$key: ${value.joinToString(", ")}"
                    }
                }
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    "$prefix$key:\n${formatData(value as Map<String, Any?>, indent + 1)}"
                }
                else -> "$prefix$key: $value"
            }
        }
    }
    
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }
    
    private fun jsonArrayToList(array: JSONArray): List<Any?> {
        return (0 until array.length()).map { i ->
            when (val value = array.get(i)) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
    }
}

/**
 * Singleton access for ToolExecutor
 */
object Tools {
    @Volatile
    private var instance: ToolExecutor? = null
    
    fun getInstance(context: Context): ToolExecutor {
        return instance ?: synchronized(this) {
            instance ?: ToolExecutor(context.applicationContext).also { instance = it }
        }
    }
}
