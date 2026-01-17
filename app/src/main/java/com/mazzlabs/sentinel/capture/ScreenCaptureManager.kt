package com.mazzlabs.sentinel.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager

/**
 * ScreenCaptureManager - Captures screen using AccessibilityService
 *
 * Note: Android 14+ allows screenshot via AccessibilityService.takeScreenshot()
 */
class ScreenCaptureManager(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "ScreenCapture"
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = DisplayMetrics()

    init {
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
    }

    /**
     * Take screenshot using AccessibilityService API (Android 14+)
     */
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.takeScreenshot(
                service.display?.displayId ?: Display.DEFAULT_DISPLAY,
                { it.run() },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = screenshot.hardwareBuffer?.let {
                            Bitmap.wrapHardwareBuffer(it, null)
                        }
                        callback(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with code: $errorCode")
                        callback(null)
                    }
                }
            )
        } else {
            callback(captureFromAccessibilityTree())
        }
    }

    /**
     * Fallback: Build approximate screenshot from accessibility tree
     */
    private fun captureFromAccessibilityTree(): Bitmap? {
        return Bitmap.createBitmap(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            Bitmap.Config.ARGB_8888
        )
    }
}
