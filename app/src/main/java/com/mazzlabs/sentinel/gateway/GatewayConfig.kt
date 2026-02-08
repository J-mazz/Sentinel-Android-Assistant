package com.mazzlabs.sentinel.gateway

/**
 * Gateway Configuration
 *
 * Port of colabPro/src/config/models.ts
 * Model aliases and session keys for the multi-agent workflow
 */
object GatewayConfig {

    /**
     * Model identifiers used by different agent roles
     * NOTE: These should be synced with the actual OpenClaw gateway config
     */
    object Models {
        const val ARCHITECT = "anthropic/claude-opus-4-5"
        const val ENGINEER = "openai-codex/gpt-5.2-codex"
        const val FIXER = "openai/gpt-5.2-pro"
    }

    /** Session keys for each agent role */
    object SessionKeys {
        const val ARCHITECT = "agent:architect:orchestrator:planning"
        const val ENGINEER = "agent:coder:orchestrator:coding"
        const val FIXER = "agent:fixer:orchestrator:debugging"
    }

    /** Default gateway configuration */
    object Defaults {
        const val GATEWAY_URL = "ws://127.0.0.1:18789"
        const val CLIENT_NAME = "sentinel-android"
        const val CLIENT_DISPLAY_NAME = "Sentinel Android Agent"
        const val RECONNECT_DELAY_MS = 3000L
        const val REQUEST_TIMEOUT_MS = 60000L
        const val HEALTH_CHECK_INTERVAL_MS = 30000L
        const val MAX_RECONNECT_ATTEMPTS = 10
    }

    /** OkHttp timeout configuration */
    object Timeouts {
        const val CONNECT_TIMEOUT_S = 10
        const val READ_TIMEOUT_S = 120  // Long timeout for inference operations
        const val WRITE_TIMEOUT_S = 30
    }
}
