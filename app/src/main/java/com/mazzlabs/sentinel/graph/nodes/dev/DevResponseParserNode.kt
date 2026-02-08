package com.mazzlabs.sentinel.graph.nodes.dev

import com.mazzlabs.sentinel.graph.state.DevFileChange
import com.mazzlabs.sentinel.graph.state.DevTestResult
import com.mazzlabs.sentinel.graph.state.FileAction

/**
 * DevResponseParser - Port of colabPro/src/utils/response-parser.ts
 *
 * Common parsing functions for extracting structured data from agent responses.
 * Used by EngineerNode, FixerNode, and the DevResponseParserNode.
 */
object DevResponseParser {

    /**
     * Parse file changes from an agent response.
     * Looks for FILES_CHANGED section and tool-use patterns.
     */
    fun parseFileChanges(response: String): List<DevFileChange> {
        val changes = mutableListOf<DevFileChange>()

        // Look for FILES_CHANGED section
        val filesMatch = Regex(
            "FILES_CHANGED:\\s*([\\s\\S]*?)(?:\\n\\n|TEST_RESULTS:|$)",
            RegexOption.IGNORE_CASE
        ).find(response)

        if (filesMatch != null) {
            for (line in filesMatch.groupValues[1].lines()) {
                val match = Regex(
                    """-\s*\[(create|modify|delete)]\s*(.+)""",
                    RegexOption.IGNORE_CASE
                ).find(line)
                if (match != null) {
                    changes.add(DevFileChange(
                        path = match.groupValues[2].trim(),
                        action = when (match.groupValues[1].lowercase()) {
                            "create" -> FileAction.CREATE
                            "delete" -> FileAction.DELETE
                            else -> FileAction.MODIFY
                        }
                    ))
                }
            }
        }

        // Also detect file writes from tool-use patterns
        val writeMatches = Regex(
            """(?:writ(?:e|ing|ten)|creat(?:e|ing|ed))\s+(?:file\s+)?[`"]?([^\s`"]+\.[a-z]+)[`"]?""",
            RegexOption.IGNORE_CASE
        ).findAll(response)

        for (match in writeMatches) {
            val path = match.groupValues[1]
            if (path.isNotBlank() && changes.none { it.path == path }) {
                changes.add(DevFileChange(path = path, action = FileAction.CREATE))
            }
        }

        return changes
    }

    /**
     * Parse test results from an agent response.
     */
    fun parseTestResults(response: String): DevTestResult? {
        // Look for TEST_RESULTS section
        val testMatch = Regex(
            "TEST_RESULTS:\\s*([\\s\\S]*?)(?:\\n\\n|$)",
            RegexOption.IGNORE_CASE
        ).find(response)

        if (testMatch != null) {
            val section = testMatch.groupValues[1]
            val passed = Regex("passed:\\s*true", RegexOption.IGNORE_CASE).containsMatchIn(section)

            val outputMatch = Regex("output:\\s*(.+)", RegexOption.IGNORE_CASE).find(section)
            val output = outputMatch?.groupValues?.get(1) ?: if (passed) "Tests passed" else "Tests failed"

            val errors = Regex("(?:error|failure|failed):\\s*(.+)", RegexOption.IGNORE_CASE)
                .findAll(section).map { it.groupValues[1] }.toList()

            return DevTestResult(passed = passed, output = output, errors = errors)
        }

        // Heuristic fallback
        if (Regex("(?:tests?\\s+(?:now\\s+)?pass(?:ed)?|all\\s+(?:tests?\\s+)?pass|✓.*passed)", RegexOption.IGNORE_CASE).containsMatchIn(response)) {
            return DevTestResult(passed = true, output = "Tests passed")
        }
        if (Regex("(?:still\\s+fail|tests?\\s+fail(?:ed)?|FAILED|✗|error:)", RegexOption.IGNORE_CASE).containsMatchIn(response)) {
            val errors = Regex("(?:error|failure|failed):\\s*(.+)", RegexOption.IGNORE_CASE)
                .findAll(response).map { it.groupValues[1] }.toList()
            return DevTestResult(
                passed = false,
                output = errors.joinToString("\n").ifEmpty { "Tests failed" },
                errors = errors
            )
        }

        return null
    }

    /**
     * Parse a fix description from an agent response.
     */
    fun parseFixDescription(response: String): String {
        val match = Regex(
            "FIX_DESCRIPTION:\\s*(.+?)(?:\\n\\n|FILES_CHANGED:|$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(response)
        if (match != null) return match.groupValues[1].trim()

        val explainMatch = Regex(
            "(?:fixed|resolved|corrected|changed|the\\s+(?:issue|problem|bug)\\s+was)[\\s:]+(.+?)(?:\\.|$)",
            RegexOption.IGNORE_CASE
        ).find(response)
        if (explainMatch != null) return explainMatch.groupValues[1].trim()

        return "Applied fixes"
    }
}
