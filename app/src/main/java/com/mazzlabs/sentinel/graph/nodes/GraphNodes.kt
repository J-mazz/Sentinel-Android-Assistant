package com.mazzlabs.sentinel.graph.nodes

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.inference.InferenceOptions
import com.mazzlabs.sentinel.graph.*
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.model.ScrollDirection
import com.mazzlabs.sentinel.tools.framework.ToolExecutor
import com.mazzlabs.sentinel.tools.framework.ToolResponse

/**
 * IntentClassifierNode - Determines user intent from query
 *
 * Uses the gateway LLM to classify intent into predefined categories.
 */
class IntentClassifierNode(
    private val toolExecutor: ToolExecutor
) : AgentNode {

    companion object {
        private const val TAG = "IntentClassifierNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        Log.d(TAG, "Classifying intent for: ${state.userQuery}")

        val prompt = buildClassificationPrompt(state)
        val inferenceRouter = SentinelApplication.getInstance().inferenceRouter

        return try {
            if (inferenceRouter == null || !inferenceRouter.isAvailable()) {
                return state.copy(
                    intent = AgentIntent.UNKNOWN,
                    error = "Gateway not connected"
                )
            }

            val result = inferenceRouter.infer(
                prompt = prompt,
                options = InferenceOptions(temperature = 0.7f, maxTokens = 512)
            )

            if (!result.success) {
                return state.copy(
                    intent = AgentIntent.UNKNOWN,
                    error = "Inference failed: ${result.error}"
                )
            }

            Log.d(TAG, "Classification response: ${result.text}")

            val (intent, entities) = parseClassificationResponse(result.text)

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
        val toolsDesc = toolExecutor.getToolSchema(compact = true)

        return """
Classify the user's intent and extract relevant entities.

User query: "${state.userQuery}"

$toolsDesc

Respond with JSON only:
{
    "intent": "one of: READ_CALENDAR, CREATE_EVENT, UPDATE_EVENT, DELETE_EVENT, CREATE_ALARM, LIST_ALARMS, DELETE_ALARM, CALL_CONTACT, SEND_SMS, SEARCH, CLICK_ELEMENT, SCROLL_SCREEN, TYPE_TEXT, GO_BACK, GO_HOME, ANSWER_QUESTION, UNKNOWN",
    "entities": {
        "relevant_key": "extracted_value"
    },
    "selected_tool": "module.operation or null if UI action",
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
                ?.entries
                ?.take(20)
                ?.associate { it.key to it.value.toString() }
                ?: emptyMap()

            intent to entities
        } catch (e: Exception) {
            val truncatedResponse = response.take(500)
            Log.e(TAG, "Failed to parse classification response: '$truncatedResponse'${if (response.length > 500) "... (truncated)" else ""}", e)
            AgentIntent.UNKNOWN to emptyMap()
        }
    }
}

/**
 * ToolSelectorNode - Selects appropriate tool module operation based on intent
 */
class ToolSelectorNode : AgentNode {

    companion object {
        private const val TAG = "ToolSelectorNode"

        private val INTENT_TO_TOOL = mapOf(
            AgentIntent.READ_CALENDAR to "calendar.read_events",
            AgentIntent.CREATE_EVENT to "calendar.create_event",
            AgentIntent.UPDATE_EVENT to "calendar.update_event",
            AgentIntent.DELETE_EVENT to "calendar.delete_event",
            AgentIntent.CREATE_ALARM to "clock.create_alarm",
            AgentIntent.LIST_ALARMS to "clock.show_alarms",
            AgentIntent.DELETE_ALARM to "clock.dismiss_alarm",
            AgentIntent.CALL_CONTACT to "contacts.call_contact",
            AgentIntent.SEND_SMS to "messaging.send_sms"
        )
    }

    override suspend fun process(state: AgentState): AgentState {
        val intent = state.intent ?: return state.copy(error = "No intent classified")

        val toolCall = INTENT_TO_TOOL[intent]

        return if (toolCall != null) {
            Log.d(TAG, "Selected tool: $toolCall for intent: $intent")
            state.copy(
                selectedTool = toolCall,
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
    private val toolExecutor: ToolExecutor
) : AgentNode {

    companion object {
        private const val TAG = "ParameterExtractorNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        val toolCall = state.selectedTool
            ?: return state.copy(error = "No tool selected")

        // If entities already contain enough info, use them
        if (state.extractedEntities.isNotEmpty()) {
            Log.d(TAG, "Using pre-extracted entities: ${state.extractedEntities}")
            return state.copy(
                toolInput = state.extractedEntities,
                currentNode = "param_extractor"
            )
        }

        // Otherwise, use LLM to extract parameters
        val schema = toolExecutor.getOperationSchema(toolCall) ?: "No schema available"
        val prompt = buildExtractionPrompt(state, toolCall, schema)
        val inferenceRouter = SentinelApplication.getInstance().inferenceRouter

        return try {
            if (inferenceRouter == null || !inferenceRouter.isAvailable()) {
                return state.copy(error = "Gateway not connected")
            }

            val result = inferenceRouter.infer(
                prompt = prompt,
                options = InferenceOptions(temperature = 0.7f, maxTokens = 512)
            )

            if (!result.success) {
                return state.copy(error = "Inference failed: ${result.error}")
            }

            val params = parseParameters(result.text)

            state.copy(
                toolInput = params,
                currentNode = "param_extractor"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parameter extraction failed", e)
            state.copy(error = "Failed to extract parameters: ${e.message}")
        }
    }

    private fun buildExtractionPrompt(state: AgentState, toolCall: String, schema: String): String {
        return """
Extract parameters for $toolCall from the user's request.

User query: "${state.userQuery}"

Screen context (may include element_id list):
${state.screenContext.take(4000)}

Tool schema:
$schema

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
            val truncatedResponse = response.take(500)
            Log.e(TAG, "Failed to parse parameters response: '$truncatedResponse'${if (response.length > 500) "... (truncated)" else ""}", e)
            emptyMap()
        }
    }
}

