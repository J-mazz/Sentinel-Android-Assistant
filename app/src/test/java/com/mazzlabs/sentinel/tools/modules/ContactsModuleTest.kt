package com.mazzlabs.sentinel.tools.modules

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.ContactsContract
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ParameterType
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ContactsModuleTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockResources: Resources
    private lateinit var contactsModule: ContactsModule

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockResources = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
        every { mockContext.resources } returns mockResources
        contactsModule = ContactsModule()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `moduleId is contacts`() {
        assertThat(contactsModule.moduleId).isEqualTo("contacts")
    }

    @Test
    fun `description mentions contacts`() {
        assertThat(contactsModule.description).containsMatch("(?i)contact")
    }

    @Test
    fun `requiredPermissions includes read contacts`() {
        assertThat(contactsModule.requiredPermissions).contains(
            "android.permission.READ_CONTACTS"
        )
    }

    @Test
    fun `operations contains expected operations`() {
        val opIds = contactsModule.operations.map { it.operationId }
        assertThat(opIds).containsAtLeast(
            "search_contacts",
            "get_contact",
            "call_contact",
            "text_contact"
        )
    }

    @Test
    fun `search_contacts requires query`() = runTest {
        val result = contactsModule.execute("search_contacts", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
        assertThat(result.message).contains("query")
    }

    @Test
    fun `search_contacts returns empty list when cursor is null`() = runTest {
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null
        
        val result = contactsModule.execute(
            "search_contacts",
            mapOf("query" to "NonexistentName"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val contacts = success.data["contacts"] as List<*>
        assertThat(contacts).isEmpty()
    }

    @Test
    fun `get_contact requires contact_id`() = runTest {
        val result = contactsModule.execute("get_contact", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
        assertThat(result.message).contains("contact_id")
    }

    @Test
    fun `get_contact returns not found for invalid id`() = runTest {
        // When cursor returns null (no contact found), implementation should return error
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null
        
        val result = contactsModule.execute(
            "get_contact",
            mapOf("contact_id" to "999"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        val error = result as ToolResponse.Error
        // Could be NOT_FOUND or SYSTEM_ERROR depending on how null is handled
        assertThat(error.errorCode).isAnyOf(ErrorCode.NOT_FOUND, ErrorCode.SYSTEM_ERROR)
    }

    @Test
    fun `call_contact returns error without any identifier`() = runTest {
        // No contact_id, phone, or name provided
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null
        
        val result = contactsModule.execute("call_contact", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `call_contact with phone starts dialer`() = runTest {
        every { mockContext.startActivity(any()) } just runs
        
        val result = contactsModule.execute(
            "call_contact",
            mapOf("phone" to "+1234567890"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        verify { mockContext.startActivity(any()) }
    }

    @Test
    fun `text_contact returns error without identifier`() = runTest {
        every { mockContentResolver.query(any(), any(), any(), any(), any()) } returns null
        
        val result = contactsModule.execute(
            "text_contact",
            mapOf("message" to "Hello"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `text_contact with phone opens messaging`() = runTest {
        every { mockContext.startActivity(any()) } just runs
        
        val result = contactsModule.execute(
            "text_contact",
            mapOf("phone" to "+1234567890", "message" to "Hello!"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        verify { mockContext.startActivity(any()) }
    }

    @Test
    fun `execute returns error for unknown operation`() = runTest {
        val result = contactsModule.execute("unknown_op", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `search_contacts operation has correct parameters`() {
        val searchOp = contactsModule.operations.find { it.operationId == "search_contacts" }!!
        
        val queryParam = searchOp.parameters.find { it.name == "query" }
        assertThat(queryParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(queryParam?.required).isTrue()
    }

    @Test
    fun `call_contact operation parameters are all optional`() {
        val callOp = contactsModule.operations.find { it.operationId == "call_contact" }!!
        
        // All of contact_id, phone, name should be optional 
        val allOptional = callOp.parameters.all { !it.required }
        assertThat(allOptional).isTrue()
    }

    @Test
    fun `text_contact operation parameters are all optional`() {
        val textOp = contactsModule.operations.find { it.operationId == "text_contact" }!!
        
        val allOptional = textOp.parameters.all { !it.required }
        assertThat(allOptional).isTrue()
    }
}
