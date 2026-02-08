package com.mazzlabs.sentinel.graph.state

/**
 * DevProjectState - Port of colabPro/src/state/project-state.ts
 *
 * Tracks the entire lifecycle of a multi-agent coding workflow.
 * This is the "brain" state for the dev workflow graph.
 */
data class DevProjectState(
    // Core objective
    val objective: String = "",
    val projectName: String = "",

    // Planning
    val plan: List<DevPlanStep> = emptyList(),
    val currentStepId: Int? = null,

    // File tracking
    val files: Map<String, String> = emptyMap(),
    val currentFile: String? = null,
    val fileChanges: List<DevFileChange> = emptyList(),

    // Code & Testing
    val codeContent: String = "",
    val testResults: DevTestResult? = null,
    val lintResults: List<String> = emptyList(),

    // Review
    val reviewComments: List<DevReviewComment> = emptyList(),
    val approvalStatus: ApprovalStatus = ApprovalStatus.PENDING,

    // Iteration & Control
    val iterationCount: Int = 0,
    val maxIterations: Int = 10,
    val status: DevProjectStatus = DevProjectStatus.INITIALIZING,

    // Context Management
    val messageHistory: List<CompressedMessage> = emptyList(),
    val lastSummary: String = "",
    val totalTokensUsed: Int = 0,

    // Error Tracking
    val errors: List<String> = emptyList(),
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,

    // Metadata
    val startTimeMs: Long = System.currentTimeMillis(),
    val lastUpdateTimeMs: Long = System.currentTimeMillis()
) {
    /**
     * Check if we should escalate to architect
     */
    fun shouldEscalate(): Boolean {
        return consecutiveFailures >= 3 ||
            iterationCount >= maxIterations - 2 ||
            reviewComments.any { it.severity == ReviewSeverity.CRITICAL }
    }

    /**
     * Check if project is in a terminal state
     */
    fun isTerminal(): Boolean {
        return status == DevProjectStatus.FINISHED || status == DevProjectStatus.FAILED
    }

    companion object {
        fun create(
            objective: String,
            projectName: String? = null,
            maxIterations: Int = 10
        ): DevProjectState {
            val now = System.currentTimeMillis()
            return DevProjectState(
                objective = objective,
                projectName = projectName ?: "project-$now",
                maxIterations = maxIterations,
                startTimeMs = now,
                lastUpdateTimeMs = now
            )
        }
    }
}

enum class DevProjectStatus {
    INITIALIZING,
    PLANNING,
    CODING,
    TESTING,
    REVIEWING,
    FIXING,
    FINISHED,
    FAILED
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    NEEDS_CHANGES
}

data class DevTestResult(
    val passed: Boolean,
    val output: String,
    val errors: List<String> = emptyList(),
    val durationMs: Long = 0
)

data class DevFileChange(
    val path: String,
    val action: FileAction,
    val content: String? = null,
    val diff: String? = null
)

enum class FileAction {
    CREATE, MODIFY, DELETE
}

data class DevReviewComment(
    val file: String,
    val line: Int? = null,
    val severity: ReviewSeverity,
    val message: String,
    val suggestion: String? = null
)

enum class ReviewSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

data class DevPlanStep(
    val id: Int,
    val description: String,
    val status: PlanStepStatus = PlanStepStatus.PENDING,
    val assignee: DevRole = DevRole.CODER,
    val dependencies: List<Int>? = null
)

enum class PlanStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED
}

enum class DevRole {
    ARCHITECT, CODER, FIXER
}

data class CompressedMessage(
    val role: MessageRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val isCompressed: Boolean = false,
    val originalLength: Int? = null
)

enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}
