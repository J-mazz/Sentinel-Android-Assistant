package com.mazzlabs.sentinel.inference

import android.util.Log

/**
 * InferenceRouter - Policy-based routing between local and remote inference
 *
 * Routes inference requests based on the configured policy:
 * - LOCAL_ONLY: Only use on-device inference (offline mode)
 * - REMOTE_ONLY: Only use gateway-based inference
 * - AUTO: Try remote first, fall back to local
 */
class InferenceRouter(
    private val localProvider: LocalInferenceProvider,
    private val remoteProvider: RemoteInferenceProvider?
) {
    companion object {
        private const val TAG = "InferenceRouter"
    }

    enum class RoutingPolicy {
        LOCAL_ONLY,
        REMOTE_ONLY,
        AUTO
    }

    var policy: RoutingPolicy = if (remoteProvider != null) RoutingPolicy.AUTO else RoutingPolicy.LOCAL_ONLY
        private set

    fun setPolicy(newPolicy: RoutingPolicy) {
        if (newPolicy == RoutingPolicy.REMOTE_ONLY && remoteProvider == null) {
            Log.w(TAG, "Cannot set REMOTE_ONLY without remote provider, using AUTO")
            policy = RoutingPolicy.AUTO
            return
        }
        policy = newPolicy
        Log.i(TAG, "Routing policy set to: $newPolicy")
    }

    /**
     * Route an inference request based on policy
     */
    suspend fun infer(prompt: String, options: InferenceOptions = InferenceOptions()): InferenceResult {
        return when (policy) {
            RoutingPolicy.LOCAL_ONLY -> inferLocal(prompt, options)
            RoutingPolicy.REMOTE_ONLY -> inferRemote(prompt, options)
            RoutingPolicy.AUTO -> inferAuto(prompt, options)
        }
    }

    /**
     * Check if any provider is available
     */
    suspend fun isAvailable(): Boolean {
        return when (policy) {
            RoutingPolicy.LOCAL_ONLY -> localProvider.isAvailable()
            RoutingPolicy.REMOTE_ONLY -> remoteProvider?.isAvailable() ?: false
            RoutingPolicy.AUTO -> localProvider.isAvailable() || remoteProvider?.isAvailable() == true
        }
    }

    /**
     * Get the currently active provider
     */
    suspend fun getActiveProvider(): InferenceProvider? {
        return when (policy) {
            RoutingPolicy.LOCAL_ONLY -> localProvider.takeIf { it.isAvailable() }
            RoutingPolicy.REMOTE_ONLY -> remoteProvider?.takeIf { it.isAvailable() }
            RoutingPolicy.AUTO -> {
                if (remoteProvider?.isAvailable() == true) remoteProvider
                else if (localProvider.isAvailable()) localProvider
                else null
            }
        }
    }

    private suspend fun inferLocal(prompt: String, options: InferenceOptions): InferenceResult {
        if (!localProvider.isAvailable()) {
            return InferenceResult.error("local", "Local model not loaded")
        }
        return localProvider.infer(prompt, options)
    }

    private suspend fun inferRemote(prompt: String, options: InferenceOptions): InferenceResult {
        if (remoteProvider == null || !remoteProvider.isAvailable()) {
            return InferenceResult.error("remote", "Gateway not connected")
        }
        return remoteProvider.infer(prompt, options)
    }

    private suspend fun inferAuto(prompt: String, options: InferenceOptions): InferenceResult {
        // Try remote first if available (more capable models)
        if (remoteProvider?.isAvailable() == true) {
            val result = remoteProvider.infer(prompt, options)
            if (result.success) return result
            Log.w(TAG, "Remote inference failed, falling back to local: ${result.error}")
        }

        // Fall back to local
        if (localProvider.isAvailable()) {
            return localProvider.infer(prompt, options)
        }

        return InferenceResult.error("router", "No inference provider available")
    }
}
