package com.mazzlabs.sentinel.capture

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.mazzlabs.sentinel.gateway.OpenClawGatewayClient
import com.mazzlabs.sentinel.gateway.GatewayConfig
import java.io.ByteArrayOutputStream

/**
 * ScreenShareManager - Encode screen (JPEG + accessibility tree text) and send to gateway
 *
 * Handles:
 * 1. JPEG compression with configurable quality
 * 2. Downscaling to 720p for bandwidth efficiency
 * 3. Combining visual data with accessibility tree text
 * 4. Sending encoded payload to remote agent via gateway
 */
class ScreenShareManager(
    private val gatewayClient: OpenClawGatewayClient
) {
    companion object {
        private const val TAG = "ScreenShareManager"
        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720
        private const val JPEG_QUALITY = 75
        private const val MAX_PAYLOAD_BYTES = 1_000_000 // 1MB limit
    }

    /**
     * Send a full screen capture with accessibility context to the remote agent.
     *
     * @param screenshot Full screen bitmap
     * @param accessibilityTree Text representation of the current accessibility tree
     * @param prompt Optional user prompt to include with the screen share
     * @return The remote agent's response text, or null on failure
     */
    suspend fun sendScreen(
        screenshot: Bitmap,
        accessibilityTree: String,
        prompt: String = "Analyze this screen and describe what you see."
    ): String? {
        if (!gatewayClient.isConnected()) {
            Log.w(TAG, "Gateway not connected, cannot share screen")
            return null
        }

        return try {
            val encoded = encodeBitmap(screenshot)
            if (encoded == null) {
                Log.e(TAG, "Failed to encode screenshot")
                return null
            }

            val message = buildScreenMessage(encoded, accessibilityTree, prompt)

            val result = gatewayClient.sendMessage(
                sessionKey = GatewayConfig.SessionKeys.ARCHITECT,
                message = message
            )

            result.text
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send screen to gateway", e)
            null
        }
    }

    /**
     * Send a cropped region with OCR text and optional image data to the remote agent.
     *
     * @param regionBitmap Cropped bitmap of the selected region
     * @param ocrText OCR-extracted text from the region
     * @param region The screen coordinates of the selection
     * @param prompt User prompt for the analysis
     * @return The remote agent's response text, or null on failure
     */
    suspend fun sendRegion(
        regionBitmap: Bitmap,
        ocrText: String,
        region: Rect,
        prompt: String = "Analyze this selected region."
    ): String? {
        if (!gatewayClient.isConnected()) {
            Log.w(TAG, "Gateway not connected, cannot share region")
            return null
        }

        return try {
            val encoded = encodeBitmap(regionBitmap)

            val message = buildRegionMessage(encoded, ocrText, region, prompt)

            val result = gatewayClient.sendMessage(
                sessionKey = GatewayConfig.SessionKeys.ARCHITECT,
                message = message
            )

            result.text
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send region to gateway", e)
            null
        }
    }

    /**
     * Encode a bitmap to base64 JPEG, downscaling to 720p if needed.
     */
    private fun encodeBitmap(bitmap: Bitmap): String? {
        val downscaled = downscaleTo720p(bitmap)
        val stream = ByteArrayOutputStream()

        return try {
            downscaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            val bytes = stream.toByteArray()

            if (bytes.size > MAX_PAYLOAD_BYTES) {
                // Re-compress at lower quality
                stream.reset()
                downscaled.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                val reducedBytes = stream.toByteArray()
                Log.d(TAG, "Re-compressed JPEG: ${bytes.size} -> ${reducedBytes.size} bytes")
                Base64.encodeToString(reducedBytes, Base64.NO_WRAP)
            } else {
                Log.d(TAG, "Encoded JPEG: ${bytes.size} bytes")
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode bitmap", e)
            null
        } finally {
            if (downscaled !== bitmap) {
                downscaled.recycle()
            }
            stream.close()
        }
    }

    /**
     * Downscale bitmap to fit within 720p (1280x720) bounds while preserving aspect ratio.
     */
    private fun downscaleTo720p(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= TARGET_WIDTH && height <= TARGET_HEIGHT) {
            return bitmap
        }

        val scaleW = TARGET_WIDTH.toFloat() / width
        val scaleH = TARGET_HEIGHT.toFloat() / height
        val scale = minOf(scaleW, scaleH)

        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun buildScreenMessage(
        base64Image: String,
        accessibilityTree: String,
        prompt: String
    ): String {
        return buildString {
            appendLine(prompt)
            appendLine()
            appendLine("## Screen Accessibility Tree")
            appendLine("```")
            appendLine(accessibilityTree.take(8000))
            appendLine("```")
            appendLine()
            appendLine("## Screen Image (base64 JPEG)")
            appendLine("```")
            appendLine(base64Image)
            appendLine("```")
        }
    }

    private fun buildRegionMessage(
        base64Image: String?,
        ocrText: String,
        region: Rect,
        prompt: String
    ): String {
        return buildString {
            appendLine(prompt)
            appendLine()
            appendLine("## Selected Region")
            appendLine("Coordinates: (${region.left}, ${region.top}) to (${region.right}, ${region.bottom})")
            appendLine("Size: ${region.width()} x ${region.height()}")
            appendLine()
            if (ocrText.isNotBlank()) {
                appendLine("## OCR Text")
                appendLine("```")
                appendLine(ocrText)
                appendLine("```")
                appendLine()
            }
            if (base64Image != null) {
                appendLine("## Region Image (base64 JPEG)")
                appendLine("```")
                appendLine(base64Image)
                appendLine("```")
            }
        }
    }
}
