package com.mazzlabs.sentinel.core

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.tools.framework.ToolExecutor
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import com.mazzlabs.sentinel.tools.framework.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AgentController - Main orchestration layer
 * 
 * Routes user requests to either:
 * 1. Tool execution (calendar, clock, contacts, etc.)
 * 2. UI actions (tap, scroll, type via accessibility)
 * 
 * This is the brain that decides what to do with user input.
 */
class AgentController(private val context: Context) {
    
    companion object {
        private const val TAG = "AgentController"
    }
    
    private val toolExecutor = Tools.getInstance(context)
    private val nativeBridge = SentinelApplication.getInstance().nativeBridge
    
    /**
     * Result of processing a user request
     */
    sealed class AgentResult {
        data class ToolResult(val response: ToolResponse) : AgentResult()
        data class UIAction(val action: AgentAction) : AgentResult()
        data class Response(val message: String) : AgentResult()
        data class Error(val message: String) : AgentResult()
    }
    
    /**
     * Process a user query
     * 
     * @param query User's natural language request
     * @param screenContext Current UI state (from accessibility service)
     * @return AgentResult indicating what action was taken
     */
    suspend fun process(query: String, screenContext: String = ""): AgentResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing query: $query")
                
                // Build system prompt with tools
                val systemPrompt = SystemPromptBuilder.build(context, includeTools = true)
                
                // Run inference
                val response = nativeBridge.infer(
                    userQuery = buildUserPrompt(query, screenContext),
                    screenContext = systemPrompt
                )
                
                Log.d(TAG, "LLM response: $response")
                
                // Parse response
                parseAndExecute(response)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing query", e)
                AgentResult.Error("Processing failed: ${e.message}")
            }
        }
    }
    
    /**
     * Build user prompt with optional screen context
     */
    private fun buildUserPrompt(query: String, screenContext: String): String {
        return if (screenContext.isNotBlank()) {
            buildString {
                appendLine("User request: $query")
                appendLine()
                appendLine("Current screen elements:")
                appendLine(screenContext.take(8000)) // Truncate for context window
            }
        } else {
            "User request: $query"
        }
    }
    
    /**
     * Parse LLM output and execute appropriate action
     */
    private suspend fun parseAndExecute(response: String): AgentResult {
        // Extract JSON from response
        val json = extractJson(response) ?: return AgentResult.Error("No valid JSON in response")
        
        return try {
            val jsonObj = JSONObject(json)
            
            when {
                // Tool call
                jsonObj.has("tool") -> {
                    val tool = jsonObj.getString("tool")
                    
                    if (tool == "none") {
                        val reason = jsonObj.optString("reason", "No applicable tool")
                        AgentResult.Response(reason)
                    } else {
                        val params = jsonObjectToMap(jsonObj.optJSONObject("params") ?: JSONObject())
                        Log.d(TAG, "Executing tool: $tool with params: $params")
                        
                        val result = toolExecutor.execute(tool, params)
                        AgentResult.ToolResult(result)
                    }
                }
                
                // UI action
                jsonObj.has("action") -> {
                    val action = AgentAction.fromJson(json)
                    if (action != null) {
                        AgentResult.UIAction(action)
                    } else {
                        AgentResult.Error("Invalid action format")
                    }
                }
                
                // Unknown format
                else -> {
                    AgentResult.Error("Unknown response format: $json")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            AgentResult.Error("Parse error: ${e.message}")
        }
    }
    
    /**
     * Extract JSON from LLM response (may contain extra text)
     */
    private fun extractJson(text: String): String? {
        // Try to find JSON object
        val jsonStart = text.indexOfFirst { it == '{' }
        val jsonEnd = text.indexOfLast { it == '}' }
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val candidate = text.substring(jsonStart, jsonEnd + 1)
            // Validate it's valid JSON
            return try {
                JSONObject(candidate)
                candidate
            } catch (e: Exception) {
                // Try to repair common issues
                repairJson(candidate)
            }
        }
        
        // Try array
        val arrayStart = text.indexOfFirst { it == '[' }
        val arrayEnd = text.indexOfLast { it == ']' }
        
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return text.substring(arrayStart, arrayEnd + 1)
        }
        
        return null
    }
    
    /**
     * Attempt to repair malformed JSON
     */
    private fun repairJson(json: String): String? {
        var repaired = json
        
        // Fix missing closing quotes
        val quotePattern = Regex(""":\s*"([^"]*?)(?=[,}\]])""")
        repaired = quotePattern.replace(repaired) { match ->
            ": \"${match.groupValues[1]}\""
        }
        
        // Fix trailing commas
        repaired = repaired.replace(Regex(",\\s*}"), "}")
        repaired = repaired.replace(Regex(",\\s*]"), "]")
        
        return try {
            JSONObject(repaired)
            repaired
        } catch (e: Exception) {
            null
        }
    }
    
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonObjectToMap(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }
    
    /**
     * Get human-readable result message
     */
    fun formatResult(result: AgentResult): String {
        return when (result) {
            is AgentResult.ToolResult -> {
                when (val response = result.response) {
                    is ToolResponse.Success -> response.message
                    is ToolResponse.Error -> "Error: ${response.message}"
                    is ToolResponse.PermissionRequired -> "Need permissions: ${response.permissions.joinToString()}"
                    is ToolResponse.Confirmation -> response.message
                }
            }
            is AgentResult.UIAction -> {
                "Performing ${result.action.action}: ${result.action.target ?: result.action.text ?: ""}"
            }
            is AgentResult.Response -> result.message
            is AgentResult.Error -> "Error: ${result.message}"
        }
    }
}
