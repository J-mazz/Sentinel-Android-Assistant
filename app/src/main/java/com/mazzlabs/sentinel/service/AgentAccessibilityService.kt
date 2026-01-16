package com.mazzlabs.sentinel.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.core.AgentController
import com.mazzlabs.sentinel.input.VoiceInputManager
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.overlay.OverlayManager
import com.mazzlabs.sentinel.security.ActionFirewall
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * AgentAccessibilityService - The Observer
 * 
 * Passive collection of UI state. Flattens the AccessibilityNodeInfo tree
 * into a semantic string for the Cortex (native inference layer).
 * 
 * Security: No business logic here. Pure observation and dispatch.
 */
class AgentAccessibilityService : AccessibilityService(), OverlayManager.OverlayListener {

    companion object {
        private const val TAG = "AgentAccessibilityService"
        
        const val ACTION_SERVICE_CONNECTED = "com.mazzlabs.sentinel.SERVICE_CONNECTED"
        const val ACTION_CONFIRMATION_REQUIRED = "com.mazzlabs.sentinel.CONFIRMATION_REQUIRED"
        const val ACTION_EXECUTED = "com.mazzlabs.sentinel.ACTION_EXECUTED"
        const val ACTION_ERROR = "com.mazzlabs.sentinel.ERROR"
        
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_ACTION_TARGET = "action_target"
        const val EXTRA_ACTION_REASONING = "action_reasoning"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
        
        fun isRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val uiTreeFlattener = UITreeFlattener()
    private val actionFirewall = ActionFirewall()
    private val actionDispatcher = ActionDispatcher()
    
    // Agent controller for tools + UI actions
    private lateinit var agentController: AgentController
    
    // Input managers
    private lateinit var overlayManager: OverlayManager
    private lateinit var voiceInputManager: VoiceInputManager
    
    private var isAgentTriggered = false
    private var pendingAction: AgentAction? = null
    private var volumeUpCallback: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AgentAccessibilityService created")
        
        // Initialize agent controller (tools + inference)
        agentController = AgentController(this)
        
        // Initialize overlay and voice input
        overlayManager = OverlayManager(this)
        overlayManager.setListener(this)
        
        voiceInputManager = VoiceInputManager(this)
        voiceInputManager.initialize()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        // Configure accessibility service
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            
            notificationTimeout = 100
        }
        
        Log.i(TAG, "AgentAccessibilityService connected and configured")
        
        // Show the floating overlay button
        overlayManager.show()
        
        // Listen for voice input results
        serviceScope.launch {
            voiceInputManager.state.collectLatest { state ->
                when (state) {
                    is VoiceInputManager.VoiceState.Result -> {
                        Log.i(TAG, "Voice input received: ${state.text}")
                        triggerAgent(state.text)
                    }
                    is VoiceInputManager.VoiceState.Error -> {
                        Log.e(TAG, "Voice input error: ${state.message}")
                        broadcastError("Voice: ${state.message}")
                    }
                    else -> { /* Idle or Listening - no action */ }
                }
            }
        }
        
