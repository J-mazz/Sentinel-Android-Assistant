package com.mazzlabs.sentinel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mazzlabs.sentinel.R
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.gateway.GatewayConnectionManager
import com.mazzlabs.sentinel.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * GatewayService - Foreground service managing the WebSocket gateway lifecycle.
 *
 * Responsibilities:
 * 1. Maintain persistent WebSocket connection to OpenClaw gateway
 * 2. Show ongoing notification with connection status
 * 3. Reconnect on network change
 * 4. Register/unregister dev tools based on connection state
 */
class GatewayService : Service() {

    companion object {
        private const val TAG = "GatewayService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "sentinel_gateway_channel"

        const val ACTION_START = "com.mazzlabs.sentinel.gateway.START"
        const val ACTION_STOP = "com.mazzlabs.sentinel.gateway.STOP"

        fun start(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var connectionManager: GatewayConnectionManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network available, ensuring gateway connection")
            connectionManager?.let { manager ->
                serviceScope.launch {
                    if (!manager.isConnected()) {
                        manager.connect()
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            Log.w(TAG, "Network lost")
            updateNotification("Disconnected - no network")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GatewayService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stopping gateway service")
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.i(TAG, "Starting gateway service")
                val notification = createNotification("Connecting...")
                startForeground(NOTIFICATION_ID, notification)
                startGatewayConnection()
                registerNetworkCallback()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterNetworkCallback()
        
        // Disconnect synchronously before cancelling the scope
        connectionManager?.disconnect()

        // Unregister dev tools
        val app = application as? SentinelApplication
        app?.gatewayClient?.let {
            // TODO: Validate this reference with the actual Tools class implementation
            com.mazzlabs.sentinel.tools.framework.Tools.getInstance(this).unregisterDevTools()
        }

        serviceScope.cancel()
        Log.i(TAG, "GatewayService destroyed")
        super.onDestroy()
    }

    private fun startGatewayConnection() {
        val app = application as? SentinelApplication ?: return
        val manager = app.gatewayConnectionManager ?: return

        connectionManager = manager

        // Observe connection state and update notification
        serviceScope.launch {
            manager.state.collectLatest { state ->
                val statusText = when (state) {
                    GatewayConnectionManager.ConnectionState.DISCONNECTED -> "Disconnected"
                    GatewayConnectionManager.ConnectionState.CONNECTING -> "Connecting..."
                    GatewayConnectionManager.ConnectionState.CONNECTED -> "Connected"
                    GatewayConnectionManager.ConnectionState.RECONNECTING -> "Reconnecting..."
                    GatewayConnectionManager.ConnectionState.ERROR -> "Connection error"
                }
                updateNotification(statusText)

                // Register/unregister dev tools based on connection
                if (state == GatewayConnectionManager.ConnectionState.CONNECTED) {
                    app.gatewayClient?.let { client ->
                        com.mazzlabs.sentinel.tools.framework.Tools.getInstance(this@GatewayService)
                            .registerDevTools(client)
                    }
                }
            }
        }

        // Initiate connection
        serviceScope.launch {
            manager.connect()
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gateway Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the status of the remote gateway connection"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sentinel Gateway")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val notification = createNotification(statusText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
