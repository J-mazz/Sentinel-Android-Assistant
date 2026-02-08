package com.mazzlabs.sentinel.inference

/**
 * InferenceProvider - Interface for inference
 *
 * Abstracts the inference source so the agent graph doesn't need to know
 * implementation details of the OpenClaw gateway connection.
 */
interface InferenceProvider {

    /** Provider identifier */
    val providerId: String

    /** Whether this provider is currently available */
    suspend fun isAvailable(): Boolean

    /**
     * Run inference with the given prompt
     *
     * @param prompt The full prompt (system + user + context)
     * @param options Optional inference parameters
     * @return The model's response text
     */
    suspend fun infer(prompt: String, options: InferenceOptions = InferenceOptions()): InferenceResult
}

/**
 * Options for inference requests
 */
data class InferenceOptions(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val model: String? = null,
    val sessionKey: String? = null,
    val systemPrompt: String? = null,
    val timeoutMs: Long = 60000L
)

/**
 * Result from an inference call
 */
data class InferenceResult(
    val text: String,
    val tokensUsed: Int = 0,
    val provider: String,
    val success: Boolean = true,
    val error: String? = null
) {
    companion object {
        fun error(provider: String, message: String) = InferenceResult(
            text = "",
            provider = provider,
            success = false,
            error = message
        )
    }
}
