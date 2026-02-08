package com.mazzlabs.sentinel.graph.state

/**
 * ContextBudget - Port of colabPro/src/utils/context-manager.ts
 *
 * Token budgeting for context management between graph transitions.
 * Prevents context overflow while maintaining important information.
 */
data class ContextBudget(
    val maxTotalTokens: Int = 128000,
    val reservedForResponse: Int = 8000,
    val maxHistoryTokens: Int = 40000,
    val maxStateTokens: Int = 20000
) {
    companion object {
        val DEFAULT = ContextBudget()

        /** Rough token estimation: ~4 characters per token for English text */
        fun estimateTokens(text: String): Int = (text.length + 3) / 4
    }

    val availableForContent: Int get() = maxTotalTokens - reservedForResponse

    /**
     * Compress state for passing between nodes.
     * Only includes essential information, summarizes the rest.
     */
    fun compressStateForTransition(state: DevProjectState): CompressedState {
        // Always include these fields
        val relevantPlan = state.plan.filter {
            it.status != PlanStepStatus.COMPLETED || it.id == state.currentStepId
        }

        val recentReviewComments = state.reviewComments.takeLast(5)
        val recentErrors = state.errors.takeLast(3)

        // Build file changes summary
        val fileChangesSummary = summarizeFileChanges(state.fileChanges)

        // Build progress summary
        val summary = buildProgressSummary(state, fileChangesSummary)

        val essentialState = state.copy(
            plan = relevantPlan,
            reviewComments = recentReviewComments,
            errors = recentErrors,
            fileChanges = emptyList(), // summarized instead
            files = emptyMap(),  // too large to include
            messageHistory = emptyList() // summarized instead
        )

        val tokensUsed = estimateTokens(essentialState.toString()) + estimateTokens(summary)

        return CompressedState(
            state = essentialState,
            summary = summary,
            tokensUsed = tokensUsed
        )
    }

    /**
     * Compress message history to fit within budget
     */
    fun compressMessageHistory(
        messages: List<CompressedMessage>,
        maxTokens: Int = maxHistoryTokens
    ): List<CompressedMessage> {
        var totalTokens = 0
        val result = mutableListOf<CompressedMessage>()
        val reversed = messages.reversed()

        for (msg in reversed) {
            if (totalTokens + msg.tokenCount > maxTokens) {
                val remainingCount = reversed.size - result.size
                if (remainingCount > 0) {
                    result.add(CompressedMessage(
                        role = MessageRole.SYSTEM,
                        content = "[$remainingCount older messages summarized]",
                        tokenCount = 20,
                        isCompressed = true,
                        originalLength = remainingCount
                    ))
                }
                break
            }
            result.add(msg)
            totalTokens += msg.tokenCount
        }

        return result.reversed()
    }

    /**
     * Check if state needs compression
     */
    fun needsCompression(state: DevProjectState): Boolean {
        val stateText = buildString {
            append(state.plan.toString())
            append(state.files.toString())
            append(state.fileChanges.toString())
            append(state.reviewComments.toString())
            append(state.errors.toString())
            append(state.messageHistory.toString())
        }
        return estimateTokens(stateText) > maxStateTokens
    }

    private fun estimateTokens(text: String): Int = Companion.estimateTokens(text)

    private fun summarizeFileChanges(changes: List<DevFileChange>): String {
        if (changes.isEmpty()) return "No file changes yet."

        val byPath = mutableMapOf<String, MutableSet<FileAction>>()
        val countByPath = mutableMapOf<String, Int>()

        for (change in changes) {
            byPath.getOrPut(change.path) { mutableSetOf() }.add(change.action)
            countByPath[change.path] = (countByPath[change.path] ?: 0) + 1
        }

        val lines = byPath.map { (path, actions) ->
            val count = countByPath[path] ?: 1
            val actionsStr = actions.joinToString("/") { it.name.lowercase() }
            "- $path: $actionsStr${if (count > 1) " (${count}x)" else ""}"
        }

        return "File changes:\n${lines.joinToString("\n")}"
    }

    private fun buildProgressSummary(state: DevProjectState, fileChangesSummary: String): String {
        val completedSteps = state.plan.count { it.status == PlanStepStatus.COMPLETED }
        val totalSteps = state.plan.size

        return buildString {
            appendLine("## Progress Summary")
            appendLine()
            appendLine("**Objective:** ${state.objective}")
            appendLine("**Status:** ${state.status}")
            appendLine("**Progress:** $completedSteps/$totalSteps steps complete")
            appendLine("**Iteration:** ${state.iterationCount}/${state.maxIterations}")

            state.testResults?.let { test ->
                appendLine()
                if (test.passed) {
                    appendLine("**Last Test Result:** PASSED")
                } else {
                    appendLine("**Last Test Result:** FAILED")
                    appendLine("Output: ${test.output.take(200)}...")
                }
            }

            if (state.lastSummary.isNotBlank()) {
                appendLine()
                appendLine("**Last Action:** ${state.lastSummary}")
            }

            appendLine()
            appendLine(fileChangesSummary)
        }
    }
}

data class CompressedState(
    val state: DevProjectState,
    val summary: String,
    val tokensUsed: Int
)
