package com.mazzlabs.sentinel.graph.workflow

import com.mazzlabs.sentinel.graph.AgentGraph
import com.mazzlabs.sentinel.graph.AgentIntent
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.nodes.*
import com.mazzlabs.sentinel.tools.framework.ToolExecutor

/**
 * AssistantWorkflowBuilder - Refactored from EnhancedAgentOrchestrator
 *
 * Builds the standard assistant graph for general-purpose mobile interaction.
 * This is the existing behavior, just restructured as a builder pattern.
 */
object AssistantWorkflowBuilder {

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

    fun build(toolExecutor: ToolExecutor, context: android.content.Context): AgentGraph {
        return AgentGraph.Builder()
            // Intent understanding pipeline
            .addNode("intent_parser", IntentParserNode())
            .addNode("entity_extractor", EntityExtractorNode())
            .addNode("context_analyzer", ContextAnalyzerNode())

            // Planning nodes
            .addNode("plan_generator", PlanGeneratorNode())
            .addNode("plan_executor", PlanExecutorNode())

            // Execution nodes
            .addNode("tool_selector", ToolSelectorNode())
            .addNode("param_extractor", ParameterExtractorNode(toolExecutor))
            .addNode("tool_executor", ToolExecutorNode(toolExecutor))
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
                    AgentIntent.SEND_SELECTION_TO_REMOTE,
                    AgentIntent.ANALYZE_IMAGE_REGION -> "selection_processor"
                    in TOOL_INTENTS -> "tool_selector"
                    in UI_INTENTS -> "ui_action"
                    else -> "response_generator"
                }
            }

            .addEdge("tool_selector", "param_extractor")
            .addEdge("param_extractor", "tool_executor")
            .addConditionalEdge("tool_executor") { "response_generator" }
            .addEdge("ui_action", "response_generator")
            .addEdge("selection_processor", "response_generator")
            .addEdge("response_generator", AgentGraph.END)

            .build()
    }
}
