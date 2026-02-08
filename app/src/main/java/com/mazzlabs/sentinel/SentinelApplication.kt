package com.mazzlabs.sentinel

import android.app.Application
import android.util.Log
import com.mazzlabs.sentinel.core.NativeBridge
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.gateway.GatewayConnectionManager
import com.mazzlabs.sentinel.gateway.security.GatewayAuthManager
import com.mazzlabs.sentinel.inference.*

/**
 * Sentinel Agent Application
 * 
 * Local, Firewall-Protected, Accessibility-Based Android Agent
 * Designed for high-security environments (GrapheneOS)
 */
class SentinelApplication : Application() {

    companion object {
        private const val TAG = "SentinelApp"
        
        @Volatile
        private var instance: SentinelApplication? = null
        
        fun getInstance(): SentinelApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    val nativeBridge: NativeBridge by lazy { NativeBridge() }

    val gatewayAuthManager: GatewayAuthManager by lazy { GatewayAuthManager(this) }

    private var _gatewayClient: OpenClawGatewayClient? = null
    private var _gatewayConnectionManager: GatewayConnectionManager? = null
    private var _inferenceRouter: InferenceRouter? = null

    /**
     * Get or create the gateway client based on current configuration
     */
    fun getOrCreateGatewayClient(): OpenClawGatewayClient? {
        val auth = gatewayAuthManager
        if (!auth.isConfigured() || !auth.isGatewayEnabled) {
            return null
        }

        if (_gatewayClient == null) {
            _gatewayClient = OpenClawGatewayClient(
                OpenClawGatewayClient.OpenClawClientConfig(
                    url = auth.gatewayUrl,
                    token = auth.gatewayToken,
                    password = auth.gatewayPassword
                )
            )
        }
        return _gatewayClient
    }

    val gatewayClient: OpenClawGatewayClient?
        get() = getOrCreateGatewayClient()

    val gatewayConnectionManager: GatewayConnectionManager?
        get() {
            if (_gatewayConnectionManager == null) {
                gatewayClient?.let { client ->
                    _gatewayConnectionManager = GatewayConnectionManager(this, client)
                }
            }
            return _gatewayConnectionManager
        }

    val inferenceRouter: InferenceRouter
        get() {
            if (_inferenceRouter == null) {
                val local = LocalInferenceProvider(nativeBridge)
                val remote = gatewayClient?.let { RemoteInferenceProvider(it) }
                _inferenceRouter = InferenceRouter(local, remote)
            }
            return _inferenceRouter!!
        }

    /**
     * Reinitialize gateway components after settings change
     */
    fun reinitializeGateway() {
        // Clean up existing instances
        _gatewayClient?.destroy()
        _gatewayConnectionManager?.destroy()

        // Clear references to force recreation
        _gatewayClient = null
        _gatewayConnectionManager = null
        _inferenceRouter = null

        Log.i(TAG, "Gateway components reinitialized")
    }

    @Volatile
    var isModelLoaded: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Sentinel Agent Application initialized")
    }

    /**
     * Initialize the native model engine
     * Must be called before any inference operations
     */
    fun initializeModel(
        modelPath: String,
        grammarPath: String,
        onComplete: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val result = nativeBridge.initModel(modelPath, grammarPath)
                isModelLoaded = result
                Log.i(TAG, "Model initialization: ${if (result) "SUCCESS" else "FAILED"}")
                onComplete(result)
            } catch (e: Exception) {
                Log.e(TAG, "Model initialization error", e)
                isModelLoaded = false
                onComplete(false)
            }
        }.start()
    }

    /**
     * Release native resources
     */
    fun releaseModel() {
        try {
            nativeBridge.releaseModel()
            isModelLoaded = false
            Log.i(TAG, "Model resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing model", e)
        }
    }

    override fun onTerminate() {
        releaseModel()
        super.onTerminate()
    }
}
