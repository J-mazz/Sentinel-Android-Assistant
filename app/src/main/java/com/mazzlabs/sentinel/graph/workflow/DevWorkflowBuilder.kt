package com.mazzlabs.sentinel.graph.workflow

import android.util.Log
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.graph.AgentGraph
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.nodes.dev.ArchitectNode
import com.mazzlabs.sentinel.graph.nodes.dev.EngineerNode
import com.mazzlabs.sentinel.graph.nodes.dev.FixerNode
import com.mazzlabs.sentinel.graph.state.*

/**
 * DevWorkflowBuilder - Builds the self-correction dev workflow graph
 *
 * Port of colabPro/src/graph/workflow.ts:272-385
 * Graph: architect_plan → engineer_implement → (test/fix loop) → architect_review → finalize
 */
object DevWorkflowBuilder {

    private const val TAG = "DevWorkflowBuilder"

    // Node names matching colabPro's NODES constant
    const val ARCHITECT_PLAN = "architect_plan"
    const val ARCHITECT_REVIEW = "architect_review"
    const val ENGINEER_IMPLEMENT = "engineer_implement"
    const val ENGINEER_FIX = "engineer_fix"
    const val FIXER = "fixer"
    const val FINALIZE = "finalize"

    fun build(client: OpenClawGatewayClient): AgentGraph {
        return AgentGraph.Builder()
            // --- Nodes ---
            .addNode(ARCHITECT_PLAN, ArchitectNode(client, ArchitectNode.ArchitectMode.PLANNING))
            .addNode(ARCHITECT_REVIEW, ArchitectNode(client, ArchitectNode.ArchitectMode.REVIEWING))
            .addNode(ENGINEER_IMPLEMENT, EngineerNode(client, EngineerNode.EngineerMode.IMPLEMENT))
            .addNode(ENGINEER_FIX, EngineerNode(client, EngineerNode.EngineerMode.FIX))
            .addNode(FIXER, FixerNode(client))
            .addNode(FINALIZE, FinalizeNode())

            // --- Entry point ---
            .setEntryPoint(ARCHITECT_PLAN)

            // --- Edges ---

            // After Planning → route based on plan result
            .addConditionalEdge(ARCHITECT_PLAN) { state ->
                val plan = state.devProject?.plan ?: emptyList()
                if (plan.isEmpty()) {
                    Log.i(TAG, "[ROUTE] No plan generated, ending...")
                    AgentGraph.END
                } else {
                    Log.i(TAG, "[ROUTE] Plan created (${plan.size} steps), proceeding to implementation...")
                    ENGINEER_IMPLEMENT
                }
            }

            // After Implementation → route based on results
            .addConditionalEdge(ENGINEER_IMPLEMENT) { state ->
                routeAfterImplementation(state)
            }

            // After Engineer Fix → route based on fix result
            .addConditionalEdge(ENGINEER_FIX) { state ->
                routeAfterFix(state)
            }

            // After Fixer → route based on debugging result
            .addConditionalEdge(FIXER) { state ->
                routeAfterFixer(state)
            }

            // After Review → route based on approval
            .addConditionalEdge(ARCHITECT_REVIEW) { state ->
                routeAfterReview(state)
            }

            // Finalize → END
            .addEdge(FINALIZE, AgentGraph.END)

            .build()
    }

    private fun routeAfterImplementation(state: AgentState): String {
        val dev = state.devProject ?: return AgentGraph.END

        if (dev.iterationCount >= dev.maxIterations) {
            Log.i(TAG, "[ROUTE] Max iterations reached, finalizing...")
            return FINALIZE
        }

        if (dev.testResults?.passed == true) {
            val remainingSteps = dev.plan.filter {
                it.status == PlanStepStatus.PENDING && it.assignee == DevRole.CODER
            }
            return if (remainingSteps.isEmpty()) {
                Log.i(TAG, "[ROUTE] All steps complete, proceeding to review...")
                ARCHITECT_REVIEW
            } else {
                Log.i(TAG, "[ROUTE] Step successful, continuing implementation...")
                ENGINEER_IMPLEMENT
            }
        }

        return if (dev.shouldEscalate()) {
            Log.i(TAG, "[ROUTE] Escalating to fixer...")
            FIXER
        } else {
            Log.i(TAG, "[ROUTE] Tests failed, attempting fix...")
            ENGINEER_FIX
        }
    }

    private fun routeAfterFix(state: AgentState): String {
        val dev = state.devProject ?: return AgentGraph.END

        if (dev.iterationCount >= dev.maxIterations) return FINALIZE

        if (dev.testResults?.passed == true) {
            val remainingSteps = dev.plan.filter {
                it.status == PlanStepStatus.PENDING && it.assignee == DevRole.CODER
            }
            return if (remainingSteps.isNotEmpty()) ENGINEER_IMPLEMENT else ARCHITECT_REVIEW
        }

        return if (dev.shouldEscalate()) FIXER else ENGINEER_FIX
    }

    private fun routeAfterFixer(state: AgentState): String {
        val dev = state.devProject ?: return AgentGraph.END

        if (dev.iterationCount >= dev.maxIterations) return FINALIZE
        if (dev.testResults?.passed == true) return ARCHITECT_REVIEW

        return if (dev.consecutiveFailures >= 3) {
            Log.i(TAG, "[ROUTE] Multiple fix failures, asking architect to revise plan...")
            ARCHITECT_PLAN
        } else {
            FIXER
        }
    }

    private fun routeAfterReview(state: AgentState): String {
        val dev = state.devProject ?: return AgentGraph.END

        if (dev.approvalStatus == ApprovalStatus.APPROVED) {
            Log.i(TAG, "[ROUTE] Code approved, finalizing...")
            return FINALIZE
        }

        if (dev.iterationCount >= dev.maxIterations) return FINALIZE

        Log.i(TAG, "[ROUTE] Review issues found, sending to fix...")
        return ENGINEER_FIX
    }
}

/**
 * FinalizeNode - Marks project as complete or failed
 */
private class FinalizeNode : com.mazzlabs.sentinel.graph.AgentNode {
    override suspend fun process(state: AgentState): AgentState {
        val dev = state.devProject ?: return state.copy(isComplete = true)

        val allStepsComplete = dev.plan.all { it.status == PlanStepStatus.COMPLETED }
        val approved = dev.approvalStatus == ApprovalStatus.APPROVED

        val finalStatus = if (allStepsComplete && approved) DevProjectStatus.FINISHED else DevProjectStatus.FAILED
        val summary = if (finalStatus == DevProjectStatus.FINISHED) {
            "Project completed successfully in ${dev.iterationCount} iterations"
        } else {
            val completed = dev.plan.count { it.status == PlanStepStatus.COMPLETED }
            "Project ended - Steps complete: $completed/${dev.plan.size}"
        }

        return state.copy(
            devProject = dev.copy(
                status = finalStatus,
                lastSummary = summary,
                lastUpdateTimeMs = System.currentTimeMillis()
            ),
            response = summary,
            isComplete = true
        )
    }
}
