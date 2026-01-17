package com.mazzlabs.sentinel.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.capture.ScreenCaptureManager
import com.mazzlabs.sentinel.core.AgentController
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.EnhancedAgentOrchestrator
import com.mazzlabs.sentinel.graph.AgentIntent
import com.mazzlabs.sentinel.input.VoiceInputManager
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.overlay.OverlayManager
import com.mazzlabs.sentinel.overlay.SelectionOverlayManager
import com.mazzlabs.sentinel.security.ActionFirewall
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import com.mazzlabs.sentinel.graph.nodes.SelectionProcessorNode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicLong

/**
 * AgentAccessibilityService - The Observer
 * 
 * Passive collection of UI state. Flattens the AccessibilityNodeInfo tree
 * into a semantic string for the Cortex (native inference layer).
 * 
 * Security: No business logic here. Pure observation and dispatch.
 */
class AgentAccessibilityService : AccessibilityService(),
    OverlayManager.OverlayListener,
    SelectionOverlayManager.SelectionListener {

    companion object {
        private const val TAG = "AgentAccessibilityService"

        private const val OCR_CONFIDENCE_THRESHOLD = 0.6f

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
    private val elementRegistry = ElementRegistry()
    private val actionDispatcher = ActionDispatcher(elementRegistry)
    private lateinit var enhancedOrchestrator: EnhancedAgentOrchestrator
    
    // Agent controller for tools + UI actions
    private lateinit var agentController: AgentController
    
    // Input managers
    private lateinit var overlayManager: OverlayManager
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var selectionOverlayManager: SelectionOverlayManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var selectionProcessorNode: SelectionProcessorNode
    private var isSelectionMode = false
    private var currentAgentJob: Job? = null
    private val requestCounter = AtomicLong(0)

    private data class OcrResult(
        val text: String,
        val confidence: Float,
        val languageHint: String
    )
    
    private var isAgentTriggered = false
    private var pendingAction: AgentAction? = null
    private var volumeUpCallback: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AgentAccessibilityService created")
        
        // Initialize agent controller (tools + inference)
        agentController = AgentController(this)
        enhancedOrchestrator = EnhancedAgentOrchestrator(this)
        
        // Initialize overlay and voice input
        overlayManager = OverlayManager(this)
        overlayManager.setListener(this)
        
        voiceInputManager = VoiceInputManager(this)
        voiceInputManager.initialize()

        selectionOverlayManager = SelectionOverlayManager(this)
        selectionOverlayManager.setListener(this)
        screenCaptureManager = ScreenCaptureManager(this)
        selectionProcessorNode = SelectionProcessorNode(this)
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
        Log.i(TAG, "Overlay long-pressed - entering selection mode")
        enterSelectionMode()
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
                elementRegistry.rebuild(root)
                lastScreenContext = uiTreeFlattener.flatten(root)
                lastElementList = elementRegistry.toPromptString()
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

        val requestId = requestCounter.incrementAndGet()
        currentAgentJob?.cancel()
        currentAgentJob = serviceScope.launch {
            try {
                val screenContext = if (lastElementList.isNotBlank()) {
                    buildString {
                        appendLine(lastElementList)
                        appendLine()
                        appendLine(lastScreenContext)
                    }
                } else {
                    lastScreenContext
                }
                Log.d(TAG, "Processing query: $userQuery")
                Log.d(TAG, "Screen context length: ${screenContext.length}")

                val finalState = enhancedOrchestrator.process(
                    userQuery = userQuery,
                    sessionId = "main",
                    screenContext = screenContext
                )

                Log.d(TAG, "Enhanced agent state: $finalState")

                if (requestId == requestCounter.get()) {
                    withContext(Dispatchers.Main) {
                        handleAgentState(finalState)
                    }
                } else {
                    Log.d(TAG, "Stale agent result ignored for request $requestId")
                }

            } catch (e: Exception) {
                if (requestId == requestCounter.get()) {
                    Log.e(TAG, "Error during agent processing", e)
                    broadcastError("Agent error: ${e.message}")
                }
            }
        }
    }

    private fun handleAgentState(state: AgentState) {
        when {
            state.needsUserInput -> {
                broadcastSuccess(state.response)
            }
            state.action != null -> {
                executeAction(state.action)
            }
            state.response.isNotBlank() -> {
                broadcastSuccess(state.response)
            }
            state.error != null -> {
                broadcastError(state.error)
            }
            else -> {
                broadcastSuccess("Request processed.")
            }
        }
    }

    /**
     * Enter interactive selection mode
     */
    private fun enterSelectionMode() {
        if (isSelectionMode) return

        screenCaptureManager.takeScreenshot { bitmap ->
            if (bitmap != null) {
                isSelectionMode = true
                overlayManager.hide()
                selectionOverlayManager.show(bitmap)
                performHapticFeedback()
            } else {
                Log.e(TAG, "Failed to capture screenshot")
                broadcastError("Screenshot failed")
            }
        }
    }

    private fun performHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    50,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    override fun onSelectionComplete(selectedRegion: Rect, screenshot: Bitmap) {
        Log.i(TAG, "Selection complete: $selectedRegion")
        isSelectionMode = false
        overlayManager.show()
        processSelectedRegion(selectedRegion, screenshot)
    }

    override fun onSelectionCanceled() {
        Log.i(TAG, "Selection canceled")
        isSelectionMode = false
        overlayManager.show()
    }

    override fun onTextExtracted(text: String, region: Rect) {
        Log.i(TAG, "Text extracted: $text")

        if (text.isNotBlank()) {
            triggerAgent("Process this text: $text")
        }
    }

    private fun processSelectedRegion(region: Rect, screenshot: Bitmap) {
        serviceScope.launch {
            try {
                broadcastSuccess("Processing selected region...")

                val ocr = performOCROnRegion(screenshot)

                if (ocr.text.isNotBlank() && ocr.confidence >= OCR_CONFIDENCE_THRESHOLD) {
                    val state = AgentState(
                        sessionId = "main",
                        intent = AgentIntent.SEARCH_SELECTED,
                        extractedEntities = mapOf(
                            "selected_text" to ocr.text,
                            "language_hint" to ocr.languageHint,
                            "ocr_confidence" to ocr.confidence.toString()
                        )
                    )

                    val processed = selectionProcessorNode.process(state)
                    withContext(Dispatchers.Main) {
                        handleAgentState(processed)
                    }
                } else {
                    showSelectionActions(region, screenshot)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing selection", e)
                broadcastError("Failed to process selection: ${e.message}")
            }
        }
    }

    private suspend fun performOCROnRegion(bitmap: Bitmap): OcrResult {
        return withContext(Dispatchers.Default) {
            suspendCancellableCoroutine { cont ->
                val downscaled = downscaleBitmap(bitmap)
                val image = InputImage.fromBitmap(downscaled, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text ?: ""
                        val confidence = computeOcrConfidence(result)
                        val languageHint = detectLanguageHint(text)
                        if (cont.isActive) {
                            cont.resume(OcrResult(text, confidence, languageHint))
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "OCR failed", e)
                        if (cont.isActive) {
                            cont.resume(OcrResult("", 0f, "unknown"))
                        }
                    }
                    .addOnCompleteListener {
                        recognizer.close()
                        if (downscaled !== bitmap) {
                            downscaled.recycle()
                        }
                    }
            }
        }
    }

    private fun downscaleBitmap(bitmap: Bitmap, maxSize: Int = 1280): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= maxSize) return bitmap

        val scale = maxSize.toFloat() / maxDim.toFloat()
        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun computeOcrConfidence(result: com.google.mlkit.vision.text.Text): Float {
        var sum = 0f
        var count = 0

        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    val confidence = element.confidence ?: 1f
                    if (confidence >= 0f) {
                        sum += confidence
                        count += 1
                    }
                }
            }
        }

        return if (count > 0) (sum / count).coerceIn(0f, 1f) else 1f
    }

    private fun detectLanguageHint(text: String): String {
        if (text.isBlank()) return "unknown"

        var latin = 0
        var cyrillic = 0
        var arabic = 0
        var han = 0
        var hiragana = 0
        var katakana = 0

        for (char in text) {
            when (Character.UnicodeScript.of(char.code)) {
                Character.UnicodeScript.LATIN -> latin++
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.ARABIC -> arabic++
                Character.UnicodeScript.HAN -> han++
                Character.UnicodeScript.HIRAGANA -> hiragana++
                Character.UnicodeScript.KATAKANA -> katakana++
                else -> Unit
            }
        }

        return when (maxOf(latin, cyrillic, arabic, han, hiragana + katakana)) {
            latin -> "latin"
            cyrillic -> "cyrillic"
            arabic -> "arabic"
            han -> "cjk"
            else -> "unknown"
        }
    }

    private fun showSelectionActions(region: Rect, screenshot: Bitmap) {
        val actions = listOf(
            "Search for this",
            "Translate this",
            "Copy to clipboard",
            "Share",
            "Save as image"
        )

        @Suppress("UNUSED_VARIABLE")
        val unused = actions

        broadcastSuccess("Selected region. Choose an action.")
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

                if (!success && action.elementId != null) {
                    broadcastError("Element ${action.elementId} not found. UI may have changed.")
                }

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
    private var lastElementList: String = ""
    @Volatile
    private var lastPackageName: String = ""
}
