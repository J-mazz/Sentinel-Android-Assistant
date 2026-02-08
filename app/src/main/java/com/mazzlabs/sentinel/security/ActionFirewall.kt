package com.mazzlabs.sentinel.security

import android.util.Log
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.security.DataClassifier

/**
 * ActionFirewall - Security Gating Layer
 * 
 * Analyzes actions before execution and determines if they require
 * user confirmation (physical volume-up key press).
 * 
 * Security: This is the last line of defense before action execution.
 * Errs on the side of caution - when in doubt, require confirmation.
 */
class ActionFirewall {

    companion object {
        private const val TAG = "ActionFirewall"
        
        /**
         * Dangerous action keywords that trigger confirmation
         */
        private val DANGEROUS_CLICK_TARGETS = setOf(
            // Destructive actions
            "delete", "remove", "uninstall", "erase", "wipe", "clear",
            "format", "reset", "factory", "destroy",
            
            // Financial actions
            "purchase", "buy", "pay", "confirm", "submit", "send",
            "transfer", "withdraw", "checkout", "order",
            
            // Permission/Security actions
            "allow", "grant", "enable", "disable", "revoke",
            "install", "download", "update",
            
            // Communication actions that send data
            "post", "share", "publish", "tweet", "message"
        )
        
        /**
         * Sensitive text patterns that trigger confirmation for TYPE actions
         */
        private val SENSITIVE_TEXT_PATTERNS = listOf(
            // Financial
            Regex("\\b\\d{13,19}\\b"),           // Credit card numbers
            Regex("\\b\\d{3,4}\\b"),              // CVV
            Regex("\\b\\d{5,}\\b"),               // Account numbers
            
            // Personal
            Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"),  // SSN
            Regex("\\b\\d{9}\\b"),                 // SSN without dashes
            
            // Authentication
            Regex("password", RegexOption.IGNORE_CASE),
            Regex("secret", RegexOption.IGNORE_CASE),
            Regex("pin", RegexOption.IGNORE_CASE),
            Regex("token", RegexOption.IGNORE_CASE)
        )
        
        /**
         * Safe/whitelisted targets that never require confirmation
         */
        private val SAFE_TARGETS = setOf(
            "cancel", "close", "back", "dismiss", "skip",
            "home", "menu", "search", "settings"
        )
    }

    /**
     * Determine if an action is dangerous and requires physical confirmation
     * 
     * @param action The action to analyze
     * @return true if action requires Volume Up confirmation
     */
    fun isDangerous(action: AgentAction): Boolean {
        val isDangerous = when (action.action) {
            ActionType.CLICK -> isClickDangerous(action)
            ActionType.TYPE -> isTypeDangerous(action)
            ActionType.SCROLL -> false // Scrolling is always safe
            ActionType.HOME -> false   // Going home is safe
            ActionType.BACK -> false   // Going back is safe
            ActionType.WAIT -> false   // Waiting is safe
            ActionType.NONE -> false   // No-op is safe
        }
        
        if (isDangerous) {
            Log.w(TAG, "DANGEROUS action detected: ${action.action} target=${action.target}")
        }
        
        return isDangerous
    }

    /**
     * Analyze a CLICK action for danger
     */
    private fun isClickDangerous(action: AgentAction): Boolean {
        val target = action.target?.lowercase() ?: return false
        
        // Check if target is explicitly safe
        if (SAFE_TARGETS.any { target.contains(it) }) {
            return false
        }
        
        // Check if target matches dangerous patterns
        return DANGEROUS_CLICK_TARGETS.any { dangerous ->
            target.contains(dangerous)
        }
    }

    /**
     * Analyze a TYPE action for sensitive content
     */
    private fun isTypeDangerous(action: AgentAction): Boolean {
        val text = action.text ?: return false
        
        // Check for sensitive patterns
        return SENSITIVE_TEXT_PATTERNS.any { pattern ->
            pattern.containsMatchIn(text)
        }
    }

    /**
     * Get explanation of why an action is considered dangerous
     */
    fun getDangerReason(action: AgentAction): String? {
        if (!isDangerous(action)) return null
        
        return when (action.action) {
            ActionType.CLICK -> {
                val target = action.target?.lowercase() ?: return null
                val matchedDanger = DANGEROUS_CLICK_TARGETS.firstOrNull { target.contains(it) }
                "Action involves '$matchedDanger' which may have permanent effects"
            }
            ActionType.TYPE -> {
                "Text contains potentially sensitive information"
            }
            else -> null
        }
    }

    /**
     * Validate action structure before firewall check
     */
    fun validateAction(action: AgentAction): ValidationResult {
        // Check required fields based on action type
        return when (action.action) {
            ActionType.CLICK -> {
                if (action.target.isNullOrBlank()) {
                    ValidationResult.Invalid("CLICK requires a target")
                } else {
                    ValidationResult.Valid
                }
            }
            ActionType.TYPE -> {
                if (action.text.isNullOrBlank()) {
                    ValidationResult.Invalid("TYPE requires text")
                } else {
                    ValidationResult.Valid
                }
            }
            ActionType.SCROLL -> {
                if (action.direction == null) {
                    ValidationResult.Invalid("SCROLL requires direction")
                } else {
                    ValidationResult.Valid
                }
            }
            else -> ValidationResult.Valid
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    // --- Network action security (Phase 2 extension) ---

    private val dataClassifier = DataClassifier()

    /**
     * Check if a network-bound action (outgoing payload) is dangerous
     *
     * @param method The gateway method being called
     * @param payload The serialized payload being sent
     * @return true if the action requires user confirmation
     */
    fun isNetworkActionDangerous(method: String, payload: String): Boolean {
        // Tool invocations that modify remote state
        val dangerousMethods = setOf(
            "write", "delete", "exec", "rm", "mv",
            "git push", "deploy", "publish"
        )

        if (dangerousMethods.any { method.contains(it, ignoreCase = true) }) {
            Log.w(TAG, "Dangerous network method: $method")
            return true
        }

        // Check for PII in payload
        val classification = dataClassifier.classify(payload)
        if (classification.containsPII) {
            Log.w(TAG, "PII detected in outgoing payload: ${classification.detectedTypes}")
            return true
        }

        return false
    }

    /**
     * Get explanation of why a network action is dangerous
     */
    fun getNetworkDangerReason(method: String, payload: String): String? {
        if (!isNetworkActionDangerous(method, payload)) return null

        val classification = dataClassifier.classify(payload)
        return if (classification.containsPII) {
            "Payload contains sensitive data: ${classification.detectedTypes.joinToString()}"
        } else {
            "Method '$method' modifies remote state"
        }
    }
}
