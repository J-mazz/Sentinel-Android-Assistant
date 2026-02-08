package com.mazzlabs.sentinel.graph.nodes.dev

import android.util.Log
import com.mazzlabs.sentinel.gateway.GatewayConfig
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.gateway.SessionPatchParams
import com.mazzlabs.sentinel.graph.AgentNode
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.state.*

/**
 * FixerNode - Port of colabPro/src/nodes/fixer-node.ts
 *
 * Specialized debugging agent via OpenClaw gateway.
 * Escalates to architect when fix attempts are exhausted.
 */
class FixerNode(
    private val client: OpenClawGatewayClient,
    private val maxFixAttempts: Int = 3
) : AgentNode {

    companion object {
        private const val TAG = "FixerNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        val devState = state.devProject ?: return state.copy(
            error = "FixerNode: No dev project state"
        )

        val attemptsRemaining = maxFixAttempts - devState.consecutiveFailures

        if (attemptsRemaining <= 0) {
            Log.i(TAG, "Max fix attempts exceeded - escalating to architect")
            return state.copy(
                devProject = devState.copy(
                    lastSummary = "Max fix attempts exceeded - escalating to architect for plan revision",
                    lastUpdateTimeMs = System.currentTimeMillis()
                )
            )
        }

        val prompt = buildDebuggingPrompt(devState)
        Log.i(TAG, "Sending debug request (attempt ${maxFixAttempts - attemptsRemaining + 1}/$maxFixAttempts)...")

        return try {
            client.patchSession(SessionPatchParams(
                key = GatewayConfig.SessionKeys.FIXER,
                model = GatewayConfig.Models.FIXER
            ))

            val result = client.sendMessage(
                sessionKey = GatewayConfig.SessionKeys.FIXER,
                message = prompt
            )

            val responseText = result.text ?: ""
            val tokensUsed = result.usage?.totalTokens ?: 0

            Log.i(TAG, "Response received")

            val fileChanges = DevResponseParser.parseFileChanges(responseText)
            val testResults = DevResponseParser.parseTestResults(responseText)
            val fixDescription = DevResponseParser.parseFixDescription(responseText)
            val fixed = testResults?.passed ?: fileChanges.isNotEmpty()

            val newFiles = devState.files.toMutableMap()
            for (change in fileChanges) {
                if (change.content != null) newFiles[change.path] = change.content
            }

            val updatedDevState = devState.copy(
                files = newFiles,
                fileChanges = devState.fileChanges + fileChanges,
                testResults = testResults,
                status = DevProjectStatus.FIXING,
                iterationCount = devState.iterationCount + 1,
                consecutiveFailures = if (fixed) 0 else devState.consecutiveFailures + 1,
                lastError = if (fixed) null else devState.lastError,
                lastSummary = fixDescription,
                totalTokensUsed = devState.totalTokensUsed + tokensUsed,
                lastUpdateTimeMs = System.currentTimeMillis()
            )

            state.copy(devProject = updatedDevState)
        } catch (e: Exception) {
            Log.e(TAG, "Error in fixer node", e)
            state.copy(
                devProject = devState.copy(
                    consecutiveFailures = devState.consecutiveFailures + 1,
                    lastError = e.message,
                    lastSummary = "Fix attempt failed: ${e.message}",
                    iterationCount = devState.iterationCount + 1
                )
            )
        }
    }

    private fun buildDebuggingPrompt(state: DevProjectState): String {
        val recentErrors = state.errors.takeLast(5)
        val existingFiles = state.files.keys.toList()

        return buildString {
            appendLine("Debug and fix the following issues:")
            appendLine()
            appendLine("**Iteration:** ${state.iterationCount} / ${state.maxIterations}")
            appendLine("**Consecutive Failures:** ${state.consecutiveFailures}")
            appendLine()
            appendLine("**Current Error:**")
            appendLine(state.lastError ?: "No specific error message")
            appendLine()
            appendLine("**Recent Errors:**")
            recentErrors.forEachIndexed { i, e -> appendLine("${i + 1}. $e") }
            appendLine()

            state.testResults?.let { test ->
                appendLine("**Test Results:**")
                appendLine("- Passed: ${test.passed}")
                appendLine("- Output: ${test.output}")
                if (test.errors.isNotEmpty()) {
                    appendLine("- Errors:\n${test.errors.joinToString("\n")}")
                }
                appendLine()
            }

            val criticalComments = state.reviewComments.filter {
                it.severity == ReviewSeverity.ERROR || it.severity == ReviewSeverity.CRITICAL
            }
            if (criticalComments.isNotEmpty()) {
                appendLine("**Review Issues:**")
                criticalComments.forEach { c ->
                    appendLine("- [${c.severity}] ${c.file}:${c.line ?: "?"} - ${c.message}")
                    c.suggestion?.let { appendLine("  Suggestion: $it") }
                }
                appendLine()
            }

            appendLine("**Available Files:**")
            existingFiles.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Debugging Protocol:")
            appendLine("1. READ - Analyze the error carefully")
            appendLine("2. LOCATE - Find where the error originates")
            appendLine("3. UNDERSTAND - Read surrounding code")
            appendLine("4. FIX - Make minimal, targeted changes")
            appendLine("5. VERIFY - Run tests to confirm")
            appendLine()
            appendLine("FIX_DESCRIPTION: Brief description of what you fixed")
            appendLine()
            appendLine("FILES_CHANGED:")
            appendLine("- [modify] path/to/file")
            appendLine()
            appendLine("TEST_RESULTS:")
            appendLine("- passed: true|false")
            appendLine("- output: summary")
        }
    }
}
