package com.mazzlabs.sentinel.graph.nodes.dev

import android.util.Log
import com.mazzlabs.sentinel.gateway.GatewayConfig
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.gateway.SessionPatchParams
import com.mazzlabs.sentinel.graph.AgentNode
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.state.*

/**
 * ArchitectNode - Port of colabPro/src/nodes/architect-node.ts
 *
 * Uses OpenClaw gateway to run the architect agent for planning and review.
 * Operates on the DevProjectState attached to AgentState.
 */
class ArchitectNode(
    private val client: OpenClawGatewayClient,
    private val mode: ArchitectMode
) : AgentNode {

    companion object {
        private const val TAG = "ArchitectNode"
    }

    enum class ArchitectMode { PLANNING, REVIEWING }

    override suspend fun process(state: AgentState): AgentState {
        val devState = state.devProject ?: return state.copy(
            error = "ArchitectNode: No dev project state"
        )

        val prompt = when (mode) {
            ArchitectMode.PLANNING -> buildPlanningPrompt(devState)
            ArchitectMode.REVIEWING -> buildReviewPrompt(devState)
        }

        Log.i(TAG, "Sending ${mode.name} request to OpenClaw gateway...")

        return try {
            // Configure the architect session
            client.patchSession(SessionPatchParams(
                key = GatewayConfig.SessionKeys.ARCHITECT,
                model = GatewayConfig.Models.ARCHITECT
            ))

            val result = client.sendMessage(
                sessionKey = GatewayConfig.SessionKeys.ARCHITECT,
                message = prompt
            )

            val responseText = result.text ?: ""
            val tokensUsed = result.usage?.totalTokens ?: 0

            Log.i(TAG, "Response received (${responseText.length} chars)")

            val updatedDevState = when (mode) {
                ArchitectMode.PLANNING -> {
                    val plan = parsePlanFromResponse(responseText)
                    devState.copy(
                        plan = plan,
                        status = DevProjectStatus.PLANNING,
                        currentStepId = plan.firstOrNull()?.id,
                        lastSummary = "Created plan with ${plan.size} steps",
                        totalTokensUsed = devState.totalTokensUsed + tokensUsed,
                        lastUpdateTimeMs = System.currentTimeMillis()
                    )
                }
                ArchitectMode.REVIEWING -> {
                    val (comments, approvalStatus) = parseReviewFromResponse(responseText)
                    devState.copy(
                        reviewComments = comments,
                        approvalStatus = approvalStatus,
                        status = DevProjectStatus.REVIEWING,
                        lastSummary = "Review complete: ${approvalStatus.name} with ${comments.size} comments",
                        totalTokensUsed = devState.totalTokensUsed + tokensUsed,
                        lastUpdateTimeMs = System.currentTimeMillis()
                    )
                }
            }

            state.copy(devProject = updatedDevState)
        } catch (e: Exception) {
            Log.e(TAG, "Error in architect node", e)
            state.copy(
                devProject = devState.copy(
                    lastError = e.message,
                    errors = devState.errors + (e.message ?: "Unknown error")
                )
            )
        }
    }

    private fun buildPlanningPrompt(state: DevProjectState): String = """
        |Create a detailed implementation plan for the following objective:
        |
        |**Objective:** ${state.objective}
        |**Project Name:** ${state.projectName}
        |
        |${if (state.lastSummary.isNotBlank()) "**Previous Context:**\n${state.lastSummary}\n" else ""}
        |
        |Please:
        |1. First, use tools to explore the current project structure
        |2. Create a numbered list of implementation steps
        |3. For each step specify who should handle it: "architect" or "coder"
        |
        |Format your plan as:
        |PLAN:
        |1. [architect|coder] Description of step
        |2. [architect|coder] Description of step
        |...
    """.trimMargin()

    private fun buildReviewPrompt(state: DevProjectState): String {
        val fileList = state.files.keys.joinToString("\n- ")
        return buildString {
            appendLine("Review the following implementation:")
            appendLine()
            appendLine("**Objective:** ${state.objective}")
            appendLine("**Iteration:** ${state.iterationCount}")
            appendLine()
            appendLine("**Files to Review:**")
            appendLine("- ${fileList.ifEmpty { "No files yet" }}")
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
            if (state.lintResults.isNotEmpty()) {
                appendLine("**Lint Results:**\n${state.lintResults.joinToString("\n")}")
                appendLine()
            }
            appendLine("Review for correctness, quality, security, and test coverage.")
            appendLine()
            appendLine("Format issues as:")
            appendLine("ISSUES:")
            appendLine("- [severity] file:line - description (suggestion: fix)")
            appendLine()
            appendLine("VERDICT: [APPROVED|REJECTED|NEEDS-CHANGES]")
        }
    }

    private fun parsePlanFromResponse(response: String): List<DevPlanStep> {
        val steps = mutableListOf<DevPlanStep>()
        val planMatch = Regex("PLAN:\\s*([\\s\\S]*?)(?:\\n\\n|$)", RegexOption.IGNORE_CASE).find(response)
        val text = planMatch?.groupValues?.get(1) ?: response

        var id = 0
        for (line in text.lines()) {
            val match = Regex("""^\d+\.\s*\[?(architect|coder)\]?\s*(.+)""", RegexOption.IGNORE_CASE).find(line)
            if (match != null) {
                id++
                val assignee = if (match.groupValues[1].lowercase() == "architect") DevRole.ARCHITECT else DevRole.CODER
                steps.add(DevPlanStep(
                    id = id,
                    description = match.groupValues[2].trim(),
                    assignee = assignee
                ))
            }
        }
        return steps
    }

    private fun parseReviewFromResponse(response: String): Pair<List<DevReviewComment>, ApprovalStatus> {
        val comments = mutableListOf<DevReviewComment>()

        val issuesMatch = Regex("ISSUES:\\s*([\\s\\S]*?)(?:VERDICT:|$)", RegexOption.IGNORE_CASE).find(response)
        if (issuesMatch != null) {
            val issueLines = issuesMatch.groupValues[1].split(Regex("\n-\\s*"))
            for (line in issueLines) {
                val match = Regex("""\[(info|warning|error|critical)]\s*([^:]+):?(\d+)?\s*-\s*([^(]+)(?:\(suggestion:\s*([^)]+)\))?""", RegexOption.IGNORE_CASE).find(line)
                if (match != null) {
                    comments.add(DevReviewComment(
                        file = match.groupValues[2].trim(),
                        line = match.groupValues[3].toIntOrNull(),
                        severity = when (match.groupValues[1].lowercase()) {
                            "warning" -> ReviewSeverity.WARNING
                            "error" -> ReviewSeverity.ERROR
                            "critical" -> ReviewSeverity.CRITICAL
                            else -> ReviewSeverity.INFO
                        },
                        message = match.groupValues[4].trim(),
                        suggestion = match.groupValues[5].takeIf { it.isNotBlank() }?.trim()
                    ))
                }
            }
        }

        val verdictMatch = Regex("VERDICT:\\s*(APPROVED|REJECTED|NEEDS-CHANGES)", RegexOption.IGNORE_CASE).find(response)
        val status = when (verdictMatch?.groupValues?.get(1)?.uppercase()) {
            "APPROVED" -> ApprovalStatus.APPROVED
            "REJECTED" -> ApprovalStatus.REJECTED
            else -> ApprovalStatus.NEEDS_CHANGES
        }

        return comments to status
    }
}
