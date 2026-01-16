package com.mazzlabs.sentinel.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log

/**
 * PhoneCallTool - Initiate phone calls to contacts
 */
class PhoneCallTool : Tool {
    override val name = "phone_call"
    override val description = "Call a contact by name or phone number"
    override val parametersSchema = """
        {
            "contact_name": "string (optional - name to search for)",
            "phone_number": "string (optional - direct number to call)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS
    )

    override fun validateParams(params: Map<String, Any?>): ValidationResult {
        val contactName = params["contact_name"] as? String
        val phoneNumber = params["phone_number"] as? String
        
        if (contactName.isNullOrBlank() && phoneNumber.isNullOrBlank()) {
            return ValidationResult.Invalid("Either contact_name or phone_number is required")
        }
        return ValidationResult.Valid
    }

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        val validation = validateParams(params)
        if (validation is ValidationResult.Invalid) {
            return ToolResult.Failure(name, validation.reason, ErrorCode.INVALID_PARAMS)
        }
        
        return try {
            val contactName = params["contact_name"] as? String
            val phoneNumber = params["phone_number"] as? String
            
            val numberToCall = if (!phoneNumber.isNullOrBlank()) {
                phoneNumber
            } else if (!contactName.isNullOrBlank()) {
                lookupContactNumber(context, contactName)
                    ?: return ToolResult.Failure(name, "Contact '$contactName' not found", ErrorCode.NOT_FOUND)
            } else {
                return ToolResult.Failure(name, "No contact or number provided", ErrorCode.INVALID_PARAMS)
            }
            
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$numberToCall")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                
                ToolResult.Success(
                    toolName = name,
                    message = "Calling ${contactName ?: numberToCall}",
                    data = mapOf("number" to numberToCall, "contact" to contactName)
                )
            } else {
                ToolResult.Failure(name, "No phone app found", ErrorCode.NOT_FOUND)
            }
        } catch (e: SecurityException) {
            ToolResult.Failure(name, "Phone/contacts permission denied", ErrorCode.PERMISSION_DENIED)
        } catch (e: Exception) {
            Log.e("PhoneCallTool", "Error making call", e)
            ToolResult.Failure(name, "Failed to make call: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
    
    private fun lookupContactNumber(context: Context, name: String): String? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }
}

/**
 * ContactLookupTool - Search contacts
 */
class ContactLookupTool : Tool {
    override val name = "contact_lookup"
    override val description = "Search for contacts by name"
    override val parametersSchema = """
        {
            "query": "string (required - name to search for)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)

    override fun validateParams(params: Map<String, Any?>): ValidationResult {
        if ((params["query"] as? String).isNullOrBlank()) {
            return ValidationResult.Invalid("query is required")
        }
        return ValidationResult.Valid
    }

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        val validation = validateParams(params)
        if (validation is ValidationResult.Invalid) {
            return ToolResult.Failure(name, validation.reason, ErrorCode.INVALID_PARAMS)
        }
        
        return try {
            val query = params["query"] as String
            val contacts = mutableListOf<Map<String, String?>>()
            
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && contacts.size < 10) {
                    contacts.add(mapOf(
                        "name" to cursor.getString(0),
                        "number" to cursor.getString(1),
                        "type" to getPhoneTypeLabel(cursor.getInt(2))
                    ))
                }
            }
            
            ToolResult.Success(
                toolName = name,
                message = "Found ${contacts.size} contacts matching '$query'",
                data = mapOf("contacts" to contacts)
            )
        } catch (e: SecurityException) {
            ToolResult.Failure(name, "Contacts permission denied", ErrorCode.PERMISSION_DENIED)
        } catch (e: Exception) {
            Log.e("ContactLookupTool", "Error searching contacts", e)
            ToolResult.Failure(name, "Failed to search contacts: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
    
    private fun getPhoneTypeLabel(type: Int): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            else -> "Other"
        }
    }
}

/**
 * SmsSendTool - Send SMS messages
 */
class SmsSendTool : Tool {
    override val name = "sms_send"
    override val description = "Send an SMS message to a contact or number"
    override val parametersSchema = """
        {
            "contact_name": "string (optional - name to search for)",
            "phone_number": "string (optional - direct number)",
            "message": "string (required - message to send)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun validateParams(params: Map<String, Any?>): ValidationResult {
        val contactName = params["contact_name"] as? String
        val phoneNumber = params["phone_number"] as? String
        val message = params["message"] as? String
        
        if (contactName.isNullOrBlank() && phoneNumber.isNullOrBlank()) {
            return ValidationResult.Invalid("Either contact_name or phone_number is required")
        }
        if (message.isNullOrBlank()) {
            return ValidationResult.Invalid("message is required")
        }
        return ValidationResult.Valid
    }

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        val validation = validateParams(params)
        if (validation is ValidationResult.Invalid) {
            return ToolResult.Failure(name, validation.reason, ErrorCode.INVALID_PARAMS)
        }
        
        return try {
            val contactName = params["contact_name"] as? String
            val phoneNumber = params["phone_number"] as? String
            val message = params["message"] as String
            
            val numberToUse = if (!phoneNumber.isNullOrBlank()) {
                phoneNumber
            } else if (!contactName.isNullOrBlank()) {
                lookupContactNumber(context, contactName)
                    ?: return ToolResult.Failure(name, "Contact '$contactName' not found", ErrorCode.NOT_FOUND)
            } else {
                return ToolResult.Failure(name, "No contact or number provided", ErrorCode.INVALID_PARAMS)
            }
            
            // Use SMS intent for user confirmation (safer than direct send)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$numberToUse")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                
                ToolResult.Success(
                    toolName = name,
                    message = "Opening SMS to ${contactName ?: numberToUse}",
                    data = mapOf(
                        "number" to numberToUse,
                        "contact" to contactName,
                        "message_preview" to message.take(50)
                    )
                )
            } else {
                ToolResult.Failure(name, "No SMS app found", ErrorCode.NOT_FOUND)
            }
        } catch (e: SecurityException) {
            ToolResult.Failure(name, "SMS/contacts permission denied", ErrorCode.PERMISSION_DENIED)
        } catch (e: Exception) {
            Log.e("SmsSendTool", "Error sending SMS", e)
            ToolResult.Failure(name, "Failed to send SMS: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
    
    private fun lookupContactNumber(context: Context, name: String): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }
}
