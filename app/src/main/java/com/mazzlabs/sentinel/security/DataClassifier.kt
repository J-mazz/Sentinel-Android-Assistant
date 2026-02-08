package com.mazzlabs.sentinel.security

import android.util.Log

/**
 * DataClassifier - PII detection for outgoing payloads
 *
 * Detects phone numbers, email addresses, SSNs, and credit card numbers
 * to prevent sensitive data from leaving the device through the gateway.
 */
class DataClassifier {

    companion object {
        private const val TAG = "DataClassifier"

        // PII detection patterns
        private val PHONE_PATTERN = Regex(
            """\b(?:\+?1[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b"""
        )

        private val EMAIL_PATTERN = Regex(
            """\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"""
        )

        private val SSN_PATTERN = Regex(
            """\b\d{3}[-\s]?\d{2}[-\s]?\d{4}\b"""
        )

        private val CREDIT_CARD_PATTERN = Regex(
            """\b(?:\d{4}[-\s]?){3}\d{4}\b"""
        )

        private val REDACTION_PLACEHOLDER = "[REDACTED]"
    }

    /**
     * Classification result
     */
    data class Classification(
        val containsPII: Boolean,
        val detectedTypes: List<String>,
        val matches: Map<String, List<String>> = emptyMap()
    )

    /**
     * Classify text for PII content
     */
    fun classify(text: String): Classification {
        val detectedTypes = mutableListOf<String>()
        val matches = mutableMapOf<String, List<String>>()

        val phones = PHONE_PATTERN.findAll(text).map { it.value }.toList()
        if (phones.isNotEmpty()) {
            detectedTypes.add("phone")
            matches["phone"] = phones
        }

        val emails = EMAIL_PATTERN.findAll(text).map { it.value }.toList()
        if (emails.isNotEmpty()) {
            detectedTypes.add("email")
            matches["email"] = emails
        }

        val ssns = SSN_PATTERN.findAll(text).map { it.value }.toList()
        // Filter out false positives (dates, etc) - SSN first digit can't be 9
        val validSsns = ssns.filter { ssn ->
            val digits = ssn.replace(Regex("[^0-9]"), "")
            digits.length == 9 && !digits.startsWith("9") && !digits.startsWith("000")
        }
        if (validSsns.isNotEmpty()) {
            detectedTypes.add("ssn")
            matches["ssn"] = validSsns
        }

        val creditCards = CREDIT_CARD_PATTERN.findAll(text).map { it.value }.toList()
        val validCards = creditCards.filter { cc ->
            val digits = cc.replace(Regex("[^0-9]"), "")
            digits.length in 13..19 && passesLuhnCheck(digits)
        }
        if (validCards.isNotEmpty()) {
            detectedTypes.add("credit_card")
            matches["credit_card"] = validCards
        }

        if (detectedTypes.isNotEmpty()) {
            Log.w(TAG, "PII detected: $detectedTypes")
        }

        return Classification(
            containsPII = detectedTypes.isNotEmpty(),
            detectedTypes = detectedTypes,
            matches = matches
        )
    }

    /**
     * Redact PII from text
     */
    fun redact(text: String): String {
        var redacted = text
        redacted = CREDIT_CARD_PATTERN.replace(redacted) { match ->
            val digits = match.value.replace(Regex("[^0-9]"), "")
            if (digits.length in 13..19 && passesLuhnCheck(digits)) REDACTION_PLACEHOLDER
            else match.value
        }
        redacted = SSN_PATTERN.replace(redacted) { match ->
            val digits = match.value.replace(Regex("[^0-9]"), "")
            if (digits.length == 9 && !digits.startsWith("9") && !digits.startsWith("000")) REDACTION_PLACEHOLDER
            else match.value
        }
        redacted = EMAIL_PATTERN.replace(redacted, REDACTION_PLACEHOLDER)
        redacted = PHONE_PATTERN.replace(redacted, REDACTION_PLACEHOLDER)
        return redacted
    }

    /**
     * Luhn check for credit card validation
     */
    private fun passesLuhnCheck(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
