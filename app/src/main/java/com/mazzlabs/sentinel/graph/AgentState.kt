package com.mazzlabs.sentinel.graph

import com.mazzlabs.sentinel.tools.ToolResult

/**
 * AgentState - Immutable state object passed through the execution graph
 * 
 * Inspired by LangGraph's state management pattern.
 * Each node receives state, processes it, and returns updated state.
 */
data class AgentState(
    // Input
    val userQuery: String = "",
    val screenContext: String = "",
    
    // Processing
    val currentNode: String = "START",
    val intent: AgentIntent? = null,
    val extractedEntities: Map<String, String> = emptyMap(),
    
    // Tool execution
    val selectedTool: String? = null,
    val toolInput: Map<String, Any?> = emptyMap(),
    val toolResults: List<ToolResult> = emptyList(),
    
    // Output
    val response: String = "",
    val action: AgentAction? = null,
    val isComplete: Boolean = false,
    val error: String? = null,
    
    // Metadata
    val history: List<String> = emptyList(),
    val iteration: Int = 0,
    val maxIterations: Int = 5
) {
    /**
     * Create new state with updates (immutable pattern)
     */
    fun copy(
        vararg updates: Pair<String, Any?>
    ): AgentState {
        var newState = this
        for ((key, value) in updates) {
            newState = when (key) {
                "userQuery" -> newState.copy(userQuery = value as String)
                "screenContext" -> newState.copy(screenContext = value as String)
                "currentNode" -> newState.copy(currentNode = value as String)
                "intent" -> newState.copy(intent = value as? AgentIntent)
                "extractedEntities" -> newState.copy(extractedEntities = value as Map<String, String>)
                "selectedTool" -> newState.copy(selectedTool = value as? String)
                "toolInput" -> newState.copy(toolInput = value as Map<String, Any?>)
                "toolResults" -> newState.copy(toolResults = value as List<ToolResult>)
                "response" -> newState.copy(response = value as String)
                "action" -> newState.copy(action = value as? AgentAction)
                "isComplete" -> newState.copy(isComplete = value as Boolean)
                "error" -> newState.copy(error = value as? String)
                else -> newState
            }
        }
        return newState.copy(
            history = history + currentNode,
            iteration = iteration + 1
        )
    }
    
    fun hasError(): Boolean = error != null
    fun shouldContinue(): Boolean = !isComplete && iteration < maxIterations && !hasError()
}

/**
 * Agent intents - what the user wants to accomplish
 */
enum class AgentIntent {
    // Calendar
    READ_CALENDAR,
    CREATE_EVENT,
    UPDATE_EVENT,
    DELETE_EVENT,
    
    // Alarms
    CREATE_ALARM,
    LIST_ALARMS,
    DELETE_ALARM,
    
    // Phone
    CALL_CONTACT,
    SEND_SMS,
    
    // UI Navigation
    CLICK_ELEMENT,
    SCROLL_SCREEN,
    TYPE_TEXT,
    GO_BACK,
    GO_HOME,
    
    // General
    SEARCH,
    ANSWER_QUESTION,
    UNKNOWN
}

/**
 * Agent action output for UI automation
 */
data class AgentAction(
    val type: ActionType,
    val target: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val reasoning: String? = null
)

enum class ActionType {
    CLICK, SCROLL, TYPE, HOME, BACK, WAIT, NONE, TOOL_CALL
}
