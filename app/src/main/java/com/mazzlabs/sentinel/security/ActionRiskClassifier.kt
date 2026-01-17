package com.mazzlabs.sentinel.security

import android.util.Log
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.core.GrammarManager
import com.mazzlabs.sentinel.core.JsonExtractor
import com.mazzlabs.sentinel.model.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ActionRiskClassifier - Lightweight semantic classifier for action risk.
 * Uses the existing on-device model with a constrained grammar to reduce
 * false positives from keyword-only firewall checks.
 */
class ActionRiskClassifier {

    companion object {
        private const val TAG = "ActionRiskClassifier"
        private const val GRAMMAR_ASSET = "risk.gbnf"
    }

    data class RiskAssessment(
        val dangerous: Boolean,
        val confidence: Float,
        val reason: String? = null,
        val raw: String? = null
    )

    private val nativeBridge = SentinelApplication.getInstance().nativeBridge
    private val grammarPath: String by lazy { GrammarManager.getGrammarPath(GRAMMAR_ASSET) }

    suspend fun assess(
        action: AgentAction,
        screenContext: String,
        packageName: String
    ): RiskAssessment? = withContext(Dispatchers.IO) {
        if (!nativeBridge.isModelReady()) {
            Log.w(TAG, "Model not ready; skipping semantic risk classification")
            return@withContext null
        }

        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(action, screenContext, packageName)

        val response = try {
            nativeBridge.inferWithGrammar(userPrompt, systemPrompt, grammarPath)
        } catch (e: Exception) {
            Log.e(TAG, "Risk classification failed", e)
            return@withContext null
        }

        parseAssessment(response)
    }

    private fun buildSystemPrompt(): String {
        return """
            You are a security classifier for an Android agent.
            Determine if executing the action is dangerous in context.
            Return JSON with fields: dangerous (true|false), confidence (0-1), reason (optional).
            Respond ONLY with JSON.
        """.trimIndent()
    }

    private fun buildUserPrompt(action: AgentAction, screenContext: String, packageName: String): String {
        val context = screenContext.take(2000)
        return buildString {
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
