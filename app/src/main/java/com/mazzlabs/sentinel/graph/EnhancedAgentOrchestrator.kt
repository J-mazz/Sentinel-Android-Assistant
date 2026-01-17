package com.mazzlabs.sentinel.graph

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.graph.nodes.*
import com.mazzlabs.sentinel.tools.*

/**
 * EnhancedAgentOrchestrator - stateful, session-based agent execution.
 */
class EnhancedAgentOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "EnhancedAgentOrchestrator"

        private val MULTI_STEP_INTENTS = setOf(
            AgentIntent.CREATE_EVENT,
            AgentIntent.SEND_SMS,
            AgentIntent.CALL_CONTACT
        )

        private val TOOL_INTENTS = setOf(
            AgentIntent.READ_CALENDAR,
            AgentIntent.CREATE_EVENT,
            AgentIntent.UPDATE_EVENT,
            AgentIntent.DELETE_EVENT,
            AgentIntent.CREATE_ALARM,
            AgentIntent.LIST_ALARMS,
            AgentIntent.DELETE_ALARM,
            AgentIntent.CALL_CONTACT,
            AgentIntent.SEND_SMS
        )

        private val UI_INTENTS = setOf(
            AgentIntent.CLICK_ELEMENT,
            AgentIntent.SCROLL_SCREEN,
            AgentIntent.TYPE_TEXT,
            AgentIntent.GO_BACK,
            AgentIntent.GO_HOME
        )
    }

    private val sessionManager = SessionManager(context)
    private val toolRegistry = ToolRegistry()
    private lateinit var graph: AgentGraph

    init {
        registerTools()
        buildEnhancedGraph()
    }

    private fun registerTools() {
        Log.i(TAG, "Registering tools...")
        toolRegistry.register(CalendarReadTool())
        toolRegistry.register(CalendarWriteTool())
        toolRegistry.register(AlarmCreateTool())
        toolRegistry.register(TimerCreateTool())
        toolRegistry.register(PhoneCallTool())
        toolRegistry.register(ContactLookupTool())
        toolRegistry.register(SmsSendTool())
        Log.i(TAG, "Registered ${toolRegistry.getAll().size} tools")
    }

    private fun buildEnhancedGraph() {
        Log.i(TAG, "Building enhanced graph...")

        graph = AgentGraph.Builder()
            // Intent understanding pipeline
            .addNode("intent_parser", IntentParserNode())
            .addNode("entity_extractor", EntityExtractorNode())
            .addNode("context_analyzer", ContextAnalyzerNode())

            // Planning nodes
            .addNode("plan_generator", PlanGeneratorNode())
            .addNode("plan_executor", PlanExecutorNode())

            // Execution nodes
            .addNode("tool_selector", ToolSelectorNode(toolRegistry))
            .addNode("param_extractor", ParameterExtractorNode(toolRegistry))
            .addNode("tool_executor", ToolExecutorNode(toolRegistry, context))
            .addNode("ui_action", UIActionNode())

            // Response generation
            .addNode("response_generator", EnhancedResponseGeneratorNode())
            .addNode("clarification_handler", ClarificationNode())
            .addNode("selection_processor", SelectionProcessorNode(context))

            // Entry point
            .setEntryPoint("intent_parser")

            // Routing logic
            .addConditionalEdge("intent_parser") { state ->
                when {
                    state.confidence < 0.6f -> "clarification_handler"
                    state.intent in MULTI_STEP_INTENTS -> "plan_generator"
                    else -> "entity_extractor"
                }
            }

            .addEdge("clarification_handler", AgentGraph.END)

            .addConditionalEdge("plan_generator") { state ->
                if (state.plan != null) "plan_executor" else "entity_extractor"
            }

            .addConditionalEdge("plan_executor") { state ->
                val plan = state.plan ?: return@addConditionalEdge AgentGraph.END
                if (plan.currentStepIndex < plan.steps.size) {
                    "entity_extractor"
                } else {
                    "response_generator"
                }
            }

            .addEdge("entity_extractor", "context_analyzer")

            .addConditionalEdge("context_analyzer") { state ->
                when (state.intent) {
                    AgentIntent.SEARCH_SELECTED,
                    AgentIntent.TRANSLATE_SELECTED,
                    AgentIntent.COPY_SELECTED,
                    AgentIntent.SAVE_SELECTED,
                    AgentIntent.SHARE_SELECTED,
                    AgentIntent.EXTRACT_DATA_FROM_SELECTION -> "selection_processor"
                    in TOOL_INTENTS -> "tool_selector"
                    in UI_INTENTS -> "ui_action"
                    else -> "response_generator"
                }
            }

            .addEdge("tool_selector", "param_extractor")
            .addEdge("param_extractor", "tool_executor")
            .addConditionalEdge("tool_executor") { state ->
                if (state.error != null) "response_generator" else "response_generator"
            }
            .addEdge("ui_action", "response_generator")
            .addEdge("selection_processor", "response_generator")
            .addEdge("response_generator", AgentGraph.END)

            .build()
    }

    suspend fun process(
        userQuery: String,
        sessionId: String = "default",
        screenContext: String = ""
    ): AgentState {
        val currentSession = sessionManager.getOrCreateSession(sessionId)

        val updatedHistory = currentSession.conversationHistory + Message(
            role = Role.USER,
            content = userQuery
        )

        val initialState = currentSession.copy(
            userQuery = userQuery,
            conversationHistory = updatedHistory,
            screenContext = screenContext,
            isComplete = false,
            error = null,
            iteration = 0
        )

        val finalState = graph.invoke(initialState)

        val completeState = finalState.copy(
            conversationHistory = finalState.conversationHistory + Message(
                role = Role.ASSISTANT,
                content = finalState.response
            )
        )

        sessionManager.updateSession(completeState)
        return completeState
    }
}
