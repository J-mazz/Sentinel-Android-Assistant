package com.mazzlabs.sentinel.core

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * JsonExtractor - Robust JSON extraction from LLM output
 *
 * Strategies (in order of preference):
 * 1. Direct parse (output is pure JSON)
 * 2. Markdown code block extraction
 * 3. Balanced brace extraction with validation
 * 4. Repair and retry
 */
object JsonExtractor {

    private const val TAG = "JsonExtractor"

    sealed class ExtractionResult {
        data class Success(val json: JSONObject, val strategy: String) : ExtractionResult()
        data class ArraySuccess(val json: JSONArray, val strategy: String) : ExtractionResult()
        data class Failure(val error: String, val attempts: List<String>) : ExtractionResult()
    }

    fun extract(text: String): ExtractionResult {
        val attempts = mutableListOf<String>()
        val trimmed = text.trim()

        tryDirectParse(trimmed)?.let {
            return it
        }
        attempts.add("direct_parse_failed")

        tryMarkdownExtraction(trimmed)?.let {
            return it
        }
        attempts.add("markdown_extraction_failed")

        tryBalancedExtraction(trimmed)?.let {
            return it
        }
        attempts.add("balanced_extraction_failed")

        tryRepairAndParse(trimmed)?.let {
            return it
        }
        attempts.add("repair_failed")

        Log.w(TAG, "All extraction strategies failed for: ${trimmed.take(100)}...")
        return ExtractionResult.Failure(
            "Could not extract valid JSON",
            attempts
        )
    }

    private fun tryDirectParse(text: String): ExtractionResult? {
        return try {
            when {
                text.startsWith("{") && text.endsWith("}") -> {
                    ExtractionResult.Success(JSONObject(text), "direct")
                }
                text.startsWith("[") && text.endsWith("]") -> {
                    ExtractionResult.ArraySuccess(JSONArray(text), "direct")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tryMarkdownExtraction(text: String): ExtractionResult? {
        val patterns = listOf(
            Regex("```json\\s*([\\s\\S]*?)\\s*```"),
            Regex("```\\s*([\\s\\S]*?)\\s*```"),
            Regex("`([^`]+)`")
        )

        for (pattern in patterns) {
            pattern.find(text)?.let { match ->
                val content = match.groupValues[1].trim()
                try {
                    return when {
                        content.startsWith("{") -> ExtractionResult.Success(JSONObject(content), "markdown")
                        content.startsWith("[") -> ExtractionResult.ArraySuccess(JSONArray(content), "markdown")
                        else -> null
                    }
                } catch (e: Exception) {
                    // continue
                }
            }
        }
        return null
    }

    private fun tryBalancedExtraction(text: String): ExtractionResult? {
        val objStart = text.indexOf('{')
        val arrStart = text.indexOf('[')

        val start = when {
            objStart == -1 -> arrStart
            arrStart == -1 -> objStart
            else -> minOf(objStart, arrStart)
        }
        if (start == -1) return null

        val isArray = text[start] == '['
        var depth = 0
        var inString = false
        var escape = false

        for (i in start until text.length) {
            val c = text[i]

            if (escape) {
                escape = false
                continue
            }

            when {
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && (c == '{' || c == '[') -> depth++
                !inString && (c == '}' || c == ']') -> {
                    depth--
                    if (depth == 0) {
                        val candidate = text.substring(start, i + 1)
                        return try {
                            if (isArray) {
                                ExtractionResult.ArraySuccess(JSONArray(candidate), "balanced")
                            } else {
                                ExtractionResult.Success(JSONObject(candidate), "balanced")
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }

    private fun tryRepairAndParse(text: String): ExtractionResult? {
        var repaired = text

        val objStart = repaired.indexOf('{')
        val arrStart = repaired.indexOf('[')

        val start = when {
            objStart == -1 -> arrStart
            arrStart == -1 -> objStart
            else -> minOf(objStart, arrStart)
        }

        val end = when (start) {
            -1 -> -1
            objStart -> repaired.lastIndexOf('}')
            else -> repaired.lastIndexOf(']')
        }

        if (start == -1 || end <= start) return null

        repaired = repaired.substring(start, end + 1)

        // Repair: trailing commas
        repaired = repaired.replace(Regex(",\\s*}"), "}")
        repaired = repaired.replace(Regex(",\\s*]"), "]")

        // Repair: single quotes to double quotes (risky but common)
        if (!repaired.contains('"') && repaired.contains("'")) {
            repaired = repaired.replace("'", "\"")
        }

        // Repair: unquoted keys
        repaired = repaired.replace(Regex("(\\{|,)\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:")) {
            "${it.groupValues[1]}\"${it.groupValues[2]}\":"
        }

        return try {
            if (repaired.startsWith("[")) {
                ExtractionResult.ArraySuccess(JSONArray(repaired), "repaired")
            } else {
                ExtractionResult.Success(JSONObject(repaired), "repaired")
            }
        } catch (e: Exception) {
            null
        }
    }
}