/**
 * ToolExecutorNode - Executes the selected tool via ToolExecutor
 */
class ToolExecutorNode(
    private val toolExecutor: ToolExecutor
) : AgentNode {

    companion object {
        private const val TAG = "ToolExecutorNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        val toolCall = state.selectedTool
            ?: return state.copy(error = "No tool selected")

        Log.d(TAG, "Executing tool: $toolCall with params: ${state.toolInput}")

        val result = toolExecutor.execute(toolCall, state.toolInput)

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
            is ToolResponse.Success -> {
                "${lastResult.message}\n${formatData(lastResult.data)}"
            }
            is ToolResponse.Error -> {
                "Sorry, I couldn't complete that: ${lastResult.message}"
            }
            is ToolResponse.PermissionRequired -> {
                "I need the following permissions: ${lastResult.permissions.joinToString()}"
            }
            is ToolResponse.Confirmation -> {
                lastResult.message
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
                val scrollDirection = when (direction.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> ScrollDirection.DOWN
                }
                AgentAction(ActionType.SCROLL, direction = scrollDirection, reasoning = "User requested scroll")
            }
            AgentIntent.CLICK_ELEMENT -> {
                val target = state.extractedEntities["target"] ?: state.extractedEntities["element"]
                val elementId = state.extractedEntities["element_id"]?.toIntOrNull()
                AgentAction(
                    ActionType.CLICK,
                    elementId = elementId,
                    target = target,
                    reasoning = "User requested click"
                )
            }
            AgentIntent.TYPE_TEXT -> {
                val text = state.extractedEntities["text"] ?: ""
                val target = state.extractedEntities["field"]
                val elementId = state.extractedEntities["element_id"]?.toIntOrNull()
                AgentAction(
                    ActionType.TYPE,
                    elementId = elementId,
                    target = target,
                    text = text,
                    reasoning = "User requested text input"
                )
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
