package com.mazzlabs.sentinel.tools.modules

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.mazzlabs.sentinel.tools.framework.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * MessagingModule - SMS messaging via SmsManager and Telephony provider
 * 
 * Operations:
 * - send_sms: Send an SMS message (requires SEND_SMS permission)
 * - read_messages: Read SMS conversations (requires READ_SMS)
 * - get_conversations: List recent conversations
 * - open_conversation: Open messaging app to a specific contact
 */
class MessagingModule : ToolModule {
    
    companion object {
        private const val TAG = "MessagingModule"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }
    
    override val moduleId = "messaging"
    
    override val description = "Send and read SMS messages - text conversations, send messages"
    
    override val requiredPermissions = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )
    
    override val operations = listOf(
        ToolOperation(
            operationId = "send_sms",
            description = "Send an SMS text message to a phone number",
            parameters = listOf(
                ToolParameter("phone", ParameterType.STRING, "Recipient phone number", required = true),
                ToolParameter("message", ParameterType.STRING, "Message text", required = true)
            ),
            examples = listOf(
                ToolExample("Text 555-1234 that I'm running late", "send_sms", mapOf("phone" to "555-1234", "message" to "I'm running late")),
                ToolExample("Send SMS to mom saying I love you", "send_sms", mapOf("phone" to "mom", "message" to "I love you"))
            )
        ),
        ToolOperation(
            operationId = "read_messages",
            description = "Read recent messages from a phone number or all messages",
            parameters = listOf(
                ToolParameter("phone", ParameterType.STRING, "Filter by phone number", required = false),
                ToolParameter("max_results", ParameterType.INTEGER, "Maximum messages to return", required = false, default = 20),
                ToolParameter("include_sent", ParameterType.BOOLEAN, "Include sent messages", required = false, default = true)
            ),
            examples = listOf(
                ToolExample("Read my messages", "read_messages", mapOf("max_results" to 10)),
                ToolExample("Show texts from 555-1234", "read_messages", mapOf("phone" to "555-1234"))
            )
        ),
        ToolOperation(
            operationId = "get_conversations",
            description = "List recent SMS conversations (threads)",
            parameters = listOf(
                ToolParameter("max_results", ParameterType.INTEGER, "Maximum conversations", required = false, default = 10)
            )
        ),
        ToolOperation(
            operationId = "open_conversation",
            description = "Open messaging app to compose or view a conversation",
            parameters = listOf(
                ToolParameter("phone", ParameterType.STRING, "Phone number", required = true),
                ToolParameter("message", ParameterType.STRING, "Pre-filled message (optional)", required = false)
            )
        )
    )
    
    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "send_sms" -> sendSms(params, context)
            "read_messages" -> readMessages(params, context)
            "get_conversations" -> getConversations(params, context)
            "open_conversation" -> openConversation(params, context)
            else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
        }
    }
    
    private fun sendSms(params: Map<String, Any?>, context: Context): ToolResponse {
        val phone = params["phone"] as? String
            ?: return ToolResponse.Error(moduleId, "send_sms", ErrorCode.INVALID_PARAMS, "phone required")
        val message = params["message"] as? String
            ?: return ToolResponse.Error(moduleId, "send_sms", ErrorCode.INVALID_PARAMS, "message required")
        
        if (message.length > 1600) {
            return ToolResponse.Error(moduleId, "send_sms", ErrorCode.INVALID_PARAMS, "Message too long (max 1600 chars)")
        }
        
        // Security: Require confirmation for sending messages
        return ToolResponse.Confirmation(
            moduleId = moduleId,
            operationId = "send_sms",
            message = "Send SMS to $phone?\n\n\"$message\"",
            pendingAction = mapOf("phone" to phone, "message" to message, "action" to "send_sms_confirmed")
        )
    }
    
    // Called after user confirmation
    fun sendSmsConfirmed(phone: String, message: String, context: Context): ToolResponse {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            
            // Split long messages
            val parts = smsManager.divideMessage(message)
            
            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
            
            Log.i(TAG, "SMS sent to $phone (${parts.size} parts)")
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "send_sms",
                message = "Message sent to $phone",
                data = mapOf("phone" to phone, "message_length" to message.length, "parts" to parts.size)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            return ToolResponse.Error(moduleId, "send_sms", ErrorCode.SYSTEM_ERROR, "Failed to send: ${e.message}")
        }
    }
    
    private fun readMessages(params: Map<String, Any?>, context: Context): ToolResponse {
        val phone = params["phone"] as? String
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 20
        val includeSent = params["include_sent"] as? Boolean ?: true
        
        try {
            val messages = mutableListOf<Map<String, Any?>>()
            
            // Query inbox
            val inboxUri = Telephony.Sms.Inbox.CONTENT_URI
            queryMessages(context, inboxUri, phone, maxResults, messages, "received")
            
            // Query sent
            if (includeSent && messages.size < maxResults) {
                val sentUri = Telephony.Sms.Sent.CONTENT_URI
                queryMessages(context, sentUri, phone, maxResults - messages.size, messages, "sent")
            }
            
            // Sort by date descending
            messages.sortByDescending { it["timestamp"] as Long }
            
            val truncated = if (messages.size > maxResults) {
                messages.take(maxResults)
            } else {
                messages
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "read_messages",
                message = "${truncated.size} message(s) found",
                data = mapOf("messages" to truncated, "filter_phone" to phone)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading messages", e)
            return ToolResponse.Error(moduleId, "read_messages", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun queryMessages(
        context: Context,
        uri: Uri,
        phone: String?,
        maxResults: Int,
        output: MutableList<Map<String, Any?>>,
        direction: String
    ) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )
        
        val selection = phone?.let { "${Telephony.Sms.ADDRESS} LIKE ?" }
        val selectionArgs = phone?.let { arrayOf("%${it.filter { c -> c.isDigit() }}%") }
        
        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < maxResults) {
                val timestamp = cursor.getLong(3)
                output.add(mapOf(
                    "id" to cursor.getLong(0).toString(),
                    "phone" to cursor.getString(1),
                    "body" to cursor.getString(2),
                    "timestamp" to timestamp,
                    "date" to dateFormat.format(Date(timestamp)),
                    "direction" to direction,
                    "read" to (cursor.getInt(4) == 1)
                ))
                count++
            }
        }
    }
    
    private fun getConversations(params: Map<String, Any?>, context: Context): ToolResponse {
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 10
        
        try {
            val conversations = mutableListOf<Map<String, Any?>>()
            
            context.contentResolver.query(
                Telephony.Sms.Conversations.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.Conversations.THREAD_ID,
                    Telephony.Sms.Conversations.SNIPPET,
                    Telephony.Sms.Conversations.MESSAGE_COUNT
                ),
                null,
                null,
                "date DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && conversations.size < maxResults) {
                    val threadId = cursor.getLong(0)
                    
                    // Get address for this thread
                    var address: String? = null
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(Telephony.Sms.ADDRESS),
                        "${Telephony.Sms.THREAD_ID} = ?",
                        arrayOf(threadId.toString()),
                        "${Telephony.Sms.DATE} DESC LIMIT 1"
                    )?.use { addrCursor ->
                        if (addrCursor.moveToFirst()) {
                            address = addrCursor.getString(0)
                        }
                    }
                    
                    conversations.add(mapOf(
                        "thread_id" to threadId.toString(),
                        "phone" to address,
                        "snippet" to cursor.getString(1),
                        "message_count" to cursor.getInt(2)
                    ))
                }
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "get_conversations",
                message = "${conversations.size} conversation(s)",
                data = mapOf("conversations" to conversations)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting conversations", e)
            return ToolResponse.Error(moduleId, "get_conversations", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun openConversation(params: Map<String, Any?>, context: Context): ToolResponse {
        val phone = params["phone"] as? String
            ?: return ToolResponse.Error(moduleId, "open_conversation", ErrorCode.INVALID_PARAMS, "phone required")
        val message = params["message"] as? String
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = if (message != null) {
                    Uri.parse("sms:$phone?body=${Uri.encode(message)}")
                } else {
                    Uri.parse("sms:$phone")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "open_conversation",
                message = "Opening conversation with $phone",
                data = mapOf("phone" to phone)
            )
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "open_conversation", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
}
