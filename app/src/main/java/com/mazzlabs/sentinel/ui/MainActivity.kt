package com.mazzlabs.sentinel.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mazzlabs.sentinel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.databinding.ActivityMainBinding
import com.mazzlabs.sentinel.service.AgentAccessibilityService

/**
 * MainActivity - Configuration and Status UI
 * 
 * Provides:
 * - Accessibility service enable/disable
 * - Model loading status
 * - Test inference interface
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // Model path - copy from Downloads: adb shell cp /sdcard/Download/jamba-reasoning-3b-Q4_K_M.gguf /data/local/tmp/
        private const val DEFAULT_MODEL_PATH = "/data/local/tmp/jamba-reasoning-3b-Q4_K_M.gguf"
        private const val DEFAULT_GRAMMAR_PATH = "/data/local/tmp/agent.gbnf"
    }

    private lateinit var binding: ActivityMainBinding
    
    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AgentAccessibilityService.ACTION_SERVICE_CONNECTED -> {
                    updateServiceStatus()
                    showToast("Agent service connected")
                }
                AgentAccessibilityService.ACTION_CONFIRMATION_REQUIRED -> {
                    val actionType = intent.getStringExtra(AgentAccessibilityService.EXTRA_ACTION_TYPE)
                    val target = intent.getStringExtra(AgentAccessibilityService.EXTRA_ACTION_TARGET)
                    showConfirmationDialog(actionType, target)
                }
                AgentAccessibilityService.ACTION_EXECUTED -> {
                    val success = intent.getBooleanExtra(AgentAccessibilityService.EXTRA_SUCCESS, false)
                    val actionType = intent.getStringExtra(AgentAccessibilityService.EXTRA_ACTION_TYPE)
                    showToast("$actionType: ${if (success) "Success" else "Failed"}")
                }
                AgentAccessibilityService.ACTION_ERROR -> {
                    val error = intent.getStringExtra(AgentAccessibilityService.EXTRA_ERROR_MESSAGE)
                    showToast("Error: $error")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        registerReceivers()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateModelStatus()
    }

    override fun onDestroy() {
        unregisterReceiver(serviceReceiver)
        super.onDestroy()
    }

    private fun setupUI() {
        // Accessibility service toggle
        binding.btnEnableService.setOnClickListener {
            if (AgentAccessibilityService.isRunning()) {
                // Can't disable from here - direct to settings
                showToast("Disable via Accessibility Settings")
                openAccessibilitySettings()
            } else {
                openAccessibilitySettings()
            }
        }

        // Model loading
        binding.btnLoadModel.setOnClickListener {
            loadModel()
        }

        // Test inference
        binding.btnTestInference.setOnClickListener {
            testInference()
        }

        // Model path configuration
        binding.etModelPath.setText(DEFAULT_MODEL_PATH)
        binding.etGrammarPath.setText(DEFAULT_GRAMMAR_PATH)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(AgentAccessibilityService.ACTION_SERVICE_CONNECTED)
            addAction(AgentAccessibilityService.ACTION_CONFIRMATION_REQUIRED)
            addAction(AgentAccessibilityService.ACTION_EXECUTED)
            addAction(AgentAccessibilityService.ACTION_ERROR)
        }
        ContextCompat.registerReceiver(
            this,
            serviceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun updateServiceStatus() {
        val isRunning = AgentAccessibilityService.isRunning()
        binding.tvServiceStatus.text = if (isRunning) "ACTIVE" else "INACTIVE"
        binding.tvServiceStatus.setTextColor(
            getColor(if (isRunning) R.color.status_active else R.color.status_inactive)
        )
        binding.btnEnableService.text = if (isRunning) "Open Settings" else "Enable Service"
    }

    private fun updateModelStatus() {
        val isLoaded = SentinelApplication.getInstance().isModelLoaded
        binding.tvModelStatus.text = if (isLoaded) "LOADED" else "NOT LOADED"
        binding.tvModelStatus.setTextColor(
            getColor(if (isLoaded) R.color.status_active else R.color.status_inactive)
        )
        binding.btnLoadModel.isEnabled = !isLoaded
        // Enable test inference when model is loaded (no longer requires accessibility service)
        binding.btnTestInference.isEnabled = isLoaded
    }

    private fun loadModel() {
        val modelPath = binding.etModelPath.text.toString()
        val grammarPath = binding.etGrammarPath.text.toString()
        
        if (modelPath.isBlank() || grammarPath.isBlank()) {
            showToast("Please enter model and grammar paths")
            return
        }

        binding.btnLoadModel.isEnabled = false
        binding.tvModelStatus.text = "LOADING..."
        
        SentinelApplication.getInstance().initializeModel(modelPath, grammarPath) { success ->
            runOnUiThread {
                updateModelStatus()
                showToast(if (success) "Model loaded successfully" else "Failed to load model")
            }
        }
    }

    private fun testInference() {
        val testQuery = binding.etTestQuery.text.toString()
        if (testQuery.isBlank()) {
            showToast("Enter a test query")
            return
        }

        if (!SentinelApplication.getInstance().isModelLoaded) {
            showToast("Model not loaded")
            return
        }

        // Disable button and show loading
        binding.btnTestInference.isEnabled = false
        binding.tvInferenceResult.visibility = View.VISIBLE
        binding.tvInferenceResult.text = "Running inference..."

        lifecycleScope.launch {
            try {
                val mockScreenContext = buildMockScreenContext()
                val nativeBridge = SentinelApplication.getInstance().nativeBridge

                val result = withContext(Dispatchers.IO) {
                    // Try inference with grammar first
                    var response = nativeBridge.infer(testQuery, mockScreenContext)

                    // Check if grammar inference failed and retry without grammar
                    if (shouldRetryWithoutGrammar(response)) {
                        Log.w(TAG, "Grammar inference failed, retrying without grammar constraint")
                        response = nativeBridge.inferWithoutGrammar(testQuery, mockScreenContext)
                        Log.i(TAG, "Fallback inference result: $response")
                    }

                    response
                }

                binding.tvInferenceResult.text = "Result:\n$result"
                Log.i(TAG, "Test inference result: $result")

            } catch (e: Exception) {
                Log.e(TAG, "Test inference failed", e)
                binding.tvInferenceResult.text = "Error: ${e.message}"
            } finally {
                binding.btnTestInference.isEnabled = true
            }
        }
    }

    /**
     * Check if inference response indicates grammar failure
     */
    private fun shouldRetryWithoutGrammar(response: String): Boolean {
        val errorIndicators = listOf(
            "Sampler error",
            "Grammar",
            "grammar constraint",
            "empty grammar stack",
            "inference error"
        )
        return errorIndicators.any { response.contains(it, ignoreCase = true) }
    }

    /**
     * Build a mock screen context for testing inference without accessibility service
     */
    private fun buildMockScreenContext(): String {
        return """
            |[Screen: MainActivity]
            |[Package: com.mazzlabs.sentinel]
            |[Element: button id=btn_settings text="Settings" clickable=true]
            |[Element: button id=btn_share text="Share" clickable=true]
            |[Element: text id=tv_title text="Welcome to Sentinel"]
            |[Element: edittext id=et_search hint="Search..." editable=true]
            |[Element: button id=btn_submit text="Submit" clickable=true]
            |[Element: list id=rv_items scrollable=true]
            |[Element: listitem id=item_1 text="Item 1" clickable=true]
            |[Element: listitem id=item_2 text="Item 2" clickable=true]
            |[Element: listitem id=item_3 text="Item 3" clickable=true]
        """.trimMargin()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun showConfirmationDialog(actionType: String?, target: String?) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirm Action")
            .setMessage("The agent wants to perform:\n\n$actionType on \"$target\"\n\nPress Volume Up to confirm.")
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
