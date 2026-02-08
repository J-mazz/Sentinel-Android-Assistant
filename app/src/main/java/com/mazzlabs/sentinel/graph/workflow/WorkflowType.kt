package com.mazzlabs.sentinel.graph.workflow

/**
 * WorkflowType - Determines which graph configuration to use
 */
enum class WorkflowType {
    /** Standard assistant workflow (existing behavior) */
    ASSISTANT,
    /** Multi-agent dev project workflow */
    DEV_PROJECT
}

/**
 * Remote agent roles for dev workflow
 */
enum class RemoteAgentRole {
    ARCHITECT,
    ENGINEER,
    FIXER
}
