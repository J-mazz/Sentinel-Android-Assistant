package com.mazzlabs.sentinel

import android.app.Application
import android.util.Log
import com.mazzlabs.sentinel.core.NativeBridge

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
