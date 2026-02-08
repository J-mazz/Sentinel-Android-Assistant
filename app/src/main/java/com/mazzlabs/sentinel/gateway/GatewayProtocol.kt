package com.mazzlabs.sentinel.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenClaw Gateway Protocol Data Classes
 *
 * Port of TypeScript interfaces from openclaw-client.ts:16-93
 * JSON-RPC style WebSocket protocol v3
 */

@Serializable
data class GatewayHelloOk(
    val protocolVersion: Int,
    val gatewayVersion: String,
    val instanceId: String,
    val hostname: String
)

@Serializable
data class GatewayEventFrame(
    val type: String = "event",
    val event: String,
    val payload: JsonElement? = null,
    val sessionKey: String? = null
)

@Serializable
data class GatewayRequestFrame(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class GatewayResponseFrame(
    val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: JsonElement? = null,
    val error: GatewayError? = null
)

@Serializable
data class GatewayError(
    val code: Int,
    val message: String
)

@Serializable
data class SessionSendParams(
    val sessionKey: String,
    val message: String,
    val idempotencyKey: String? = null
)

@Serializable
data class SessionPatchParams(
    val key: String,
    val model: String? = null,
    val systemPrompt: String? = null,
    val tools: ToolPermissions? = null
)

@Serializable
data class ToolPermissions(
    val allow: List<String>? = null,
    val deny: List<String>? = null
)

@Serializable
data class AgentInfo(
    val name: String,
    val modelId: String,
    val provider: String? = null,
    val tools: List<String>? = null
)

@Serializable
data class AgentRunPayload(
    val status: AgentRunStatus,
    val text: String? = null,
    val toolCalls: List<ToolCallInfo>? = null,
    val toolResults: List<ToolResultInfo>? = null,
    val usage: TokenUsage? = null
)

@Serializable
enum class AgentRunStatus {
    @SerialName("running") RUNNING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED
}

@Serializable
data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class ToolResultInfo(
    val toolCallId: String,
    val output: String,
    val error: String? = null
)

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

/**
 * Connect request parameters (OpenClaw protocol v3)
 * Note: The OpenClaw protocol uses camelCase for JSON field names
 */
@Serializable
data class ConnectParams(
    val minProtocol: Int = 3,
    val maxProtocol: Int = 3,
    val client: ClientInfo,
    val role: String = "operator",
    val scopes: List<String> = listOf("operator.admin"),
    val caps: List<String> = emptyList(),
    val auth: AuthParams? = null
)

@Serializable
data class ClientInfo(
    val id: String = "gateway-client",
    val displayName: String,
    val version: String = "1.0.0",
    val platform: String = "android",
    val mode: String = "backend"
)

@Serializable
data class AuthParams(
    val token: String? = null,
    val password: String? = null
)

/**
 * Gateway events emitted through the event bus
 */
sealed class GatewayEvent {
    data class Connected(val hello: GatewayHelloOk) : GatewayEvent()
    data class Disconnected(val code: Int, val reason: String) : GatewayEvent()
    object Reconnecting : GatewayEvent()
    object Reconnected : GatewayEvent()
    data class EventReceived(val frame: GatewayEventFrame) : GatewayEvent()
    data class ChatUpdate(val payload: AgentRunPayload, val sessionKey: String?) : GatewayEvent()
    data class AgentComplete(val payload: JsonElement, val sessionKey: String) : GatewayEvent()
    data class Error(val throwable: Throwable) : GatewayEvent()
}

/**
 * Tool invocation result from HTTP API
 */
@Serializable
data class ToolInvocationResult(
    val ok: Boolean,
    val result: JsonElement? = null,
    val error: String? = null
)

/**
 * Exec command result
 */
@Serializable
data class ExecResult(
    val ok: Boolean,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null
)

/**
 * File operation result
 */
@Serializable
data class FileResult(
    val ok: Boolean,
    val content: String? = null,
    val files: List<String>? = null,
    val error: String? = null
)
