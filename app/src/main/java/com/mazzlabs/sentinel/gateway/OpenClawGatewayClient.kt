package com.mazzlabs.sentinel.gateway

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * OpenClaw Gateway Client
 *
 * Port of colabPro/src/client/openclaw-client.ts
 * Manages WebSocket connection to OpenClaw gateway with automatic reconnection.
 *
 * Key TS→Kotlin mappings:
 * - EventEmitter → GatewayEventBus (SharedFlow)
 * - Promise<T> → suspend fun + CompletableDeferred<T>
 * - Map<string, {resolve, reject}> → ConcurrentHashMap<String, CompletableDeferred<JsonElement>>
 * - setTimeout → CoroutineScope.launch { delay() }
 * - ws library → OkHttpClient.newWebSocket()
 */
class OpenClawGatewayClient(
    private val config: OpenClawClientConfig = OpenClawClientConfig()
) {
    companion object {
        private const val TAG = "OpenClawGateway"
    }

    data class OpenClawClientConfig(
        val url: String = GatewayConfig.Defaults.GATEWAY_URL,
        val token: String = "",
        val password: String = "",
        val clientName: String = GatewayConfig.Defaults.CLIENT_NAME,
        val clientDisplayName: String = GatewayConfig.Defaults.CLIENT_DISPLAY_NAME,
        val reconnect: Boolean = true,
        val reconnectDelayMs: Long = GatewayConfig.Defaults.RECONNECT_DELAY_MS
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(GatewayConfig.Timeouts.CONNECT_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
        .readTimeout(GatewayConfig.Timeouts.READ_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
        .writeTimeout(GatewayConfig.Timeouts.WRITE_TIMEOUT_S.toLong(), TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val requestCounter = AtomicInteger(0)
    private val connected = AtomicBoolean(false)
    private val helloReceived = AtomicBoolean(false)
    private val connectSent = AtomicBoolean(false)
    private var connectNonce: String? = null
    private var reconnectEnabled = config.reconnect
    private var reconnectJob: Job? = null

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonElement?>>()
    private var connectDeferred: CompletableDeferred<GatewayHelloOk>? = null

    val eventBus = GatewayEventBus()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Connect to the OpenClaw gateway
     */
    suspend fun connect(): GatewayHelloOk {
        val deferred = CompletableDeferred<GatewayHelloOk>()
        connectDeferred = deferred

        val request = Request.Builder()
            .url(config.url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                // Queue connect - wait briefly for potential challenge
                scope.launch {
                    delay(100)
                    sendConnect()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val frame = json.parseToJsonElement(text).jsonObject
                    val type = frame["type"]?.jsonPrimitive?.content

                    when (type) {
                        "event" -> {
                            val event = frame["event"]?.jsonPrimitive?.content ?: return
                            // Handle connect.challenge
                            if (event == "connect.challenge") {
                                val payload = frame["payload"]?.jsonObject
                                val nonce = payload?.get("nonce")?.jsonPrimitive?.content
                                if (nonce != null) {
                                    connectNonce = nonce
                                    connectSent.set(false)
                                    scope.launch { sendConnect() }
                                    return
                                }
                            }
                            handleEvent(frame)
                        }
                        "res" -> handleResponse(frame)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                    eventBus.tryEmit(GatewayEvent.Error(e))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                eventBus.tryEmit(GatewayEvent.Error(t))
                if (!helloReceived.get()) {
                    connectDeferred?.completeExceptionally(t)
                    connectDeferred = null
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                helloReceived.set(false)
                connectSent.set(false)
                connectNonce = null

                eventBus.tryEmit(GatewayEvent.Disconnected(code, reason))

                // Reject all pending requests
                for ((id, deferred) in pendingRequests) {
                    deferred.completeExceptionally(
                        GatewayDisconnectedException("Connection closed: $code")
                    )
                    pendingRequests.remove(id)
                }

                // Auto-reconnect if enabled
                if (reconnectEnabled && code != 1000) {
                    scheduleReconnect()
                }
            }
        })

        return deferred.await()
    }

    /**
     * Send the connect request (OpenClaw protocol v3)
     */
    private fun sendConnect() {
        if (connectSent.getAndSet(true) || webSocket == null) return

        val auth = if (config.token.isNotEmpty() || config.password.isNotEmpty()) {
            AuthParams(
                token = config.token.ifEmpty { null },
                password = config.password.ifEmpty { null }
            )
        } else null

        val params = ConnectParams(
            client = ClientInfo(
                displayName = config.clientDisplayName
            ),
            auth = auth
        )

        val id = generateId()
        val frame = buildJsonObject {
            put("type", "req")
            put("id", id)
            put("method", "connect")
            put("params", json.encodeToJsonElement(params))
        }

        // Set up pending handler for connect response
        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[id] = deferred

        scope.launch {
            try {
                val payload = deferred.await()
                helloReceived.set(true)
                val hello = if (payload != null) {
                    json.decodeFromJsonElement<GatewayHelloOk>(payload)
                } else {
                    GatewayHelloOk(3, "unknown", "unknown", "unknown")
                }
                eventBus.emit(GatewayEvent.Connected(hello))
                connectDeferred?.complete(hello)
                connectDeferred = null
            } catch (e: Exception) {
                connectDeferred?.completeExceptionally(e)
                connectDeferred = null
            }
        }

        webSocket?.send(frame.toString())
    }

    private fun generateId(): String = "req-${requestCounter.incrementAndGet()}"

    /**
     * Handle response frames
     */
    private fun handleResponse(frame: JsonObject) {
        val id = frame["id"]?.jsonPrimitive?.content ?: return
        val pending = pendingRequests[id] ?: return
        val ok = frame["ok"]?.jsonPrimitive?.boolean ?: false

        if (ok) {
            pending.complete(frame["payload"])
        } else {
            val errorMsg = frame["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Request failed"
            pending.completeExceptionally(GatewayRequestException(errorMsg))
        }

        pendingRequests.remove(id)
    }

    /**
     * Handle event frames
     */
    private fun handleEvent(frame: JsonObject) {
        val event = frame["event"]?.jsonPrimitive?.content ?: return
        val payload = frame["payload"]
        val sessionKey = frame["sessionKey"]?.jsonPrimitive?.contentOrNull

        val eventFrame = GatewayEventFrame(
            event = event,
            payload = payload,
            sessionKey = sessionKey
        )
        eventBus.tryEmit(GatewayEvent.EventReceived(eventFrame))

        // Handle chat updates
        if (event == "chat" && payload != null) {
            try {
                val runPayload = json.decodeFromJsonElement<AgentRunPayload>(payload)
                eventBus.tryEmit(GatewayEvent.ChatUpdate(runPayload, sessionKey))
            } catch (_: Exception) { /* ignore parse errors for non-chat payloads */ }
        }

        // Handle agent completion events
        if ((event == "agent.final" || event == "agent.error") && sessionKey != null && payload != null) {
            eventBus.tryEmit(GatewayEvent.AgentComplete(payload, sessionKey))
        }
    }

    /**
     * Schedule reconnection
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        eventBus.tryEmit(GatewayEvent.Reconnecting)

        reconnectJob = scope.launch {
            delay(config.reconnectDelayMs)
            try {
                connect()
                eventBus.emit(GatewayEvent.Reconnected)
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect failed", e)
                scheduleReconnect()
            }
        }
    }

    /**
     * Send a request to the gateway
     */
    suspend fun request(
        method: String,
        params: JsonElement? = null,
        timeoutMs: Long = GatewayConfig.Defaults.REQUEST_TIMEOUT_MS
    ): JsonElement? {
        if (!connected.get() || !helloReceived.get()) {
            throw GatewayNotConnectedException("Not connected to gateway")
        }

        val id = generateId()
        val frame = buildJsonObject {
            put("type", "req")
            put("id", id)
            put("method", method)
            if (params != null) put("params", params)
        }

        val deferred = CompletableDeferred<JsonElement?>()
        pendingRequests[id] = deferred

        // Timeout handling
        val timeoutJob = scope.launch {
            delay(timeoutMs)
            if (pendingRequests.remove(id) != null) {
                deferred.completeExceptionally(
                    GatewayTimeoutException("Request timeout: $method")
                )
            }
        }

        webSocket?.send(frame.toString())

        return try {
            deferred.await()
        } finally {
            timeoutJob.cancel()
        }
    }

    /**
     * List configured agents
     */
    suspend fun listAgents(): List<AgentInfo> {
        val result = request("agents.list", buildJsonObject { })
        val agents = result?.jsonObject?.get("agents")
        return if (agents != null) {
            json.decodeFromJsonElement(agents)
        } else {
            emptyList()
        }
    }

    /**
     * Get gateway health status
     */
    suspend fun getHealth(): JsonElement? {
        return request("health", buildJsonObject { })
    }

    /**
     * Get current configuration
     */
    suspend fun getConfig(): JsonElement? {
        return request("config.get", buildJsonObject { })
    }

    /**
     * Patch a session's configuration
     */
    suspend fun patchSession(params: SessionPatchParams) {
        request("sessions.patch", json.encodeToJsonElement(params))
    }

    /**
     * Send a message to an agent session and wait for completion
     */
    suspend fun sendMessage(
        sessionKey: String,
        message: String,
        idempotencyKey: String? = null,
        onUpdate: ((AgentRunPayload) -> Unit)? = null
    ): AgentRunPayload {
        val key = idempotencyKey ?: java.util.UUID.randomUUID().toString()

        // Set up event listener for updates
        // Note: collect() suspends forever, but the job is explicitly cancelled after completion
        val updateJob = if (onUpdate != null) {
            scope.launch {
                eventBus.events.collect { event ->
                    if (event is GatewayEvent.ChatUpdate && event.sessionKey == sessionKey) {
                        onUpdate(event.payload)
                    }
                }
            }
        } else null

        // Send the message
        request("chat.send", buildJsonObject {
            put("sessionKey", sessionKey)
            put("message", message)
            put("idempotencyKey", key)
        })

        // Wait for completion
        val completeEvent = eventBus.awaitAgentComplete(sessionKey, 120000L)

        updateJob?.cancel()

        if (completeEvent != null) {
            return try {
                json.decodeFromJsonElement(completeEvent.payload)
            } catch (_: Exception) {
                AgentRunPayload(status = AgentRunStatus.COMPLETED, text = completeEvent.payload.toString())
            }
        }

        throw GatewayTimeoutException("Agent completion timeout for session: $sessionKey")
    }

    /**
     * Invoke a tool directly via the gateway HTTP API
     */
    suspend fun invokeTool(
        toolName: String,
        args: JsonObject,
        sessionKey: String,
        action: String = "json"
    ): ToolInvocationResult = withContext(Dispatchers.IO) {
        val httpUrl = config.url
            .replace("ws://", "http://")
            .replace("wss://", "https://")

        val body = buildJsonObject {
            put("tool", toolName)
            put("args", args)
            put("sessionKey", sessionKey)
            put("action", action)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url("$httpUrl/tools/invoke")
            .post(requestBody)

        if (config.token.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.token}")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        val responseBody = response.body()?.string() ?: """{"ok":false,"error":"Empty response"}"""

        json.decodeFromString<ToolInvocationResult>(responseBody)
    }

    /**
     * Execute a command via the exec tool
     */
    suspend fun exec(
        command: String,
        sessionKey: String = "main",
        workdir: String? = null,
        timeout: Int? = null,
        host: String = "sandbox"
    ): ExecResult {
        val result = invokeTool(
            "exec",
            buildJsonObject {
                put("command", command)
                workdir?.let { put("workdir", it) }
                timeout?.let { put("timeout", it) }
                put("host", host)
            },
            sessionKey
        )

        return ExecResult(
            ok = result.ok,
            stdout = result.result?.jsonObject?.get("stdout")?.jsonPrimitive?.contentOrNull,
            stderr = result.result?.jsonObject?.get("stderr")?.jsonPrimitive?.contentOrNull,
            exitCode = result.result?.jsonObject?.get("exitCode")?.jsonPrimitive?.intOrNull
        )
    }

    /**
     * Read a file via the gateway
     */
    suspend fun readFile(path: String, sessionKey: String = "main"): FileResult {
        val result = invokeTool(
            "read",
            buildJsonObject { put("file_path", path) },
            sessionKey
        )
        return FileResult(
            ok = result.ok,
            content = result.result?.jsonPrimitive?.contentOrNull,
            error = result.error
        )
    }

    /**
     * Write a file via the gateway
     */
    suspend fun writeFile(path: String, content: String, sessionKey: String = "main"): FileResult {
        val result = invokeTool(
            "write",
            buildJsonObject {
                put("file_path", path)
                put("content", content)
            },
            sessionKey
        )
        return FileResult(ok = result.ok, error = result.error)
    }

    /**
     * List files via the gateway
     */
    suspend fun listFiles(path: String, sessionKey: String = "main"): FileResult {
        val result = invokeTool(
            "read",
            buildJsonObject {
                put("file_path", path)
                put("action", "ls")
            },
            sessionKey,
            action = "ls"
        )
        return FileResult(
            ok = result.ok,
            files = result.result?.jsonArray?.map { it.jsonPrimitive.content },
            error = result.error
        )
    }

    /**
     * Check if connected to gateway
     */
    fun isConnected(): Boolean = connected.get() && helloReceived.get()

    /**
     * Disconnect from the gateway
     */
    fun disconnect() {
        reconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        connected.set(false)
        helloReceived.set(false)
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        disconnect()
        scope.cancel()
    }
}

// Custom exceptions
class GatewayNotConnectedException(message: String) : Exception(message)
class GatewayDisconnectedException(message: String) : Exception(message)
class GatewayTimeoutException(message: String) : Exception(message)
class GatewayRequestException(message: String) : Exception(message)
