package com.mazzlabs.sentinel.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * TTSManager - Text-to-Speech for agent feedback
 * 
 * Provides voice feedback to the user about agent actions
 * and confirmation requests.
 */
class TTSManager(private val context: Context) {

    companion object {
        private const val TAG = "TTSManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtterances = mutableListOf<String>()

    interface TTSListener {
        fun onSpeakStart(utteranceId: String)
        fun onSpeakDone(utteranceId: String)
        fun onSpeakError(utteranceId: String, errorCode: Int)
    }

    private var listener: TTSListener? = null

    fun setListener(listener: TTSListener) {
        this.listener = listener
    }

    /**
     * Initialize Text-to-Speech engine
     */
    fun initialize(onReady: (Boolean) -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported")
                    isInitialized = false
                    onReady(false)
                } else {
                    isInitialized = true
                    setupListener()
                    Log.i(TAG, "TTS initialized successfully")
                    
                    // Speak any pending utterances
                    pendingUtterances.forEach { speak(it) }
                    pendingUtterances.clear()
                    
                    onReady(true)
                }
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
                isInitialized = false
                onReady(false)
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                Log.d(TAG, "Speaking: $utteranceId")
                listener?.onSpeakStart(utteranceId)
            }

            override fun onDone(utteranceId: String) {
                Log.d(TAG, "Done speaking: $utteranceId")
                listener?.onSpeakDone(utteranceId)
            }

            override fun onError(utteranceId: String) {
                Log.e(TAG, "TTS error: $utteranceId")
                listener?.onSpeakError(utteranceId, -1)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error: $utteranceId, code: $errorCode")
                listener?.onSpeakError(utteranceId ?: "", errorCode)
            }
        })
    }

    /**
     * Speak the given text
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD): String {
        val utteranceId = UUID.randomUUID().toString()
        
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, queuing: $text")
            pendingUtterances.add(text)
            return utteranceId
        }

        tts?.speak(text, queueMode, null, utteranceId)
        return utteranceId
    }

    /**
     * Speak immediately, interrupting any current speech
     */
    fun speakNow(text: String): String {
        return speak(text, TextToSpeech.QUEUE_FLUSH)
    }

    /**
     * Stop speaking
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Set speech rate (0.5 = half speed, 2.0 = double speed)
     */
    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.1f, 4.0f))
    }

    /**
     * Set pitch (0.5 = lower, 2.0 = higher)
     */
    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.1f, 4.0f))
    }

    /**
     * Release TTS resources
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.i(TAG, "TTS released")
    }
}
