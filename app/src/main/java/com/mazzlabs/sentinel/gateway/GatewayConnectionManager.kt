package com.mazzlabs.sentinel.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GatewayConnectionManager - Lifecycle, reconnect, health checks
 *
 * Manages the WebSocket connection lifecycle with:
 * - Network connectivity monitoring
 * - Automatic reconnection on network changes
 * - Periodic health checks
 * - Connection state tracking
 */
class GatewayConnectionManager(
    private val context: Context,
    private val client: OpenClawGatewayClient
) {
    companion object {
        private const val TAG = "GatewayConnMgr"
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Check if currently connected to the gateway
     */
    fun isConnected(): Boolean = state.value == ConnectionState.CONNECTED
    private var healthCheckJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = GatewayConfig.Defaults.MAX_RECONNECT_ATTEMPTS

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available")
            if (_state.value == ConnectionState.DISCONNECTED ||
                _state.value == ConnectionState.ERROR) {
                scope.launch { attemptConnect() }
            }
        }

        override fun onLost(network: Network) {
            Log.w(TAG, "Network lost")
            _state.value = ConnectionState.DISCONNECTED
            stopHealthChecks()
        }
    }

    init {
        // Listen for gateway events
        scope.launch {
            client.eventBus.events.collect { event ->
                when (event) {
                    is GatewayEvent.Connected -> {
                        _state.value = ConnectionState.CONNECTED
                        reconnectAttempts = 0
                        _lastError.value = null
                        startHealthChecks()
                    }
                    is GatewayEvent.Disconnected -> {
                        _state.value = ConnectionState.DISCONNECTED
                        stopHealthChecks()
                    }
                    is GatewayEvent.Reconnecting -> {
                        _state.value = ConnectionState.RECONNECTING
                    }
                    is GatewayEvent.Reconnected -> {
                        _state.value = ConnectionState.CONNECTED
                        reconnectAttempts = 0
                        startHealthChecks()
                    }
                    is GatewayEvent.Error -> {
                        _lastError.value = event.throwable.message
                    }
                    else -> { /* handled elsewhere */ }
                }
            }
        }
    }

    /**
     * Start managing the connection
     */
    fun start() {
        registerNetworkCallback()
        scope.launch { attemptConnect() }
    }

    /**
     * Stop managing the connection
     */
    fun stop() {
        stopHealthChecks()
        unregisterNetworkCallback()
        client.disconnect()
        _state.value = ConnectionState.DISCONNECTED
    }

    /**
     * Disconnect from the gateway
     */
    fun disconnect() {
        client.disconnect()
        _state.value = ConnectionState.DISCONNECTED
    }

    /**
     * Connect to the gateway
     */
    suspend fun connect() {
        attemptConnect()
    }

    /**
     * Attempt to connect to the gateway (internal)
     */
    private suspend fun attemptConnect() {
        if (_state.value == ConnectionState.CONNECTED || _state.value == ConnectionState.CONNECTING) {
            return
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network available")
            _state.value = ConnectionState.DISCONNECTED
            return
        }

        _state.value = ConnectionState.CONNECTING

        try {
            client.connect()
            // State will be updated via event bus
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _lastError.value = e.message
            reconnectAttempts++

            if (reconnectAttempts < maxReconnectAttempts) {
                _state.value = ConnectionState.RECONNECTING
                val delay = GatewayConfig.Defaults.RECONNECT_DELAY_MS * reconnectAttempts
                scope.launch {
                    delay(delay)
                    attemptConnect()
                }
            } else {
                _state.value = ConnectionState.ERROR
                Log.e(TAG, "Max reconnect attempts reached")
            }
        }
    }

    private fun startHealthChecks() {
        stopHealthChecks()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(GatewayConfig.Defaults.HEALTH_CHECK_INTERVAL_MS)
                try {
                    if (client.isConnected()) {
                        client.getHealth()
                        Log.d(TAG, "Health check OK")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Health check failed", e)
                }
            }
        }
    }

    private fun stopHealthChecks() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
