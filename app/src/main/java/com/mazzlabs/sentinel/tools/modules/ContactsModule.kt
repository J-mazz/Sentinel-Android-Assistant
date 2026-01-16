package com.mazzlabs.sentinel.tools.modules

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.mazzlabs.sentinel.tools.framework.*

/**
 * ContactsModule - Contact management via ContactsContract
 * 
 * Operations:
 * - search_contacts: Find contacts by name, phone, or email
 * - get_contact: Get full details of a contact
 * - create_contact: Add a new contact
 * - call_contact: Dial a contact's phone number
 * - text_contact: Open SMS to a contact
 */
class ContactsModule : ToolModule {
    
    companion object {
        private const val TAG = "ContactsModule"
    }
    
    override val moduleId = "contacts"
    
    override val description = "Find and manage contacts - lookup, call, text, create contacts"
    
    override val requiredPermissions = listOf(
        Manifest.permission.READ_CONTACTS
    )
    
    override val operations = listOf(
        ToolOperation(
            operationId = "search_contacts",
            description = "Search for contacts by name, phone number, or email",
            parameters = listOf(
                ToolParameter("query", ParameterType.STRING, "Search term (name, phone, or email)", required = true),
                ToolParameter("max_results", ParameterType.INTEGER, "Maximum results to return", required = false, default = 10)
            ),
            examples = listOf(
                ToolExample("Find John's number", "search_contacts", mapOf("query" to "John")),
                ToolExample("Look up mom's contact", "search_contacts", mapOf("query" to "mom")),
                ToolExample("Search for 555-1234", "search_contacts", mapOf("query" to "555-1234"))
            )
        ),
        ToolOperation(
            operationId = "get_contact",
            description = "Get full details of a specific contact",
            parameters = listOf(
                ToolParameter("contact_id", ParameterType.STRING, "Contact ID", required = true)
            )
        ),
        ToolOperation(
            operationId = "call_contact",
            description = "Initiate a phone call to a contact or number",
            parameters = listOf(
                ToolParameter("contact_id", ParameterType.STRING, "Contact ID to call", required = false),
                ToolParameter("phone", ParameterType.STRING, "Phone number to call (if no contact_id)", required = false),
                ToolParameter("name", ParameterType.STRING, "Contact name to search and call", required = false)
            ),
            examples = listOf(
                ToolExample("Call mom", "call_contact", mapOf("name" to "mom")),
                ToolExample("Call 555-1234", "call_contact", mapOf("phone" to "555-1234"))
            )
        ),
        ToolOperation(
            operationId = "text_contact",
            description = "Open SMS app to send a message",
            parameters = listOf(
                ToolParameter("contact_id", ParameterType.STRING, "Contact ID to text", required = false),
                ToolParameter("phone", ParameterType.STRING, "Phone number to text", required = false),
                ToolParameter("name", ParameterType.STRING, "Contact name to search and text", required = false),
                ToolParameter("message", ParameterType.STRING, "Pre-filled message (optional)", required = false)
            ),
            examples = listOf(
                ToolExample("Text Bob", "text_contact", mapOf("name" to "Bob")),
                ToolExample("Send message to mom saying I'll be late", "text_contact", mapOf("name" to "mom", "message" to "I'll be late"))
            )
        ),
        ToolOperation(
            operationId = "get_recent_contacts",
            description = "Get recently contacted people",
            parameters = listOf(
                ToolParameter("max_results", ParameterType.INTEGER, "Maximum results", required = false, default = 10)
            )
        )
    )
    
    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "search_contacts" -> searchContacts(params, context)
            "get_contact" -> getContact(params, context)
            "call_contact" -> callContact(params, context)
            "text_contact" -> textContact(params, context)
            "get_recent_contacts" -> getRecentContacts(params, context)
            else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
        }
    }
    
    private fun searchContacts(params: Map<String, Any?>, context: Context): ToolResponse {
        val query = params["query"] as? String
            ?: return ToolResponse.Error(moduleId, "search_contacts", ErrorCode.INVALID_PARAMS, "query required")
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 10
        
        try {
            val contacts = mutableListOf<Map<String, Any?>>()
            
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && contacts.size < maxResults) {
                    val contactId = cursor.getLong(0)
                    val name = cursor.getString(1)
                    val hasPhone = cursor.getInt(3) > 0
                    
                    // Get primary phone if available
                    var phone: String? = null
                    if (hasPhone) {
                        context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId.toString()),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                phone = phoneCursor.getString(0)
                            }
                        }
                    }
                    
                    contacts.add(mapOf(
                        "id" to contactId.toString(),
                        "name" to name,
                        "phone" to phone,
                        "has_phone" to hasPhone
                    ))
                }
            }
            
            // Also search by phone number
            if (contacts.size < maxResults && query.any { it.isDigit() }) {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    arrayOf("%$query%"),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext() && contacts.size < maxResults) {
                        val contactId = cursor.getLong(0).toString()
                        if (contacts.none { it["id"] == contactId }) {
                            contacts.add(mapOf(
                                "id" to contactId,
                                "name" to cursor.getString(1),
                                "phone" to cursor.getString(2),
                                "has_phone" to true
                            ))
                        }
                    }
                }
            }
            
            val message = if (contacts.isEmpty()) {
                "No contacts found for '$query'"
            } else {
                "${contacts.size} contact(s) found"
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "search_contacts",
                message = message,
                data = mapOf("contacts" to contacts, "query" to query)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contacts", e)
            return ToolResponse.Error(moduleId, "search_contacts", ErrorCode.SYSTEM_ERROR, "Search failed: ${e.message}")
        }
    }
    
    private fun getContact(params: Map<String, Any?>, context: Context): ToolResponse {
        val contactId = params["contact_id"] as? String
            ?: return ToolResponse.Error(moduleId, "get_contact", ErrorCode.INVALID_PARAMS, "contact_id required")
        
        try {
            // Get basic info
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            )
            
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
            
            var name: String? = null
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(1)
                }
            }
            
            if (name == null) {
                return ToolResponse.Error(moduleId, "get_contact", ErrorCode.NOT_FOUND, "Contact not found: $contactId")
            }
            
            // Get phone numbers
            val phones = mutableListOf<Map<String, String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        cursor.getInt(1),
                        ""
                    ).toString()
                    phones.add(mapOf("number" to cursor.getString(0), "type" to type))
                }
            }
            
            // Get emails
            val emails = mutableListOf<Map<String, String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = ContactsContract.CommonDataKinds.Email.getTypeLabel(
                        context.resources,
                        cursor.getInt(1),
                        ""
                    ).toString()
                    emails.add(mapOf("address" to cursor.getString(0), "type" to type))
                }
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "get_contact",
                message = "Contact: $name",
                data = mapOf(
                    "id" to contactId,
                    "name" to name,
                    "phones" to phones,
                    "emails" to emails
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting contact", e)
            return ToolResponse.Error(moduleId, "get_contact", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun callContact(params: Map<String, Any?>, context: Context): ToolResponse {
        val phone = resolvePhoneNumber(params, context)
            ?: return ToolResponse.Error(moduleId, "call_contact", ErrorCode.NOT_FOUND, "No phone number found. Provide contact_id, phone, or name")
        
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "call_contact",
                message = "Dialing $phone",
                data = mapOf("phone" to phone)
            )
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "call_contact", ErrorCode.SYSTEM_ERROR, "Failed to dial: ${e.message}")
        }
    }
    
    private fun textContact(params: Map<String, Any?>, context: Context): ToolResponse {
        val phone = resolvePhoneNumber(params, context)
            ?: return ToolResponse.Error(moduleId, "text_contact", ErrorCode.NOT_FOUND, "No phone number found")
        
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
                operationId = "text_contact",
                message = "Opening SMS to $phone",
                data = mapOf("phone" to phone, "prefilled_message" to message)
            )
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "text_contact", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun getRecentContacts(params: Map<String, Any?>, context: Context): ToolResponse {
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 10
        
        try {
            val contacts = mutableListOf<Map<String, Any?>>()
            
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.LAST_TIME_CONTACTED
                ),
                "${ContactsContract.Contacts.LAST_TIME_CONTACTED} > 0",
                null,
                "${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && contacts.size < maxResults) {
                    contacts.add(mapOf(
                        "id" to cursor.getLong(0).toString(),
                        "name" to cursor.getString(1),
                        "last_contacted" to cursor.getLong(2)
                    ))
                }
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "get_recent_contacts",
                message = "${contacts.size} recent contact(s)",
                data = mapOf("contacts" to contacts)
            )
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "get_recent_contacts", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun resolvePhoneNumber(params: Map<String, Any?>, context: Context): String? {
        // Direct phone number
        (params["phone"] as? String)?.let { return it }
        
        // By contact ID
        (params["contact_id"] as? String)?.let { contactId ->
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        }
        
        // By name search
        (params["name"] as? String)?.let { name ->
            val searchResult = searchContacts(mapOf("query" to name, "max_results" to 1), context)
            if (searchResult is ToolResponse.Success) {
                @Suppress("UNCHECKED_CAST")
                val contacts = searchResult.data["contacts"] as? List<Map<String, Any?>>
                contacts?.firstOrNull()?.get("phone")?.let { return it as String }
            }
        }
        
        return null
    }
}
