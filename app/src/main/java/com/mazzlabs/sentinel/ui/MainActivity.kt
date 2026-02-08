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
import com.mazzlabs.sentinel.ui.settings.SettingsActivity
import com.mazzlabs.sentinel.inference.InferenceOptions

/**
 * MainActivity - Configuration and Status UI
 * 
 * Provides:
 * - Accessibility service enable/disable
 * - Gateway connection status
 * - Test inference interface
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
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
        updateGatewayStatus()
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

        // Test inference
        binding.btnTestInference.setOnClickListener {
            testInference()
        }

        // Settings button (gateway, privacy, dev mode)
        binding.btnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Gateway configuration button
        binding.btnLoadModel?.apply {
            text = "Configure Gateway"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
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

    private fun updateGatewayStatus() {
        lifecycleScope.launch {
            val app = SentinelApplication.getInstance()
            val inferenceRouter = app.inferenceRouter
            val isConnected = inferenceRouter?.isAvailable() == true
            
            binding.tvModelStatus?.text = if (isConnected) "CONNECTED" else "NOT CONNECTED"
            binding.tvModelStatus?.setTextColor(
                getColor(if (isConnected) R.color.status_active else R.color.status_inactive)
            )
            
            // Enable test inference when gateway is connected
            binding.btnTestInference.isEnabled = isConnected
        }
    }

    private fun testInference() {
        val testQuery = binding.etTestQuery.text.toString()
        if (testQuery.isBlank()) {
            showToast("Enter a test query")
            return
        }

        val app = SentinelApplication.getInstance()
        val inferenceRouter = app.inferenceRouter

        if (inferenceRouter == null) {
            showToast("Gateway not configured. Please configure in settings.")
            return
        }

        // Disable button and show loading
        binding.btnTestInference.isEnabled = false
        binding.tvInferenceResult.visibility = View.VISIBLE
        binding.tvInferenceResult.text = "Running inference via gateway..."

        lifecycleScope.launch {
            try {
                val mockScreenContext = buildMockScreenContext()
                
                val prompt = buildString {
                    appendLine("Test inference query:")
                    appendLine(testQuery)
                    appendLine()
                    appendLine("Mock screen context:")
                    appendLine(mockScreenContext)
                }

                val result = withContext(Dispatchers.IO) {
                    inferenceRouter.infer(
                        prompt = prompt,
                        options = InferenceOptions(
                            temperature = 0.7f,
                            maxTokens = 512
                        )
                    )
                }

                if (result.success) {
                    binding.tvInferenceResult.text = "✓ Gateway Response:\n${result.text}"
                    Log.i(TAG, "Test inference result: ${result.text}")
                } else {
                    binding.tvInferenceResult.text = "✗ Error: ${result.error}"
                    Log.e(TAG, "Test inference failed: ${result.error}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Test inference failed", e)
                binding.tvInferenceResult.text = "✗ Error: ${e.message}"
            } finally {
                binding.btnTestInference.isEnabled = true
            }
        }
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
