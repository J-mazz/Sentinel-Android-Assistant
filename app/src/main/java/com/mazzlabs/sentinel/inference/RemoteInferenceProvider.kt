package com.mazzlabs.sentinel.inference

import android.util.Log
import com.mazzlabs.sentinel.gateway.GatewayConfig
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.gateway.SessionPatchParams

/**
 * RemoteInferenceProvider - Routes inference through OpenClaw gateway
 *
 * Sends prompts to remote agents via the OpenClaw WebSocket connection.
 * Supports model selection and session configuration.
 */
class RemoteInferenceProvider(
    private val gatewayClient: OpenClawGatewayClient
) : InferenceProvider {

    companion object {
        private const val TAG = "RemoteInference"
    }

    override val providerId: String = "remote"

    override suspend fun isAvailable(): Boolean {
        return gatewayClient.isConnected()
    }

    override suspend fun infer(
        prompt: String,
        options: InferenceOptions
    ): InferenceResult {
        try {
            if (!gatewayClient.isConnected()) {
                return InferenceResult.error(providerId, "Gateway not connected")
            }

            val sessionKey = options.sessionKey ?: GatewayConfig.SessionKeys.ENGINEER
            val model = options.model ?: GatewayConfig.Models.ENGINEER

            // Configure session if model specified
            gatewayClient.patchSession(
                SessionPatchParams(
                    key = sessionKey,
                    model = model,
                    systemPrompt = options.systemPrompt
                )
            )

            // Send message and wait for response
            val result = gatewayClient.sendMessage(
                sessionKey = sessionKey,
                message = prompt
            )

            return InferenceResult(
                text = result.text ?: "",
                tokensUsed = result.usage?.totalTokens ?: 0,
                provider = providerId,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Remote inference failed", e)
            return InferenceResult.error(providerId, e.message ?: "Remote inference failed")
        }
    }
}
