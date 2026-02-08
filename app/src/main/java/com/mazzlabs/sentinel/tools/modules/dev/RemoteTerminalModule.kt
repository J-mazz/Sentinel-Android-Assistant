package com.mazzlabs.sentinel.tools.modules.dev

import android.content.Context
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.tools.framework.*

/**
 * RemoteTerminalModule - Execute commands, run tests, and lint on dev machine via gateway
 */
class RemoteTerminalModule(
    private val gatewayClient: OpenClawGatewayClient
) : ToolModule {

    override val moduleId = "remote_terminal"
    override val description = "Execute commands, run tests, and run lint on the remote development machine"

    override val operations = listOf(
        ToolOperation(
            operationId = "exec",
            description = "Execute a shell command on the remote machine",
            parameters = listOf(
                ToolParameter("command", ParameterType.STRING, "Shell command to execute", required = true),
                ToolParameter("workdir", ParameterType.STRING, "Working directory", required = false),
                ToolParameter("timeout", ParameterType.INTEGER, "Timeout in milliseconds", required = false, default = 30000)
            )
        ),
        ToolOperation(
            operationId = "run_tests",
            description = "Run project tests on the remote machine",
            parameters = listOf(
                ToolParameter("test_path", ParameterType.STRING, "Specific test file or directory", required = false),
                ToolParameter("verbose", ParameterType.BOOLEAN, "Enable verbose output", required = false, default = true)
            )
        ),
        ToolOperation(
            operationId = "run_lint",
            description = "Run linting on the remote project",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "Path to lint", required = false),
                ToolParameter("fix", ParameterType.BOOLEAN, "Auto-fix issues", required = false, default = false)
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
                "exec" -> {
                    val command = params["command"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'command'")
                    val workdir = params["workdir"] as? String
                    val timeout = (params["timeout"] as? Number)?.toInt()

                    val result = gatewayClient.exec(command, workdir = workdir, timeout = timeout)
                    if (result.ok) {
                        ToolResponse.Success(moduleId, operationId, "Command executed (exit: ${result.exitCode})",
                            mapOf(
                                "stdout" to (result.stdout ?: ""),
                                "stderr" to (result.stderr ?: ""),
                                "exitCode" to (result.exitCode ?: -1)
                            ))
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR,
                            "Command failed: ${result.stderr ?: "Unknown error"}")
                    }
                }
                "run_tests" -> {
                    val testPath = params["test_path"] as? String ?: ""
                    val verbose = params["verbose"] as? Boolean ?: true
                    val command = buildTestCommand(testPath, verbose)

                    val result = gatewayClient.exec(command)
                    if (result.ok) {
                        ToolResponse.Success(moduleId, operationId,
                            if (result.exitCode == 0) "Tests passed" else "Tests failed (exit: ${result.exitCode})",
                            mapOf(
                                "stdout" to (result.stdout ?: ""),
                                "stderr" to (result.stderr ?: ""),
                                "passed" to (result.exitCode == 0)
                            ))
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR,
                            "Test execution failed: ${result.stderr}")
                    }
                }
                "run_lint" -> {
                    val path = params["path"] as? String ?: "."
                    val fix = params["fix"] as? Boolean ?: false
                    val command = buildLintCommand(path, fix)

                    val result = gatewayClient.exec(command)
                    if (result.ok) {
                        ToolResponse.Success(moduleId, operationId, "Lint completed",
                            mapOf(
                                "output" to (result.stdout ?: ""),
                                "errors" to (result.stderr ?: ""),
                                "clean" to (result.exitCode == 0)
                            ))
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR,
                            "Lint failed: ${result.stderr}")
                    }
                }
                else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
            }
        } catch (e: Exception) {
            ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, "Error: ${e.message}")
        }
    }

    private fun buildTestCommand(testPath: String, verbose: Boolean): String {
        // Auto-detect test runner based on common patterns
        val verboseFlag = if (verbose) "--verbose" else ""
        return if (testPath.isNotBlank()) {
            "npx vitest run $testPath $verboseFlag 2>&1 || pytest $testPath -v 2>&1 || go test $testPath $verboseFlag 2>&1"
        } else {
            "npm test 2>&1 || pytest -v 2>&1 || go test ./... 2>&1"
        }
    }

    private fun buildLintCommand(path: String, fix: Boolean): String {
        val fixFlag = if (fix) "--fix" else ""
        return "npx eslint $path $fixFlag 2>&1 || flake8 $path 2>&1 || golangci-lint run $path 2>&1"
    }
}
