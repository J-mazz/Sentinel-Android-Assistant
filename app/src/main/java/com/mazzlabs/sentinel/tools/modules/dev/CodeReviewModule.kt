package com.mazzlabs.sentinel.tools.modules.dev

import android.content.Context
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.tools.framework.*

/**
 * CodeReviewModule - Trigger review workflow via gateway
 */
class CodeReviewModule(
    private val gatewayClient: OpenClawGatewayClient
) : ToolModule {

    override val moduleId = "code_review"
    override val description = "Trigger code review workflow on the remote machine via the architect agent"

    override val operations = listOf(
        ToolOperation(
            operationId = "review_files",
            description = "Request an architect review of specified files",
            parameters = listOf(
                ToolParameter("files", ParameterType.STRING, "Comma-separated list of file paths to review", required = true),
                ToolParameter("focus", ParameterType.STRING, "Review focus area (security, performance, correctness, all)", required = false, default = "all")
            )
        ),
        ToolOperation(
            operationId = "review_diff",
            description = "Request review of git diff (uncommitted changes)",
            parameters = listOf(
                ToolParameter("base", ParameterType.STRING, "Base branch for diff", required = false, default = "main")
            )
        )
    )

    override val requiredPermissions = listOf("android.permission.INTERNET")

    override fun isAvailable(context: Context): Boolean = gatewayClient.isConnected()

    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        if (!gatewayClient.isConnected()) {
            return ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_AVAILABLE, "Gateway not connected")
        }

        return try {
            when (operationId) {
                "review_files" -> {
                    val files = (params["files"] as? String)?.split(",")?.map { it.trim() }
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'files'")
                    val focus = params["focus"] as? String ?: "all"

                    val prompt = buildString {
                        appendLine("Please review the following files for $focus issues:")
                        files.forEach { appendLine("- $it") }
                        appendLine()
                        appendLine("Read each file and provide a detailed review with:")
                        appendLine("- Issues found (with severity and line numbers)")
                        appendLine("- Suggestions for improvement")
                        appendLine("- Overall assessment")
                    }

                    val result = gatewayClient.sendMessage(
                        sessionKey = com.mazzlabs.sentinel.gateway.GatewayConfig.SessionKeys.ARCHITECT,
                        message = prompt
                    )

                    ToolResponse.Success(moduleId, operationId,
                        "Review completed",
                        mapOf("review" to (result.text ?: "No review text"), "files" to files))
                }
                "review_diff" -> {
                    val base = params["base"] as? String ?: "main"

                    // Get the diff first
                    val diffResult = gatewayClient.exec("git diff $base")
                    val diff = diffResult.stdout ?: ""

                    if (diff.isBlank()) {
                        return ToolResponse.Success(moduleId, operationId, "No changes to review")
                    }

                    val prompt = buildString {
                        appendLine("Please review the following git diff:")
                        appendLine("```diff")
                        appendLine(diff.take(10000)) // Limit diff size
                        appendLine("```")
                        appendLine()
                        appendLine("Provide detailed review with issues, suggestions, and overall assessment.")
                    }

                    val result = gatewayClient.sendMessage(
                        sessionKey = com.mazzlabs.sentinel.gateway.GatewayConfig.SessionKeys.ARCHITECT,
                        message = prompt
                    )

                    ToolResponse.Success(moduleId, operationId,
                        "Diff review completed",
                        mapOf("review" to (result.text ?: "No review text")))
                }
                else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation")
            }
        } catch (e: Exception) {
            ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, "Error: ${e.message}")
        }
    }
}
