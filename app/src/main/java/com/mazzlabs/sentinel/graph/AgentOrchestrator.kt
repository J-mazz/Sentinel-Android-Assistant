package com.mazzlabs.sentinel.graph

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.graph.nodes.*
import com.mazzlabs.sentinel.tools.*

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

    private val toolRegistry = ToolRegistry()
    private lateinit var graph: AgentGraph

    init {
        registerTools()
        buildGraph()
    }

    /**
     * Register all available tools
     */
    private fun registerTools() {
        Log.i(TAG, "Registering tools...")
        
        // Calendar tools
        toolRegistry.register(CalendarReadTool())
        toolRegistry.register(CalendarWriteTool())
        
        // Alarm tools
        toolRegistry.register(AlarmCreateTool())
        toolRegistry.register(TimerCreateTool())
        
        // Phone tools
        toolRegistry.register(PhoneCallTool())
        toolRegistry.register(ContactLookupTool())
        toolRegistry.register(SmsSendTool())
        
        Log.i(TAG, "Registered ${toolRegistry.getAll().size} tools")
    }

    /**
     * Build the execution DAG
     * 
     * Graph structure:
     * 
     *   START
     *     │
     *     ▼
     *   [intent_classifier]
     *     │
     *     ▼
     *   [router] ──────────────────┐
     *     │                        │
     *     │ (tool intent)          │ (ui intent)
     *     ▼                        ▼
     *   [tool_selector]       [ui_action]
     *     │                        │
     *     ▼                        │
     *   [param_extractor]          │
     *     │                        │
     *     ▼                        │
     *   [tool_executor]            │
     *     │                        │
     *     ▼                        │
     *   [response_generator]       │
     *     │                        │
     *     └────────────┬───────────┘
     *                  ▼
     *                 END
     */
    private fun buildGraph() {
        Log.i(TAG, "Building execution graph...")
        
        graph = AgentGraph.Builder()
            // Add nodes
            .addNode("intent_classifier", IntentClassifierNode(toolRegistry))
            .addNode("tool_selector", ToolSelectorNode(toolRegistry))
            .addNode("param_extractor", ParameterExtractorNode(toolRegistry))
            .addNode("tool_executor", ToolExecutorNode(toolRegistry, context))
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
                when {
                    lastResult == null -> AgentGraph.END
                    lastResult.isSuccess() -> "response_generator"
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
     * Get available tools for display
     */
    fun getAvailableTools(): List<Tool> = toolRegistry.getAll()

    /**
     * Check if a specific tool is available and has permissions
     */
    fun isToolAvailable(toolName: String): Boolean {
        return toolRegistry.get(toolName) != null && 
               toolRegistry.hasPermissions(context, toolName)
    }

    /**
     * Generate tools description for prompt augmentation
     */
    fun getToolsPrompt(): String = toolRegistry.generateToolsPrompt()
}
