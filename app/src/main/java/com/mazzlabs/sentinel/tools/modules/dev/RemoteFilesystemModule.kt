package com.mazzlabs.sentinel.tools.modules.dev

import android.content.Context
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.tools.framework.*

/**
 * RemoteFilesystemModule - Read/write/list files on dev machine via gateway
 */
class RemoteFilesystemModule(
    private val gatewayClient: OpenClawGatewayClient
) : ToolModule {

    override val moduleId = "remote_fs"
    override val description = "Read, write, and list files on the remote development machine via OpenClaw gateway"

    override val operations = listOf(
        ToolOperation(
            operationId = "read_file",
            description = "Read the contents of a file on the remote machine",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "Absolute path to the file", required = true)
            )
        ),
        ToolOperation(
            operationId = "write_file",
            description = "Write content to a file on the remote machine",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "Absolute path to the file", required = true),
                ToolParameter("content", ParameterType.STRING, "Content to write", required = true)
            )
        ),
        ToolOperation(
            operationId = "list_files",
            description = "List files in a directory on the remote machine",
            parameters = listOf(
                ToolParameter("path", ParameterType.STRING, "Directory path to list", required = true)
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
                "read_file" -> {
                    val path = params["path"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'path'")
                    val result = gatewayClient.readFile(path)
                    if (result.ok) {
                        ToolResponse.Success(moduleId, operationId, "File read successfully",
                            mapOf("content" to (result.content ?: ""), "path" to path))
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, result.error ?: "Read failed")
                    }
                }
                "write_file" -> {
                    val path = params["path"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'path'")
                    val content = params["content"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'content'")
                    val result = gatewayClient.writeFile(path, content)
                    if (result.ok) {
                        ToolResponse.Success(moduleId, operationId, "File written: $path")
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, result.error ?: "Write failed")
                    }
                }
                "list_files" -> {
                    val path = params["path"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'path'")
                    val result = gatewayClient.listFiles(path)
                    if (result.ok) {
                        ToolResponse.Success(moduleId, operationId, "Listed ${result.files?.size ?: 0} files",
                            mapOf("files" to (result.files ?: emptyList<String>()), "path" to path))
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, result.error ?: "List failed")
                    }
                }
                else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
            }
        } catch (e: Exception) {
            ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, "Error: ${e.message}")
        }
    }
}
