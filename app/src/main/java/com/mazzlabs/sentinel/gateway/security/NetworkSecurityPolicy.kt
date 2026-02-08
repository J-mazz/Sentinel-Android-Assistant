package com.mazzlabs.sentinel.gateway.security

import android.util.Log

/**
 * NetworkSecurityPolicy - TLS pinning and allowed hosts
 *
 * Enforces network security by restricting which hosts the gateway client
 * can connect to. Part of the 6-layer security model extension for network ops.
 */
class NetworkSecurityPolicy {

    companion object {
        private const val TAG = "NetworkSecPolicy"

        /** Hosts that are always allowed for gateway connections */
        private val DEFAULT_ALLOWED_HOSTS = setOf(
            "127.0.0.1",
            "localhost",
            "::1"
        )
    }

    private val allowedHosts = mutableSetOf<String>().apply {
        addAll(DEFAULT_ALLOWED_HOSTS)
    }

    /**
     * Add a host to the allowlist
     */
    fun addAllowedHost(host: String) {
        allowedHosts.add(host.lowercase())
        Log.i(TAG, "Added allowed host: $host")
    }

    /**
     * Remove a host from the allowlist
     */
    fun removeAllowedHost(host: String) {
        if (host !in DEFAULT_ALLOWED_HOSTS) {
            allowedHosts.remove(host.lowercase())
        }
    }

    /**
     * Check if a host is allowed for gateway connections
     */
    fun isHostAllowed(host: String): Boolean {
        val normalized = host.lowercase()
        val allowed = normalized in allowedHosts || isLocalNetworkHost(normalized)
        if (!allowed) {
            Log.w(TAG, "Connection to unauthorized host blocked: $host")
        }
        return allowed
    }

    /**
     * Check if a URL is allowed
     */
    fun isUrlAllowed(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return isHostAllowed(host)
    }

    /**
     * Check if URL uses TLS (required for non-local hosts)
     */
    fun requiresTls(url: String): Boolean {
        val host = extractHost(url) ?: return true
        // Local connections don't require TLS
        if (isLocalNetworkHost(host)) return false
        // Everything else requires TLS
        return !url.startsWith("wss://") && !url.startsWith("https://")
    }

    /**
     * Validate a gateway URL
     */
    fun validateGatewayUrl(url: String): ValidationResult {
        val host = extractHost(url) ?: return ValidationResult.Invalid("Cannot parse host from URL")

        if (!isHostAllowed(host)) {
            return ValidationResult.Invalid("Host not in allowlist: $host")
        }

        if (requiresTls(url)) {
            return ValidationResult.Invalid("TLS required for non-local host: $host")
        }

        return ValidationResult.Valid
    }

    private fun extractHost(url: String): String? {
        return try {
            val withoutScheme = url
                .removePrefix("ws://")
                .removePrefix("wss://")
                .removePrefix("http://")
                .removePrefix("https://")
            withoutScheme.substringBefore(":").substringBefore("/")
        } catch (_: Exception) {
            null
        }
    }

    private fun isLocalNetworkHost(host: String): Boolean {
        return host == "127.0.0.1" ||
            host == "localhost" ||
            host == "::1" ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.startsWith("172.16.") ||
            host.endsWith(".local")
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}
