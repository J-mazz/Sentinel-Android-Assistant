package com.mazzlabs.sentinel.core

import android.util.Log

/**
 * NativeBridge - JNI Wrapper for llama.cpp Inference Engine
 * 
 * Provides a secure bridge between Kotlin and the C++ inference layer.
 * All input sanitization and grammar enforcement happens in native code.
 */
class NativeBridge {

    companion object {
        private const val TAG = "NativeBridge"
        
        init {
            try {
                System.loadLibrary("sentinel_native")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw RuntimeException("Failed to load sentinel_native library", e)
            }
        }
    }

    /**
     * Initialize the Jamba model with GBNF grammar constraint
     * 
     * @param modelPath Absolute path to the .gguf model file
     * @param grammarPath Absolute path to the .gbnf grammar file
     * @return true if initialization successful
     */
    external fun initModel(modelPath: String, grammarPath: String): Boolean

    /**
     * Run inference with the loaded model
     * Input is sanitized and output is constrained by GBNF grammar in native code
     * 
     * @param userQuery The user's query/command
     * @param screenContext Flattened UI tree context
     * @return JSON string of the action to perform (grammar-constrained)
     */
    external fun infer(userQuery: String, screenContext: String): String

    /**
     * Release model resources and free memory
     */
    external fun releaseModel()

    /**
     * Get current model status
     * @return true if model is loaded and ready
     */
    external fun isModelReady(): Boolean

    /**
     * Get model metadata (name, context size, etc.)
     * @return JSON string with model information
     */
    external fun getModelInfo(): String

    /**
     * Set inference parameters
     * @param temperature Sampling temperature (0.0-2.0)
     * @param topP Top-p sampling (0.0-1.0)
     * @param maxTokens Maximum tokens to generate
     */
    external fun setInferenceParams(temperature: Float, topP: Float, maxTokens: Int)
}
