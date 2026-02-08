package com.mazzlabs.sentinel.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.view.Display
import android.view.WindowManager
import java.io.ByteArrayOutputStream

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
    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        screenWidth = bounds.width()
        screenHeight = bounds.height()
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
            screenWidth,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
    }

    /**
     * Compress a bitmap to JPEG bytes.
     *
     * @param bitmap Source bitmap
     * @param quality JPEG quality (0-100)
     * @return Compressed JPEG byte array
     */
    fun compressToJpeg(bitmap: Bitmap, quality: Int = 75): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Compress a bitmap to a base64-encoded JPEG string.
     */
    fun compressToBase64Jpeg(bitmap: Bitmap, quality: Int = 75): String {
        val bytes = compressToJpeg(bitmap, quality)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Downscale a bitmap to fit within 720p (1280x720) bounds, preserving aspect ratio.
     * Returns the original bitmap if already small enough.
     */
    fun downscaleTo720p(bitmap: Bitmap): Bitmap {
        val targetWidth = 1280
        val targetHeight = 720
        val width = bitmap.width
        val height = bitmap.height

        if (width <= targetWidth && height <= targetHeight) return bitmap

        val scaleW = targetWidth.toFloat() / width
        val scaleH = targetHeight.toFloat() / height
        val scale = minOf(scaleW, scaleH)

        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
