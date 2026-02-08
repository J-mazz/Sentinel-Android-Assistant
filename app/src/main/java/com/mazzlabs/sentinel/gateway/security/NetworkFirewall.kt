package com.mazzlabs.sentinel.gateway.security

import android.util.Log
import com.mazzlabs.sentinel.security.DataClassifier

/**
 * NetworkFirewall - Egress filtering for gateway communications
 *
 * Filters outgoing payloads to prevent sensitive data from leaving the device.
 * Works with DataClassifier to block PII in outgoing messages.
 */
class NetworkFirewall(
    private val securityPolicy: NetworkSecurityPolicy = NetworkSecurityPolicy(),
    private val dataClassifier: DataClassifier = DataClassifier()
) {
    companion object {
        private const val TAG = "NetworkFirewall"

        /** Maximum payload size in bytes (5MB) */
        private const val MAX_PAYLOAD_SIZE = 5 * 1024 * 1024
    }

    /**
     * Result of firewall check
     */
    sealed class FirewallResult {
        object Allowed : FirewallResult()
        data class Blocked(val reason: String, val detectedItems: List<String> = emptyList()) : FirewallResult()
    }

    /**
     * Check if an outgoing payload is safe to send
     */
    fun checkOutgoingPayload(payload: String, targetHost: String? = null): FirewallResult {
        // Check host allowlist
        if (targetHost != null && !securityPolicy.isHostAllowed(targetHost)) {
            return FirewallResult.Blocked("Target host not allowed: $targetHost")
        }

        // Check payload size
        if (payload.length > MAX_PAYLOAD_SIZE) {
            return FirewallResult.Blocked("Payload exceeds maximum size: ${payload.length} bytes")
        }

        // Classify data for PII
        val classification = dataClassifier.classify(payload)
        if (classification.containsPII) {
            Log.w(TAG, "PII detected in outgoing payload: ${classification.detectedTypes}")
            return FirewallResult.Blocked(
                "PII detected in outgoing data",
                classification.detectedTypes
            )
        }

        return FirewallResult.Allowed
    }

    /**
     * Sanitize a payload by redacting detected PII
     */
    fun sanitizePayload(payload: String): String {
        return dataClassifier.redact(payload)
    }

    /**
     * Check if a network action is dangerous (for ActionFirewall integration)
     */
    fun isNetworkActionDangerous(method: String, payload: String): Boolean {
        // Tool invocations that modify remote state are dangerous
        val dangerousMethods = setOf(
            "write", "delete", "exec", "rm", "mv",
            "git push", "deploy", "publish"
        )

        if (dangerousMethods.any { method.contains(it, ignoreCase = true) }) {
            return true
        }

        val classification = dataClassifier.classify(payload)
        return classification.containsPII
    }
}
