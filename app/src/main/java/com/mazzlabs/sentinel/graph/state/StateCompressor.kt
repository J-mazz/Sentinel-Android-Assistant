package com.mazzlabs.sentinel.graph.state

/**
 * StateCompressor - Message compression and garbage collection
 *
 * Port of gc/compression logic from colabPro/src/utils/context-manager.ts
 */
object StateCompressor {

    /**
     * Create a compressed message from content
     */
    fun createMessage(role: MessageRole, content: String): CompressedMessage {
        return CompressedMessage(
            role = role,
            content = content,
            tokenCount = ContextBudget.estimateTokens(content),
            isCompressed = false
        )
    }

    /**
     * Garbage collect old state entries to keep memory bounded
     */
    fun garbageCollect(
        state: DevProjectState,
        budget: ContextBudget = ContextBudget.DEFAULT
    ): DevProjectState {
        return state.copy(
            fileChanges = state.fileChanges.takeLast(20),
            errors = state.errors.takeLast(10),
            messageHistory = budget.compressMessageHistory(state.messageHistory, 10000),
            lintResults = state.lintResults.takeLast(20),
            lastUpdateTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * Update state immutably with automatic timestamp
     */
    fun updateState(
        current: DevProjectState,
        updates: DevProjectState.() -> DevProjectState
    ): DevProjectState {
        return current.updates().copy(lastUpdateTimeMs = System.currentTimeMillis())
    }
}
