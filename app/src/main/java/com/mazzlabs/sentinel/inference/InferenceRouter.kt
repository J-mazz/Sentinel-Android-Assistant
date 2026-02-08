package com.mazzlabs.sentinel.inference

import android.util.Log

/**
 * InferenceRouter - Routes all inference through the OpenClaw gateway
 *
 * Simplified router that directs all inference requests to the remote gateway.
 * Keeps the routing abstraction for future extensibility (e.g., model selection,
 * load balancing) but removes the local inference path entirely.
 */
class InferenceRouter(
    private val remoteProvider: RemoteInferenceProvider
) {
    companion object {
        private const val TAG = "InferenceRouter"
    }

    enum class RoutingPolicy {
        /** Use remote gateway (default and only practical option) */
        REMOTE,
        /** Auto mode - currently just uses remote, kept for future extensibility */
        AUTO
    }

    var policy: RoutingPolicy = RoutingPolicy.AUTO
        private set

    fun setPolicy(newPolicy: RoutingPolicy) {
        policy = newPolicy
        Log.i(TAG, "Routing policy set to: $newPolicy")
    }

    /**
     * Route an inference request (always goes to remote)
     */
    suspend fun infer(prompt: String, options: InferenceOptions = InferenceOptions()): InferenceResult {
        if (!remoteProvider.isAvailable()) {
            return InferenceResult.error("remote", "Gateway not connected")
        }
        return remoteProvider.infer(prompt, options)
    }

    /**
     * Check if the gateway is available
     */
    suspend fun isAvailable(): Boolean {
        return remoteProvider.isAvailable()
    }

    /**
     * Get the currently active provider (always remote)
     */
    suspend fun getActiveProvider(): InferenceProvider? {
        return remoteProvider.takeIf { it.isAvailable() }
    }
}
