package com.mazzlabs.sentinel.graph

import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.model.UIElement
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import com.mazzlabs.sentinel.graph.state.DevProjectState
import com.mazzlabs.sentinel.graph.workflow.WorkflowType
import com.mazzlabs.sentinel.graph.workflow.RemoteAgentRole

/**
 * AgentState - Immutable state object passed through the execution graph
 * 
 * Inspired by LangGraph's state management pattern.
 * Each node receives state, processes it, and returns updated state.
 */
data class AgentState(
    // Session
    val sessionId: String = "default",
    // Input
    val userQuery: String = "",
    val screenContext: String = "",
    val conversationHistory: List<Message> = emptyList(),
    val plan: Plan? = null,
    val planStep: Int = 0,
    
    // Processing
    val currentNode: String = "START",
    val intent: AgentIntent? = null,
    val extractedEntities: Map<String, String> = emptyMap(),
    val confidence: Float = 0f,
    val focusedElement: UIElement? = null,
    
    // Tool execution
    val selectedTool: String? = null,
    val toolInput: Map<String, Any?> = emptyMap(),
    val toolResults: List<ToolResponse> = emptyList(),
    
    // Output
    val response: String = "",
    val action: AgentAction? = null,
    val needsUserInput: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    
    // Dev workflow state (Phase 3)
    val devProject: DevProjectState? = null,
    val workflowType: WorkflowType = WorkflowType.ASSISTANT,
    val remoteAgentRole: RemoteAgentRole? = null,

    // Metadata
    val history: List<String> = emptyList(),
    val iteration: Int = 0,
    val maxIterations: Int = 10
) {
    /**
     * Create new state with updates (immutable pattern).
     * Automatically increments iteration and appends currentNode to history.
     * Use data class copy() for simple field updates without side effects.
     */
    fun update(
        vararg updates: Pair<String, Any?>
    ): AgentState {
        var newState = this
        for ((key, value) in updates) {
            newState = when (key) {
                "sessionId" -> newState.copy(sessionId = value as String)
                "userQuery" -> newState.copy(userQuery = value as String)
                "screenContext" -> newState.copy(screenContext = value as String)
                "conversationHistory" -> newState.copy(conversationHistory = value as List<Message>)
                "plan" -> newState.copy(plan = value as? Plan)
                "planStep" -> newState.copy(planStep = value as Int)
                "currentNode" -> newState.copy(currentNode = value as String)
                "intent" -> newState.copy(intent = value as? AgentIntent)
                "extractedEntities" -> newState.copy(extractedEntities = value as Map<String, String>)
                "confidence" -> newState.copy(confidence = value as Float)
                "focusedElement" -> newState.copy(focusedElement = value as? UIElement)
                "selectedTool" -> newState.copy(selectedTool = value as? String)
                "toolInput" -> newState.copy(toolInput = value as Map<String, Any?>)
                "toolResults" -> newState.copy(toolResults = value as List<ToolResponse>)
                "response" -> newState.copy(response = value as String)
                "action" -> newState.copy(action = value as? AgentAction)
                "needsUserInput" -> newState.copy(needsUserInput = value as Boolean)
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

data class Message(
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role { USER, ASSISTANT, SYSTEM }

data class Plan(
    val goal: String,
    val steps: List<PlanStep>,
    val currentStepIndex: Int = 0
)

data class PlanStep(
    val description: String,
    val intent: AgentIntent,
    val requiredEntities: List<String>,
    val toolCall: String? = null
)

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
    UNKNOWN,

    // Selection-based intents
    SEARCH_SELECTED,
    TRANSLATE_SELECTED,
    COPY_SELECTED,
    SAVE_SELECTED,
    SHARE_SELECTED,
    EXTRACT_DATA_FROM_SELECTION,

    // Remote/dev workflow intents (Phase 3/5)
    SEND_SELECTION_TO_REMOTE,
    ANALYZE_IMAGE_REGION,
    START_DEV_PROJECT,
    CONTINUE_DEV_PROJECT
}