        // Broadcast service ready
        sendBroadcast(Intent(ACTION_SERVICE_CONNECTED))
    }
    
    // OverlayManager.OverlayListener implementation
    override fun onOverlayTapped() {
        Log.i(TAG, "Overlay tapped - starting voice input")
        voiceInputManager.startListening()
    }
    
    override fun onOverlayLongPressed() {
        Log.i(TAG, "Overlay long-pressed - hiding overlay")
        overlayManager.hide()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Only process window content changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        // Cache current screen context for when agent is triggered
        rootInActiveWindow?.let { root ->
            try {
                lastScreenContext = uiTreeFlattener.flatten(root)
                lastPackageName = event.packageName?.toString() ?: ""
            } finally {
                // Don't recycle - system manages this
            }
        }
    }

    /**
     * Trigger agent inference with a user query
     * Called from overlay button or voice input
     */
    fun triggerAgent(userQuery: String) {
        if (!SentinelApplication.getInstance().isModelLoaded) {
            Log.w(TAG, "Model not loaded, cannot process query")
            broadcastError("Model not loaded")
            return
        }

        serviceScope.launch {
            try {
                val screenContext = lastScreenContext
                Log.d(TAG, "Processing query: $userQuery")
                Log.d(TAG, "Screen context length: ${screenContext.length}")

                // Use AgentController to process (handles both tools and UI actions)
                val result = agentController.process(userQuery, screenContext)
                
                Log.d(TAG, "Agent result: $result")

                withContext(Dispatchers.Main) {
                    handleAgentResult(result)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during agent processing", e)
                broadcastError("Agent error: ${e.message}")
            }
        }
    }
    
    /**
     * Handle the result from AgentController
     */
    private fun handleAgentResult(result: AgentController.AgentResult) {
        when (result) {
            is AgentController.AgentResult.ToolResult -> {
                handleToolResult(result.response)
            }
            is AgentController.AgentResult.UIAction -> {
                executeAction(result.action)
            }
            is AgentController.AgentResult.Response -> {
                Log.i(TAG, "Agent response: ${result.message}")
                // Could show toast or TTS here
                broadcastSuccess(result.message)
            }
            is AgentController.AgentResult.Error -> {
                Log.e(TAG, "Agent error: ${result.message}")
                broadcastError(result.message)
            }
        }
    }
    
    /**
     * Handle tool execution results
     */
    private fun handleToolResult(response: ToolResponse) {
        when (response) {
            is ToolResponse.Success -> {
                Log.i(TAG, "Tool success: ${response.message}")
                broadcastSuccess(response.message)
            }
            is ToolResponse.Error -> {
                Log.e(TAG, "Tool error: ${response.message}")
                broadcastError("${response.moduleId}: ${response.message}")
            }
            is ToolResponse.PermissionRequired -> {
                Log.w(TAG, "Permissions needed: ${response.permissions}")
                broadcastError("Need permissions: ${response.permissions.joinToString()}")
            }
            is ToolResponse.Confirmation -> {
                Log.i(TAG, "Confirmation needed: ${response.message}")
                // For now, just broadcast - could show dialog
                broadcastConfirmation(response.message)
            }
        }
    }
    
    private fun broadcastSuccess(message: String) {
        val intent = Intent(ACTION_EXECUTED).apply {
            putExtra(EXTRA_SUCCESS, true)
            putExtra("message", message)
        }
        sendBroadcast(intent)
    }
    
    private fun broadcastConfirmation(message: String) {
        val intent = Intent(ACTION_CONFIRMATION_REQUIRED).apply {
            putExtra("message", message)
        }
        sendBroadcast(intent)
    }

    /**
     * Execute validated action through the Actuator
     */
    private fun executeAction(action: AgentAction) {
        // Check firewall for dangerous actions
        if (actionFirewall.isDangerous(action)) {
            Log.w(TAG, "Dangerous action detected: ${action.action}")
            pendingAction = action
            
            // Request physical confirmation
            requestPhysicalConfirmation(action) {
                dispatchAction(action)
            }
        } else {
            dispatchAction(action)
        }
    }

    /**
     * Request physical volume-up key confirmation for dangerous actions
     */
    private fun requestPhysicalConfirmation(action: AgentAction, onConfirm: () -> Unit) {
        volumeUpCallback = onConfirm
        
        // Broadcast confirmation request to UI
        val intent = Intent(ACTION_CONFIRMATION_REQUIRED).apply {
            putExtra(EXTRA_ACTION_TYPE, action.action.name)
            putExtra(EXTRA_ACTION_TARGET, action.target ?: "")
            putExtra(EXTRA_ACTION_REASONING, action.reasoning ?: "")
        }
        sendBroadcast(intent)
        
        Log.i(TAG, "Waiting for Volume Up confirmation for: ${action.action}")
    }

    /**
     * Dispatch action through the Actuator
     */
    private fun dispatchAction(action: AgentAction) {
        rootInActiveWindow?.let { root ->
            try {
                val success = actionDispatcher.dispatch(this, root, action)
                Log.i(TAG, "Action ${action.action} executed: $success")
                
                val intent = Intent(ACTION_EXECUTED).apply {
                    putExtra(EXTRA_ACTION_TYPE, action.action.name)
                    putExtra(EXTRA_SUCCESS, success)
                }
                sendBroadcast(intent)
            } finally {
                // Root managed by system
            }
        }
        
        pendingAction = null
        volumeUpCallback = null
    }

    /**
     * Handle physical key events for confirmation
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && 
            event.action == KeyEvent.ACTION_DOWN) {
            
            volumeUpCallback?.let { callback ->
                Log.i(TAG, "Volume Up pressed - confirming action")
                callback()
                return true // Consume the event
            }
        }
        
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AgentAccessibilityService interrupted")
    }

    override fun onDestroy() {
        // Clean up overlay and voice input
        overlayManager.hide()
        voiceInputManager.release()
        
        instance = null
        serviceScope.cancel()
        Log.i(TAG, "AgentAccessibilityService destroyed")
        super.onDestroy()
    }

    private fun broadcastError(message: String) {
        val intent = Intent(ACTION_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    // Cached state
    @Volatile
    private var lastScreenContext: String = ""
    @Volatile
    private var lastPackageName: String = ""
}
