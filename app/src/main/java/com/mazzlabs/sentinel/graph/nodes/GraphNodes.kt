package com.mazzlabs.sentinel.graph.nodes

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.graph.*
import com.mazzlabs.sentinel.tools.Tool
import com.mazzlabs.sentinel.tools.ToolRegistry
import com.mazzlabs.sentinel.tools.ToolResult

/**
 * IntentClassifierNode - Determines user intent from query
 * 
 * Uses the LLM to classify intent into predefined categories.
 */
class IntentClassifierNode(
    private val toolRegistry: ToolRegistry
) : AgentNode {
    
    companion object {
        private const val TAG = "IntentClassifierNode"
    }
    
    override suspend fun process(state: AgentState): AgentState {
        Log.d(TAG, "Classifying intent for: ${state.userQuery}")
        
        val prompt = buildClassificationPrompt(state)
        
        return try {
            val response = SentinelApplication.getInstance()
                .nativeBridge
                .infer(prompt, "")
            
            Log.d(TAG, "Classification response: $response")
            
            val (intent, entities) = parseClassificationResponse(response)
            
            state.copy(
                intent = intent,
                extractedEntities = entities,
                currentNode = "intent_classifier"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Intent classification failed", e)
            state.copy(
                intent = AgentIntent.UNKNOWN,
                error = "Failed to classify intent: ${e.message}"
            )
        }
    }
    
    private fun buildClassificationPrompt(state: AgentState): String {
        val toolsDesc = toolRegistry.generateToolsPrompt()
        
        return """
Classify the user's intent and extract relevant entities.

User query: "${state.userQuery}"

$toolsDesc

Respond with JSON only:
{
    "intent": "one of: READ_CALENDAR, CREATE_EVENT, CREATE_ALARM, CALL_CONTACT, SEND_SMS, SEARCH, CLICK_ELEMENT, SCROLL_SCREEN, TYPE_TEXT, GO_BACK, GO_HOME, ANSWER_QUESTION, UNKNOWN",
    "entities": {
        "relevant_key": "extracted_value"
    },
    "selected_tool": "tool_name or null if UI action",
    "reasoning": "brief explanation"
}
""".trimIndent()
    }
    
    private fun parseClassificationResponse(response: String): Pair<AgentIntent, Map<String, String>> {
        return try {
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val parsed: Map<String, Any> = gson.fromJson(response, type)
            
            val intentStr = parsed["intent"]?.toString()?.uppercase() ?: "UNKNOWN"
            val intent = try {
                AgentIntent.valueOf(intentStr)
            } catch (e: Exception) {
                AgentIntent.UNKNOWN
            }
            
            @Suppress("UNCHECKED_CAST")
            val entities = (parsed["entities"] as? Map<String, Any>)
                ?.mapValues { it.value.toString() }
                ?: emptyMap()
            
            intent to entities
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse classification", e)
            AgentIntent.UNKNOWN to emptyMap()
        }
    }
}

/**
 * ToolSelectorNode - Selects appropriate tool based on intent
 */
class ToolSelectorNode(
    private val toolRegistry: ToolRegistry
) : AgentNode {
    
    companion object {
        private const val TAG = "ToolSelectorNode"
        
        private val INTENT_TO_TOOL = mapOf(
            AgentIntent.READ_CALENDAR to "calendar_read",
            AgentIntent.CREATE_EVENT to "calendar_create",
            AgentIntent.CREATE_ALARM to "alarm_create",
            AgentIntent.CALL_CONTACT to "phone_call",
            AgentIntent.SEND_SMS to "sms_send"
        )
    }
    
    override suspend fun process(state: AgentState): AgentState {
        val intent = state.intent ?: return state.copy(error = "No intent classified")
        
        val toolName = INTENT_TO_TOOL[intent]
        
        return if (toolName != null && toolRegistry.get(toolName) != null) {
            Log.d(TAG, "Selected tool: $toolName for intent: $intent")
            state.copy(
                selectedTool = toolName,
                currentNode = "tool_selector"
            )
        } else {
            Log.d(TAG, "No tool for intent: $intent - will use UI action")
            state.copy(
                selectedTool = null,
                currentNode = "tool_selector"
            )
        }
    }
}

/**
 * ParameterExtractorNode - Extracts tool parameters from entities
 */
class ParameterExtractorNode(
    private val toolRegistry: ToolRegistry
) : AgentNode {
    
    companion object {
        private const val TAG = "ParameterExtractorNode"
    }
    
    override suspend fun process(state: AgentState): AgentState {
        val toolName = state.selectedTool 
            ?: return state.copy(error = "No tool selected")
        
        val tool = toolRegistry.get(toolName)
            ?: return state.copy(error = "Tool not found: $toolName")
        
        // If entities already contain enough info, use them
        if (state.extractedEntities.isNotEmpty()) {
            Log.d(TAG, "Using pre-extracted entities: ${state.extractedEntities}")
            return state.copy(
                toolInput = state.extractedEntities,
                currentNode = "param_extractor"
            )
        }
        
        // Otherwise, use LLM to extract parameters
        val prompt = buildExtractionPrompt(state, tool)
        
        return try {
            val response = SentinelApplication.getInstance()
                .nativeBridge
                .infer(prompt, "")
            
            val params = parseParameters(response)
            
            state.copy(
                toolInput = params,
                currentNode = "param_extractor"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parameter extraction failed", e)
            state.copy(error = "Failed to extract parameters: ${e.message}")
        }
    }
    
    private fun buildExtractionPrompt(state: AgentState, tool: Tool): String {
        return """
Extract parameters for the ${tool.name} tool from the user's request.

User query: "${state.userQuery}"

Tool schema:
${tool.parametersSchema}

Respond with JSON containing only the parameter values:
{
    "param_name": "value",
    ...
}
""".trimIndent()
    }
    
    private fun parseParameters(response: String): Map<String, Any?> {
        return try {
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(response, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse parameters", e)
            emptyMap()
        }
    }
}

/**
 * ToolExecutorNode - Executes the selected tool
 */
class ToolExecutorNode(
    private val toolRegistry: ToolRegistry,
    private val context: Context
) : AgentNode {
    
    companion object {
        private const val TAG = "ToolExecutorNode"
    }
    
    override suspend fun process(state: AgentState): AgentState {
        val toolName = state.selectedTool
            ?: return state.copy(error = "No tool selected")
        
        val tool = toolRegistry.get(toolName)
            ?: return state.copy(error = "Tool not found: $toolName")
        
        Log.d(TAG, "Executing tool: $toolName with params: ${state.toolInput}")
        
        // Validate parameters
        val validation = tool.validateParams(state.toolInput)
        if (validation is com.mazzlabs.sentinel.tools.ValidationResult.Invalid) {
            return state.copy(error = "Invalid parameters: ${validation.reason}")
        }
        
        // Check permissions
        if (!toolRegistry.hasPermissions(context, toolName)) {
            return state.copy(error = "Missing permissions for $toolName")
        }
        
        // Execute
        val result = tool.execute(state.toolInput, context)
        
        return state.copy(
            toolResults = state.toolResults + result,
            currentNode = "tool_executor"
        )
    }
}

/**
 * ResponseGeneratorNode - Generates final response from tool results
 */
class ResponseGeneratorNode : AgentNode {
    
    companion object {
        private const val TAG = "ResponseGeneratorNode"
    }
    
    override suspend fun process(state: AgentState): AgentState {
        val lastResult = state.toolResults.lastOrNull()
        
        val response = when (lastResult) {
            is ToolResult.Success -> {
                "${lastResult.message}\n${formatData(lastResult.data)}"
            }
            is ToolResult.Failure -> {
                "Sorry, I couldn't complete that: ${lastResult.message}"
            }
            null -> {
                "I processed your request but have no specific result to report."
            }
        }
        
        return state.copy(
            response = response,
            isComplete = true,
            currentNode = "response_generator"
        )
    }
    
    private fun formatData(data: Map<String, Any?>): String {
        if (data.isEmpty()) return ""
        
        return buildString {
            data.forEach { (key, value) ->
                when (value) {
                    is List<*> -> {
                        appendLine("$key:")
                        value.forEach { item ->
                            appendLine("  - $item")
                        }
                    }
                    else -> appendLine("$key: $value")
                }
            }
        }
    }
}

/**
 * UIActionNode - Generates UI actions when no tool is applicable
 */
class UIActionNode : AgentNode {
    
    companion object {
        private const val TAG = "UIActionNode"
    }
    
    override suspend fun process(state: AgentState): AgentState {
        val intent = state.intent ?: AgentIntent.UNKNOWN
        
        val action = when (intent) {
            AgentIntent.GO_BACK -> AgentAction(ActionType.BACK, reasoning = "User requested to go back")
            AgentIntent.GO_HOME -> AgentAction(ActionType.HOME, reasoning = "User requested to go home")
            AgentIntent.SCROLL_SCREEN -> {
                val direction = state.extractedEntities["direction"] ?: "down"
                AgentAction(ActionType.SCROLL, direction = direction, reasoning = "User requested scroll")
            }
            AgentIntent.CLICK_ELEMENT -> {
                val target = state.extractedEntities["target"] ?: state.extractedEntities["element"]
                AgentAction(ActionType.CLICK, target = target, reasoning = "User requested click")
            }
            AgentIntent.TYPE_TEXT -> {
                val text = state.extractedEntities["text"] ?: ""
                val target = state.extractedEntities["field"]
                AgentAction(ActionType.TYPE, target = target, text = text, reasoning = "User requested text input")
            }
            else -> AgentAction(ActionType.NONE, reasoning = "No action determined")
        }
        
        return state.copy(
            action = action,
            isComplete = true,
            currentNode = "ui_action"
        )
    }
}
