package com.mazzlabs.sentinel.graph.workflow

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.graph.AgentGraph
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.state.DevProjectState
import com.mazzlabs.sentinel.tools.framework.ToolExecutor

/**
 * WorkflowFactory - Selects graph based on detected intent
 *
 * Analyzes the user query and current state to determine whether to use
 * the standard assistant workflow or the dev project workflow.
 */
class WorkflowFactory(
    private val context: Context,
    private val toolExecutor: ToolExecutor,
    private val gatewayClient: OpenClawGatewayClient?
) {
    companion object {
        private const val TAG = "WorkflowFactory"

        /** Keywords that trigger the dev workflow */
        private val DEV_TRIGGER_PATTERNS = listOf(
            Regex("\\bdev\\s+project\\b", RegexOption.IGNORE_CASE),
            Regex("\\bbuild\\s+(a|an|the)\\s+", RegexOption.IGNORE_CASE),
            Regex("\\bcreate\\s+(a|an)\\s+.*(api|app|service|server|library|package)\\b", RegexOption.IGNORE_CASE),
            Regex("\\bstart\\s+(a\\s+)?dev\\b", RegexOption.IGNORE_CASE),
            Regex("\\bimplement\\s+", RegexOption.IGNORE_CASE),
            Regex("\\bcode\\s+(a|an|the)\\s+", RegexOption.IGNORE_CASE),
            Regex("\\brefactor\\s+", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Detect which workflow type to use based on the user query
     */
    fun detectWorkflowType(userQuery: String, currentState: AgentState? = null): WorkflowType {
        // If already in a dev workflow, continue it
        if (currentState?.workflowType == WorkflowType.DEV_PROJECT) {
            return WorkflowType.DEV_PROJECT
        }

        // Check if gateway is available for dev workflow
        if (gatewayClient == null || !gatewayClient.isConnected()) {
            return WorkflowType.ASSISTANT
        }

        // Check for dev workflow triggers
        if (DEV_TRIGGER_PATTERNS.any { it.containsMatchIn(userQuery) }) {
            Log.i(TAG, "Dev workflow detected for query: $userQuery")
            return WorkflowType.DEV_PROJECT
        }

        return WorkflowType.ASSISTANT
    }

    /**
     * Build the appropriate graph for the workflow type
     */
    fun buildGraph(workflowType: WorkflowType): AgentGraph {
        return when (workflowType) {
            WorkflowType.ASSISTANT -> {
                AssistantWorkflowBuilder.build(toolExecutor, context)
            }
            WorkflowType.DEV_PROJECT -> {
                val client = gatewayClient
                    ?: throw IllegalStateException("Gateway client required for dev workflow")
                DevWorkflowBuilder.build(client)
            }
        }
    }

    /**
     * Prepare initial state for a dev workflow
     */
    fun prepareDevState(userQuery: String, state: AgentState): AgentState {
        // Extract objective from the query
        val objective = extractObjective(userQuery)

        return state.copy(
            workflowType = WorkflowType.DEV_PROJECT,
            devProject = DevProjectState.create(objective = objective),
            remoteAgentRole = RemoteAgentRole.ARCHITECT
        )
    }

    private fun extractObjective(query: String): String {
        // Remove common trigger phrases to get the core objective
        var objective = query
        for (pattern in DEV_TRIGGER_PATTERNS) {
            objective = pattern.replace(objective, "").trim()
        }
        // If we stripped too much, use the original
        return objective.ifBlank { query }
    }
}
