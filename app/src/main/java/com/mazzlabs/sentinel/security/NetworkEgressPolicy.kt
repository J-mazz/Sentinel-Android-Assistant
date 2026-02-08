package com.mazzlabs.sentinel.security

import android.util.Log

/**
 * NetworkEgressPolicy - Allowlisted gateway host and data type classification
 *
 * Defines what data can leave the device and to which destinations.
 * Part of the extended security model for network operations.
 */
class NetworkEgressPolicy {

    companion object {
        private const val TAG = "NetworkEgressPolicy"
    }

    /**
     * Data sensitivity levels
     */
    enum class DataSensitivity {
        /** Public data - safe to send anywhere */
        PUBLIC,
        /** Internal data - safe to send to allowlisted hosts */
        INTERNAL,
        /** Confidential - requires explicit user consent */
        CONFIDENTIAL,
        /** Restricted - never leaves device */
        RESTRICTED
    }

    /**
     * Data types and their default sensitivity
     */
    private val dataTypeSensitivity = mutableMapOf(
        "screen_context" to DataSensitivity.INTERNAL,
        "user_query" to DataSensitivity.PUBLIC,
        "ocr_text" to DataSensitivity.INTERNAL,
        "screen_image" to DataSensitivity.CONFIDENTIAL,
        "file_content" to DataSensitivity.INTERNAL,
        "tool_result" to DataSensitivity.INTERNAL,
        "pii_phone" to DataSensitivity.RESTRICTED,
        "pii_email" to DataSensitivity.RESTRICTED,
        "pii_ssn" to DataSensitivity.RESTRICTED,
        "pii_credit_card" to DataSensitivity.RESTRICTED,
        "credentials" to DataSensitivity.RESTRICTED,
        "conversation_history" to DataSensitivity.CONFIDENTIAL
    )

    /**
     * Check if data of a given type can be sent to the network
     */
    fun canSendDataType(dataType: String, hasUserConsent: Boolean = false): Boolean {
        val sensitivity = dataTypeSensitivity[dataType] ?: DataSensitivity.CONFIDENTIAL

        return when (sensitivity) {
            DataSensitivity.PUBLIC -> true
            DataSensitivity.INTERNAL -> true
            DataSensitivity.CONFIDENTIAL -> hasUserConsent
            DataSensitivity.RESTRICTED -> {
                Log.w(TAG, "Blocked attempt to send restricted data: $dataType")
                false
            }
        }
    }

    /**
     * Get the sensitivity level for a data type
     */
    fun getSensitivity(dataType: String): DataSensitivity {
        return dataTypeSensitivity[dataType] ?: DataSensitivity.CONFIDENTIAL
    }

    /**
     * Override sensitivity for a data type (user preference)
     */
    fun overrideSensitivity(dataType: String, sensitivity: DataSensitivity) {
        // Never allow downgrading RESTRICTED types
        if (dataTypeSensitivity[dataType] == DataSensitivity.RESTRICTED) {
            Log.w(TAG, "Cannot override restricted data type: $dataType")
            return
        }
        dataTypeSensitivity[dataType] = sensitivity
    }
}
