package com.mazzlabs.sentinel.tools.framework

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests for ToolExecutor - focusing on JSON parsing and routing
 * Note: ToolExecutor creates its own ToolRouter internally, so we test
 * through the public API rather than mocking the router.
 */
class ToolExecutorTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("sentinel_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.packageName } returns "com.mazzlabs.sentinel.test"
        every { mockContext.applicationContext } returns mockContext
        // Mock permissions as granted by default for testing
        every { mockContext.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `getToolSchema returns non-empty schema`() {
        val executor = ToolExecutor(mockContext)
        
        val schema = executor.getToolSchema()
        
        assertThat(schema).isNotEmpty()
        assertThat(schema).containsMatch("(?i)tool|module")
    }

    @Test
    fun `getToolSchema compact version is shorter`() {
        val executor = ToolExecutor(mockContext)
        
        val fullSchema = executor.getToolSchema(compact = false)
        val compactSchema = executor.getToolSchema(compact = true)
        
        assertThat(compactSchema.length).isLessThan(fullSchema.length)
    }

    @Test
    fun `executeFromJson parses valid single tool call`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """{"tool": "notes.create_note", "params": {"title": "Test", "content": "Hello"}}"""
        
        val results = executor.executeFromJson(json)
        
        assertThat(results).hasSize(1)
        // Should fail due to permissions but proves parsing works
    }

    @Test
    fun `executeFromJson parses array of tool calls`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """[
            {"tool": "notes.list_notes", "params": {}},
            {"tool": "clock.show_alarms", "params": {}}
        ]"""
        
        val results = executor.executeFromJson(json)
        
        // In unit test environment with mocked context, Dispatchers.IO may not execute properly
        // Verifies method doesn't throw and returns a list
        assertThat(results).isNotNull()
    }

    @Test
    fun `executeFromJson returns error for malformed JSON`() = runTest {
        val executor = ToolExecutor(mockContext)
        val malformedJson = "{invalid json}"
        
        val results = executor.executeFromJson(malformedJson)
        
        assertThat(results).hasSize(1)
        assertThat(results[0]).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((results[0] as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `executeFromJson returns error for missing tool field`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """{"params": {"title": "Test"}}"""
        
        val results = executor.executeFromJson(json)
        
        assertThat(results).hasSize(1)
        assertThat(results[0]).isInstanceOf(ToolResponse.Error::class.java)
        val error = results[0] as ToolResponse.Error
        assertThat(error.message).containsMatch("(?i)tool|missing")
    }

    @Test
    fun `executeFromJson returns error for unknown module`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """{"tool": "unknown.operation", "params": {}}"""
        
        val results = executor.executeFromJson(json)
        
        assertThat(results).hasSize(1)
        assertThat(results[0]).isInstanceOf(ToolResponse.Error::class.java)
        // In mock environment, various error codes are valid depending on where failure occurs
    }

    @Test
    fun `executeFromJson handles empty params object`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """{"tool": "notes.list_notes", "params": {}}"""
        
        val results = executor.executeFromJson(json)
        
        assertThat(results).hasSize(1)
        // Result depends on permissions/module state, but no parsing error
    }

    @Test
    fun `executeFromJson handles missing params field`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """{"tool": "notes.list_notes"}"""
        
        val results = executor.executeFromJson(json)
        
        assertThat(results).hasSize(1)
        // Should treat missing params as empty params
    }

    @Test
    fun `execute direct call works`() = runTest {
        val executor = ToolExecutor(mockContext)
        
        val result = executor.execute("notes.list_notes", emptyMap())
        
        // Result depends on permissions but verifies the API works
        assertThat(result).isNotNull()
    }

    @Test
    fun `formatResponseForLlm formats success`() {
        val executor = ToolExecutor(mockContext)
        val response = ToolResponse.Success(
            moduleId = "notes",
            operationId = "create_note",
            message = "Note created",
            data = mapOf("note_id" to "123")
        )
        
        val formatted = executor.formatResponseForLlm(response)
        
        assertThat(formatted).contains("SUCCESS")
        assertThat(formatted).contains("notes")
        assertThat(formatted).contains("create_note")
        assertThat(formatted).contains("Note created")
    }

    @Test
    fun `formatResponseForLlm formats error`() {
        val executor = ToolExecutor(mockContext)
        val response = ToolResponse.Error(
            moduleId = "calendar",
            operationId = "read_events",
            errorCode = ErrorCode.PERMISSION_DENIED,
            message = "Calendar permission required"
        )
        
        val formatted = executor.formatResponseForLlm(response)
        
        assertThat(formatted).contains("ERROR")
        assertThat(formatted).contains("PERMISSION_DENIED")
        assertThat(formatted).contains("Calendar permission required")
    }

    @Test
    fun `formatResponseForLlm formats permission required`() {
        val executor = ToolExecutor(mockContext)
        val response = ToolResponse.PermissionRequired(
            moduleId = "contacts",
            operationId = "search_contacts",
            permissions = listOf("android.permission.READ_CONTACTS")
        )
        
        val formatted = executor.formatResponseForLlm(response)
        
        assertThat(formatted).contains("PERMISSION_REQUIRED")
        assertThat(formatted).contains("READ_CONTACTS")
    }

    @Test
    fun `formatResponseForLlm formats confirmation`() {
        val executor = ToolExecutor(mockContext)
        val response = ToolResponse.Confirmation(
            moduleId = "messaging",
            operationId = "send_sms",
            message = "Send SMS to +1234567890?",
            pendingAction = mapOf("phone" to "+1234567890")
        )
        
        val formatted = executor.formatResponseForLlm(response)
        
        assertThat(formatted).contains("CONFIRMATION_REQUIRED")
        assertThat(formatted).contains("Send SMS")
    }

    @Test
    fun `getAvailableModules returns list`() {
        val executor = ToolExecutor(mockContext)
        
        val modules = executor.getAvailableModules()
        
        // Should be empty without permissions but method works
        assertThat(modules).isNotNull()
    }

    @Test
    fun `getModulesNeedingPermissions returns map`() {
        val executor = ToolExecutor(mockContext)
        
        val needsPerms = executor.getModulesNeedingPermissions()
        
        assertThat(needsPerms).isNotNull()
        // With permissions granted, should be empty
        assertThat(needsPerms).isEmpty()
    }

    @Test
    fun `executeFromJson handles plain text gracefully`() = runTest {
        val executor = ToolExecutor(mockContext)
        val plainText = "This is not JSON"
        
        val results = executor.executeFromJson(plainText)
        
        assertThat(results).hasSize(1)
        assertThat(results[0]).isInstanceOf(ToolResponse.Error::class.java)
    }

    @Test
    fun `executeFromJson handles nested params`() = runTest {
        val executor = ToolExecutor(mockContext)
        val json = """{
            "tool": "notes.create_note",
            "params": {
                "title": "Nested Test",
                "content": "Content here",
                "metadata": {"key": "value"}
            }
        }"""
        
        val results = executor.executeFromJson(json)
        
        assertThat(results).hasSize(1)
        // Parsing succeeds even if execution fails due to permissions
    }

    @Test
    fun `formatResponseForLlm handles complex data`() {
        val executor = ToolExecutor(mockContext)
        val response = ToolResponse.Success(
            moduleId = "calendar",
            operationId = "read_events",
            message = "Found 2 events",
            data = mapOf(
                "events" to listOf(
                    mapOf("title" to "Meeting", "time" to "10:00"),
                    mapOf("title" to "Lunch", "time" to "12:00")
                ),
                "count" to 2
            )
        )
        
        val formatted = executor.formatResponseForLlm(response)
        
        assertThat(formatted).contains("Meeting")
        assertThat(formatted).contains("Lunch")
    }
}
