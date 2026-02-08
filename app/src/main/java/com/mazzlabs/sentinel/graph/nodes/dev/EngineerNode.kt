package com.mazzlabs.sentinel.graph.nodes.dev

import android.util.Log
import com.mazzlabs.sentinel.gateway.GatewayConfig
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.gateway.SessionPatchParams
import com.mazzlabs.sentinel.graph.AgentNode
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.state.*

/**
 * EngineerNode - Port of colabPro/src/nodes/engineer-node.ts
 *
 * Uses OpenClaw gateway to run the coder agent for implementation and fixing.
 */
class EngineerNode(
    private val client: OpenClawGatewayClient,
    private val mode: EngineerMode
) : AgentNode {

    companion object {
        private const val TAG = "EngineerNode"
    }

    enum class EngineerMode { IMPLEMENT, FIX }

    override suspend fun process(state: AgentState): AgentState {
        val devState = state.devProject ?: return state.copy(
            error = "EngineerNode: No dev project state"
        )

        val prompt = when (mode) {
            EngineerMode.IMPLEMENT -> buildImplementationPrompt(devState)
            EngineerMode.FIX -> buildFixingPrompt(devState)
        }

        Log.i(TAG, "Sending ${mode.name} request to OpenClaw gateway...")

        return try {
            client.patchSession(SessionPatchParams(
                key = GatewayConfig.SessionKeys.ENGINEER,
                model = GatewayConfig.Models.ENGINEER
            ))

            val result = client.sendMessage(
                sessionKey = GatewayConfig.SessionKeys.ENGINEER,
                message = prompt
            )

            val responseText = result.text ?: ""
            val tokensUsed = result.usage?.totalTokens ?: 0

            Log.i(TAG, "Response received")

            val fileChanges = DevResponseParser.parseFileChanges(responseText)
            val testResults = DevResponseParser.parseTestResults(responseText)
            val success = testResults == null || testResults.passed

            // Update files map
            val newFiles = devState.files.toMutableMap()
            for (change in fileChanges) {
                when (change.action) {
                    FileAction.DELETE -> newFiles.remove(change.path)
                    else -> if (change.content != null) newFiles[change.path] = change.content
                }
            }

            // Mark current step as completed if successful
            val updatedPlan = if (mode == EngineerMode.IMPLEMENT) {
                devState.plan.map { step ->
                    if (step.id == devState.currentStepId && success)
                        step.copy(status = PlanStepStatus.COMPLETED)
                    else step
                }
            } else devState.plan

            // Find next step
            val nextStep = updatedPlan.firstOrNull {
                it.status == PlanStepStatus.PENDING && it.assignee == DevRole.CODER
            }

            val updatedDevState = devState.copy(
                files = newFiles,
                plan = updatedPlan,
                currentStepId = nextStep?.id ?: devState.currentStepId,
                fileChanges = devState.fileChanges + fileChanges,
                testResults = testResults,
                lintResults = parseLintResults(responseText),
                status = if (mode == EngineerMode.FIX) DevProjectStatus.FIXING else DevProjectStatus.CODING,
                iterationCount = devState.iterationCount + 1,
                consecutiveFailures = if (success) 0 else devState.consecutiveFailures + 1,
                lastError = if (success) null else testResults?.output ?: "Implementation issue",
                errors = if (success) devState.errors else devState.errors + (testResults?.output ?: "Implementation issue"),
                totalTokensUsed = devState.totalTokensUsed + tokensUsed,
                lastUpdateTimeMs = System.currentTimeMillis()
            )

            state.copy(devProject = updatedDevState)
        } catch (e: Exception) {
            Log.e(TAG, "Error in engineer node", e)
            state.copy(
                devProject = devState.copy(
                    consecutiveFailures = devState.consecutiveFailures + 1,
                    lastError = e.message,
                    errors = devState.errors + (e.message ?: "Unknown error"),
                    iterationCount = devState.iterationCount + 1
                )
            )
        }
    }

    private fun buildImplementationPrompt(state: DevProjectState): String {
        val currentStep = state.plan.find { it.id == state.currentStepId }
        val completedSteps = state.plan.filter { it.status == PlanStepStatus.COMPLETED }
        val existingFiles = state.files.keys.toList()

        return buildString {
            appendLine("Implement the following step from the plan:")
            appendLine()
            appendLine("**Project Objective:** ${state.objective}")
            appendLine()
            appendLine("**Current Step (${state.currentStepId} of ${state.plan.size}):**")
            appendLine(currentStep?.description ?: "No specific step - implement based on objective")
            appendLine()
            appendLine("**Completed Steps:**")
            appendLine(completedSteps.joinToString("\n") { "✓ ${it.id}. ${it.description}" }.ifEmpty { "None yet" })
            appendLine()
            appendLine("**Existing Files:**")
            appendLine(existingFiles.joinToString("\n") { "- $it" }.ifEmpty { "None yet" })

            if (state.reviewComments.isNotEmpty()) {
                appendLine()
                appendLine("**Review Comments to Address:**")
                state.reviewComments.forEach { c ->
                    appendLine("- [${c.severity}] ${c.file}${c.line?.let { ":$it" } ?: ""}: ${c.message}")
                }
            }

            state.lastError?.let {
                appendLine()
                appendLine("**Previous Error to Fix:**")
                appendLine(it)
            }

            appendLine()
            appendLine("When complete, summarize:")
            appendLine("FILES_CHANGED:")
            appendLine("- [create|modify|delete] path/to/file")
            appendLine()
            appendLine("TEST_RESULTS:")
            appendLine("- passed: true|false")
            appendLine("- output: summary")
        }
    }

    private fun buildFixingPrompt(state: DevProjectState): String {
        val existingFiles = state.files.keys.toList()

        return buildString {
            appendLine("Fix the following issues in the codebase:")
            appendLine()
            appendLine("**Project Objective:** ${state.objective}")
            appendLine()

            state.testResults?.let { test ->
                if (!test.passed) {
                    appendLine("**Failed Tests:**")
                    appendLine("Output: ${test.output}")
                    appendLine("Errors:")
                    test.errors.forEach { appendLine(it) }
                    appendLine()
                }
            }

            if (state.reviewComments.isNotEmpty()) {
                appendLine("**Review Comments:**")
                state.reviewComments
                    .filter { it.severity == ReviewSeverity.ERROR || it.severity == ReviewSeverity.CRITICAL }
                    .forEach { c ->
                        appendLine("- [${c.severity}] ${c.file}:${c.line ?: "?"} - ${c.message}")
                        c.suggestion?.let { appendLine("  Suggestion: $it") }
                    }
                appendLine()
            }

            state.lastError?.let {
                appendLine("**Error to Fix:**")
                appendLine(it)
                appendLine()
            }

            appendLine("**Current Files:**")
            existingFiles.forEach { appendLine("- $it") }
            appendLine()
            appendLine("FILES_CHANGED:")
            appendLine("- [modify] path/to/file")
            appendLine()
            appendLine("TEST_RESULTS:")
            appendLine("- passed: true|false")
            appendLine("- output: summary")
        }
    }

    private fun parseLintResults(response: String): List<String> {
        val match = Regex("lint(?:ing)?\\s+(?:results?|output):\\s*([\\s\\S]*?)(?:\\n\\n|$)", RegexOption.IGNORE_CASE).find(response)
        return match?.groupValues?.get(1)?.lines()?.filter { it.isNotBlank() } ?: emptyList()
    }
}
