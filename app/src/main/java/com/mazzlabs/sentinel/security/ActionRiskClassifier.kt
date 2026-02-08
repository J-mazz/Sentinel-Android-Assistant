package com.mazzlabs.sentinel.security

import android.util.Log
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.inference.InferenceOptions
import com.mazzlabs.sentinel.core.JsonExtractor
import com.mazzlabs.sentinel.model.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ActionRiskClassifier - Lightweight semantic classifier for action risk.
 * Uses the OpenClaw gateway to analyze actions for potential security risks,
 * reducing false positives from keyword-only firewall checks.
 */
class ActionRiskClassifier {

    companion object {
        private const val TAG = "ActionRiskClassifier"
    }

    data class RiskAssessment(
        val dangerous: Boolean,
        val confidence: Float,
        val reason: String? = null,
        val raw: String? = null
    )

    private val inferenceRouter = SentinelApplication.getInstance().inferenceRouter

    suspend fun assess(
        action: AgentAction,
        screenContext: String,
        packageName: String
    ): RiskAssessment? = withContext(Dispatchers.IO) {
        if (inferenceRouter == null || !inferenceRouter.isAvailable()) {
            Log.w(TAG, "Gateway not connected; skipping semantic risk classification")
            return@withContext null
        }

        val prompt = buildPrompt(action, screenContext, packageName)

        val response = try {
            inferenceRouter.infer(
                prompt = prompt,
                options = InferenceOptions(temperature = 0.3f, maxTokens = 256)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Risk classification failed", e)
            return@withContext null
        }

        if (!response.success) {
            Log.w(TAG, "Risk classification inference failed: ${response.error}")
            return@withContext null
        }

        parseAssessment(response.text)
    }

    private fun buildPrompt(action: AgentAction, screenContext: String, packageName: String): String {
        val context = screenContext.take(2000)
        return buildString {
            appendLine("You are a security classifier for an Android agent.")
            appendLine("Determine if executing this action is dangerous in the given context.")
            appendLine("Return JSON with fields: dangerous (true|false), confidence (0-1), reason (optional).")
            appendLine("Respond ONLY with JSON.")
            appendLine()
            appendLine("Package: $packageName")
            appendLine("Action JSON:")
            appendLine(action.toJson())
            appendLine()
            appendLine("Screen context:")
            appendLine(context)
        }
    }

    private fun parseAssessment(response: String): RiskAssessment? {
        val extraction = JsonExtractor.extract(response)
        val json = when (extraction) {
            is JsonExtractor.ExtractionResult.Success -> extraction.json
            is JsonExtractor.ExtractionResult.ArraySuccess -> {
                if (extraction.json.length() > 0) extraction.json.getJSONObject(0) else null
            }
            is JsonExtractor.ExtractionResult.Failure -> null
        } ?: run {
            Log.w(TAG, "Risk classifier JSON extraction failed: $response")
            return null
        }

        val dangerous = json.optBoolean("dangerous", true)
        val confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
        val reason = json.optString("reason", null)?.takeIf { it.isNotBlank() }

        return RiskAssessment(
            dangerous = dangerous,
            confidence = confidence,
            reason = reason,
            raw = response
        )
    }
}
