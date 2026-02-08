package com.mazzlabs.sentinel.graph.nodes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.capture.ScreenShareManager
import com.mazzlabs.sentinel.graph.AgentIntent
import com.mazzlabs.sentinel.graph.AgentNode
import com.mazzlabs.sentinel.graph.AgentState

/**
 * SelectionProcessorNode - Handles selection-based intents
 */
class SelectionProcessorNode(private val context: Context) : AgentNode {

    override suspend fun process(state: AgentState): AgentState {
        val selectedText = state.extractedEntities["selected_text"] ?: ""

        if (selectedText.isBlank() &&
            state.intent != AgentIntent.EXTRACT_DATA_FROM_SELECTION) {
            return state.copy(
                response = "No text available for selection action.",
                isComplete = true
            )
        }

        return when (state.intent) {
            AgentIntent.SEARCH_SELECTED -> searchSelected(state, selectedText)
            AgentIntent.TRANSLATE_SELECTED -> translateSelected(state, selectedText)
            AgentIntent.COPY_SELECTED -> copySelected(state, selectedText)
            AgentIntent.SAVE_SELECTED -> saveSelected(state)
            AgentIntent.SHARE_SELECTED -> shareSelected(state, selectedText)
            AgentIntent.EXTRACT_DATA_FROM_SELECTION -> extractData(state, selectedText)
            AgentIntent.SEND_SELECTION_TO_REMOTE -> sendToRemote(state, selectedText)
            AgentIntent.ANALYZE_IMAGE_REGION -> analyzeImageRegion(state, selectedText)
            else -> state
        }
    }

    private fun searchSelected(state: AgentState, text: String): AgentState {
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(searchIntent)

        return state.copy(
            response = "Searching for: $text",
            isComplete = true
        )
    }

    private fun translateSelected(state: AgentState, text: String): AgentState {
        val translateUrl = "https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(text)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(translateUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        return state.copy(
            response = "Opening translation for: $text",
            isComplete = true
        )
    }

    private fun copySelected(state: AgentState, text: String): AgentState {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Selected Text", text)
        clipboard.setPrimaryClip(clip)

        return state.copy(
            response = "Copied to clipboard: $text",
            isComplete = true
        )
    }

    private fun shareSelected(state: AgentState, text: String): AgentState {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })

        return state.copy(
            response = "Sharing: $text",
            isComplete = true
        )
    }

    private fun saveSelected(state: AgentState): AgentState {
        val selectedText = state.extractedEntities["selected_text"] ?: ""
        if (selectedText.isBlank()) {
            return state.copy(
                response = "Nothing to save from selection.",
                isComplete = true
            )
        }

        return try {
            val filename = "selection_${System.currentTimeMillis()}.txt"
            val file = java.io.File(context.filesDir, filename)
            file.writeText(selectedText)

            state.copy(
                response = "Saved selection to ${file.name}",
                extractedEntities = state.extractedEntities + mapOf("saved_file" to file.absolutePath),
                isComplete = true
            )
        } catch (e: Exception) {
            state.copy(
                response = "Failed to save selection: ${e.message}",
                isComplete = true
            )
        }
    }

    private suspend fun sendToRemote(state: AgentState, text: String): AgentState {
        val app = context.applicationContext as? SentinelApplication
        val gatewayClient = app?.gatewayClient
        if (gatewayClient == null || !gatewayClient.isConnected()) {
            return state.copy(
                response = "Gateway not connected. Cannot send selection to remote agent.",
                isComplete = true
            )
        }

        val screenShareManager = ScreenShareManager(gatewayClient)
        val regionInfo = state.extractedEntities["region_info"] ?: ""
        val prompt = if (text.isNotBlank()) {
            "The user selected this text from their phone screen: \"$text\". $regionInfo\nPlease analyze and respond."
        } else {
            "The user selected a region on their phone screen. $regionInfo\nPlease analyze the image."
        }

        val result = try {
            gatewayClient.sendMessage(
                sessionKey = com.mazzlabs.sentinel.gateway.GatewayConfig.SessionKeys.ARCHITECT,
                message = prompt
            )
        } catch (e: Exception) {
            return state.copy(
                response = "Failed to send to remote agent: ${e.message}",
                isComplete = true
            )
        }

        return state.copy(
            response = result.text ?: "Remote agent returned no response.",
            isComplete = true
        )
    }

    private suspend fun analyzeImageRegion(state: AgentState, text: String): AgentState {
        val app = context.applicationContext as? SentinelApplication
        val gatewayClient = app?.gatewayClient
        if (gatewayClient == null || !gatewayClient.isConnected()) {
            return state.copy(
                response = "Gateway not connected. Cannot analyze image region remotely.",
                isComplete = true
            )
        }

        val base64Image = state.extractedEntities["base64_image"] ?: ""
        val ocrText = text.ifBlank { state.extractedEntities["ocr_text"] ?: "" }

        val prompt = buildString {
            appendLine("Analyze this image region from the user's phone screen.")
            if (ocrText.isNotBlank()) {
                appendLine()
                appendLine("OCR text extracted: \"$ocrText\"")
            }
            if (base64Image.isNotBlank()) {
                appendLine()
                appendLine("Image (base64 JPEG):")
                appendLine("```")
                appendLine(base64Image)
                appendLine("```")
            }
        }

        val result = try {
            gatewayClient.sendMessage(
                sessionKey = com.mazzlabs.sentinel.gateway.GatewayConfig.SessionKeys.ARCHITECT,
                message = prompt
            )
        } catch (e: Exception) {
            return state.copy(
                response = "Failed to analyze image region: ${e.message}",
                isComplete = true
            )
        }

        return state.copy(
            response = result.text ?: "Remote agent returned no response.",
            isComplete = true
        )
    }

    private fun extractData(state: AgentState, text: String): AgentState {
        val phone = Regex("""\b\d{3}[-.]?\d{3}[-.]?\d{4}\b""").find(text)?.value
        val email = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
            .find(text)?.value
        val url = Regex("""https?://[^\s]+""").find(text)?.value

        val extracted = buildString {
            phone?.let { appendLine("Phone: $it") }
            email?.let { appendLine("Email: $it") }
            url?.let { appendLine("URL: $it") }
        }

        return if (extracted.isNotBlank()) {
            state.copy(
                response = "Extracted data:\n$extracted",
                extractedEntities = state.extractedEntities + mapOf(
                    "phone" to (phone ?: ""),
                    "email" to (email ?: ""),
                    "url" to (url ?: "")
                ),
                isComplete = true
            )
        } else {
            state.copy(
                response = "No phone numbers, emails, or URLs found in selection",
                isComplete = true
            )
        }
    }
}
