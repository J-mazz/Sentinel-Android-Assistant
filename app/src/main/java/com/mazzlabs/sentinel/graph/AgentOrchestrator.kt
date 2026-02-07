package com.mazzlabs.sentinel.graph

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.graph.nodes.*
import com.mazzlabs.sentinel.tools.framework.ToolExecutor
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import com.mazzlabs.sentinel.tools.framework.Tools

/**
 * AgentOrchestrator - Main entry point for DAG-based agent execution
 *
 * Builds and manages the execution graph with tool capabilities.
 * Inspired by LangGraph's declarative graph definition pattern.
 */
class AgentOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "AgentOrchestrator"
    }

    private val toolExecutor = Tools.getInstance(context)
    private lateinit var graph: AgentGraph

    init {
        buildGraph()
    }

    /**
     * Build the execution DAG
     *
     * Graph structure:
     *
     *   START
     *     |
     *     v
     *   [intent_classifier]
     *     |
     *     v
     *   [router] ------------------+
     *     |                        |
     *     | (tool intent)          | (ui intent)
     *     v                        v
     *   [tool_selector]       [ui_action]
     *     |                        |
     *     v                        |
     *   [param_extractor]          |
     *     |                        |
     *     v                        |
     *   [tool_executor]            |
     *     |                        |
     *     v                        |
     *   [response_generator]       |
     *     |                        |
     *     +------------+-----------+
     *                  v
     *                 END
     */
    private fun buildGraph() {
        Log.i(TAG, "Building execution graph...")

        graph = AgentGraph.Builder()
            // Add nodes
            .addNode("intent_classifier", IntentClassifierNode(toolExecutor))
            .addNode("tool_selector", ToolSelectorNode())
            .addNode("param_extractor", ParameterExtractorNode(toolExecutor))
            .addNode("tool_executor", ToolExecutorNode(toolExecutor))
            .addNode("response_generator", ResponseGeneratorNode())
            .addNode("ui_action", UIActionNode())

            // Entry point
            .setEntryPoint("intent_classifier")

            // Edges
            .addConditionalEdge("intent_classifier") { state ->
                // Route based on whether intent requires a tool or UI action
                when (state.intent) {
                    AgentIntent.READ_CALENDAR,
                    AgentIntent.CREATE_EVENT,
                    AgentIntent.UPDATE_EVENT,
                    AgentIntent.DELETE_EVENT,
                    AgentIntent.CREATE_ALARM,
                    AgentIntent.LIST_ALARMS,
                    AgentIntent.DELETE_ALARM,
                    AgentIntent.CALL_CONTACT,
                    AgentIntent.SEND_SMS -> "tool_selector"

                    AgentIntent.CLICK_ELEMENT,
                    AgentIntent.SCROLL_SCREEN,
                    AgentIntent.TYPE_TEXT,
                    AgentIntent.GO_BACK,
                    AgentIntent.GO_HOME,
                    AgentIntent.SEARCH_SELECTED,
                    AgentIntent.TRANSLATE_SELECTED,
                    AgentIntent.COPY_SELECTED,
                    AgentIntent.SAVE_SELECTED,
                    AgentIntent.SHARE_SELECTED,
                    AgentIntent.EXTRACT_DATA_FROM_SELECTION -> "ui_action"

                    AgentIntent.SEARCH,
                    AgentIntent.ANSWER_QUESTION,
                    AgentIntent.UNKNOWN,
                    null -> "ui_action"  // Default to UI for unknown
                }
            }

            // Tool path
            .addEdge("tool_selector", "param_extractor")
            .addEdge("param_extractor", "tool_executor")
            .addConditionalEdge("tool_executor") { state ->
                // Check if tool execution succeeded
                val lastResult = state.toolResults.lastOrNull()
                when (lastResult) {
                    null -> AgentGraph.END
                    is ToolResponse.Success -> "response_generator"
                    else -> "response_generator"  // Still generate response for errors
                }
            }
            .addEdge("response_generator", AgentGraph.END)

            // UI action path
            .addEdge("ui_action", AgentGraph.END)

            .build()

        Log.i(TAG, "Graph built successfully")
    }

    /**
     * Process a user query through the graph
     */
    suspend fun process(userQuery: String, screenContext: String = ""): AgentState {
        Log.i(TAG, "Processing query: $userQuery")

        val initialState = AgentState(
            userQuery = userQuery,
            screenContext = screenContext
        )

        return graph.invoke(initialState)
    }

    /**
     * Get available tool modules for display
     */
    fun getAvailableModules(): List<String> = toolExecutor.getAvailableModules()

    /**
     * Generate tools description for prompt augmentation
     */
    fun getToolsPrompt(): String = toolExecutor.getToolSchema()
}
