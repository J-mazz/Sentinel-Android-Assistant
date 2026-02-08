package com.mazzlabs.sentinel.inference

import android.util.Log
import com.mazzlabs.sentinel.core.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LocalInferenceProvider - Wraps existing NativeBridge for on-device inference
 *
 * Uses llama.cpp via JNI for fast, private, offline inference with Jamba-3B.
 */
class LocalInferenceProvider(
    private val nativeBridge: NativeBridge
) : InferenceProvider {

    companion object {
        private const val TAG = "LocalInference"
    }

    override val providerId: String = "local"

    override suspend fun isAvailable(): Boolean {
        return try {
            nativeBridge.isModelReady()
        } catch (e: Exception) {
            Log.w(TAG, "Model readiness check failed", e)
            false
        }
    }

    override suspend fun infer(
        prompt: String,
        options: InferenceOptions
    ): InferenceResult = withContext(Dispatchers.IO) {
        try {
            // Set inference parameters if different from defaults
            nativeBridge.setInferenceParams(
                options.temperature,
                options.topP,
                options.maxTokens
            )

            val response = if (options.grammarPath != null) {
                nativeBridge.inferWithGrammar(prompt, "", options.grammarPath)
            } else {
                nativeBridge.inferWithoutGrammar(prompt, "")
            }

            InferenceResult(
                text = response,
                provider = providerId,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Local inference failed", e)
            InferenceResult.error(providerId, e.message ?: "Local inference failed")
        }
    }
}
