package com.mazzlabs.sentinel.gateway.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * GatewayAuthManager - Secure token storage via EncryptedSharedPreferences
 *
 * Stores gateway credentials securely using Android's Keystore-backed encryption.
 * Works on GrapheneOS without Google Play Services.
 */
class GatewayAuthManager(private val context: Context) {

    companion object {
        private const val PREF_FILE = "sentinel_gateway_auth"
        private const val KEY_GATEWAY_URL = "gateway_url"
        private const val KEY_GATEWAY_TOKEN = "gateway_token"
        private const val KEY_GATEWAY_PASSWORD = "gateway_password"
        private const val KEY_GATEWAY_ENABLED = "gateway_enabled"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var gatewayUrl: String
        get() = prefs.getString(KEY_GATEWAY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GATEWAY_URL, value).apply()

    var gatewayToken: String
        get() = prefs.getString(KEY_GATEWAY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GATEWAY_TOKEN, value).apply()

    var gatewayPassword: String
        get() = prefs.getString(KEY_GATEWAY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GATEWAY_PASSWORD, value).apply()

    var isGatewayEnabled: Boolean
        get() = prefs.getBoolean(KEY_GATEWAY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GATEWAY_ENABLED, value).apply()

    /**
     * Check if gateway credentials are configured
     */
    fun isConfigured(): Boolean {
        return gatewayUrl.isNotEmpty() && (gatewayToken.isNotEmpty() || gatewayPassword.isNotEmpty())
    }

    /**
     * Clear all stored credentials
     */
    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_GATEWAY_TOKEN)
            .remove(KEY_GATEWAY_PASSWORD)
            .apply()
    }

    /**
     * Clear all gateway settings
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
