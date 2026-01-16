package com.mazzlabs.sentinel.core

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for AgentController
 * Note: AgentController has dependencies on SentinelApplication and NativeBridge
 * which makes full integration testing difficult. These tests focus on the
 * data classes and formatResult functionality.
 */
class AgentControllerTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("sentinel_test")
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.packageName } returns "com.mazzlabs.sentinel.test"
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `AgentResult ToolResult holds response`() {
        val response = ToolResponse.Success(
            moduleId = "calendar",
            operationId = "read_events",
            message = "Found 2 events",
            data = mapOf("events" to listOf<Any>())
        )
        
        val result = AgentController.AgentResult.ToolResult(response)
        
        assertThat(result.response).isEqualTo(response)
    }

    @Test
    fun `AgentResult UIAction holds action`() {
        // Test that data class properly holds values
        val uiResult = AgentController.AgentResult.UIAction(
            mockk(relaxed = true)
        )
        
        assertThat(uiResult).isInstanceOf(AgentController.AgentResult.UIAction::class.java)
    }

    @Test
    fun `AgentResult Response holds message`() {
        val result = AgentController.AgentResult.Response("No applicable tool")
        
        assertThat(result.message).isEqualTo("No applicable tool")
    }

    @Test
    fun `AgentResult Error holds message`() {
        val result = AgentController.AgentResult.Error("Something went wrong")
        
        assertThat(result.message).isEqualTo("Something went wrong")
    }

    @Test
    fun `AgentResult types are distinguishable`() {
        val toolResult = AgentController.AgentResult.ToolResult(
            ToolResponse.Success("mod", "op", "msg")
        )
        val response = AgentController.AgentResult.Response("text")
        val error = AgentController.AgentResult.Error("err")
        
        assertThat(toolResult).isInstanceOf(AgentController.AgentResult.ToolResult::class.java)
        assertThat(response).isInstanceOf(AgentController.AgentResult.Response::class.java)
        assertThat(error).isInstanceOf(AgentController.AgentResult.Error::class.java)
    }

    @Test
    fun `ToolResponse Success can be wrapped in AgentResult`() {
        val response = ToolResponse.Success(
            moduleId = "notes",
            operationId = "create_note",
            message = "Note created",
            data = mapOf("id" to "123")
        )
        
        val result = AgentController.AgentResult.ToolResult(response)
        
        val unwrapped = result.response as ToolResponse.Success
        assertThat(unwrapped.moduleId).isEqualTo("notes")
        assertThat(unwrapped.operationId).isEqualTo("create_note")
        assertThat(unwrapped.message).isEqualTo("Note created")
    }

    @Test
    fun `ToolResponse Error can be wrapped in AgentResult`() {
        val response = ToolResponse.Error(
            moduleId = "calendar",
            operationId = "delete_event",
            errorCode = ErrorCode.NOT_FOUND,
            message = "Event not found"
        )
        
        val result = AgentController.AgentResult.ToolResult(response)
        
        val unwrapped = result.response as ToolResponse.Error
        assertThat(unwrapped.errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `ToolResponse PermissionRequired can be wrapped in AgentResult`() {
        val response = ToolResponse.PermissionRequired(
            moduleId = "contacts",
            operationId = "search_contacts",
            permissions = listOf("android.permission.READ_CONTACTS")
        )
        
        val result = AgentController.AgentResult.ToolResult(response)
        
        val unwrapped = result.response as ToolResponse.PermissionRequired
        assertThat(unwrapped.permissions).contains("android.permission.READ_CONTACTS")
    }

    @Test
    fun `ToolResponse Confirmation can be wrapped in AgentResult`() {
        val response = ToolResponse.Confirmation(
            moduleId = "messaging",
            operationId = "send_sms",
            message = "Send SMS to +1234567890?",
            pendingAction = mapOf("phone" to "+1234567890")
        )
        
        val result = AgentController.AgentResult.ToolResult(response)
        
        val unwrapped = result.response as ToolResponse.Confirmation
        assertThat(unwrapped.message).contains("Send SMS")
    }
}
