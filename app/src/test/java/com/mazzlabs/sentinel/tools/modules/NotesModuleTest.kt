package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ParameterType
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NotesModuleTest {

    private lateinit var mockContext: Context
    private lateinit var notesModule: NotesModule
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("sentinel_test_notes").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        notesModule = NotesModule()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `moduleId is notes`() {
        assertThat(notesModule.moduleId).isEqualTo("notes")
    }

    @Test
    fun `description mentions notes`() {
        assertThat(notesModule.description).containsMatch("(?i)note")
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(notesModule.requiredPermissions).isEmpty()
    }

    @Test
    fun `operations contains expected operations`() {
        val opIds = notesModule.operations.map { it.operationId }
        assertThat(opIds).containsAtLeast(
            "create_note",
            "read_note",
            "list_notes",
            "append_note",
            "delete_note",
            "search_notes"
        )
    }

    @Test
    fun `create_note requires title`() = runTest {
        val result = notesModule.execute(
            "create_note",
            mapOf("content" to "Test content"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
        assertThat(result.message).contains("title")
    }

    @Test
    fun `create_note requires content`() = runTest {
        val result = notesModule.execute(
            "create_note",
            mapOf("title" to "Test"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
        assertThat(result.message).contains("content")
    }

    @Test
    fun `create_note creates file successfully`() = runTest {
        val result = notesModule.execute(
            "create_note",
            mapOf("title" to "My Note", "content" to "Hello World"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.message).contains("My Note")
        
        // Verify file was created
        val notesDir = File(tempDir, "sentinel_notes")
        assertThat(notesDir.exists()).isTrue()
        val noteFile = File(notesDir, "My Note.txt")
        assertThat(noteFile.exists()).isTrue()
        assertThat(noteFile.readText()).contains("Hello World")
    }

    @Test
    fun `create_note includes tags in file`() = runTest {
        val result = notesModule.execute(
            "create_note",
            mapOf(
                "title" to "Tagged Note",
                "content" to "Content here",
                "tags" to listOf("work", "important")
            ),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        
        val noteFile = File(tempDir, "sentinel_notes/Tagged Note.txt")
        val content = noteFile.readText()
        assertThat(content).contains("Tags: work, important")
    }

    @Test
    fun `read_note requires title`() = runTest {
        val result = notesModule.execute("read_note", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `read_note returns not found for missing note`() = runTest {
        val result = notesModule.execute(
            "read_note",
            mapOf("title" to "Nonexistent"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `read_note returns content of existing note`() = runTest {
        // Create note first
        notesModule.execute(
            "create_note",
            mapOf("title" to "Readable", "content" to "Test content"),
            mockContext
        )
        
        val result = notesModule.execute(
            "read_note",
            mapOf("title" to "Readable"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.data["content"] as String).contains("Test content")
    }

    @Test
    fun `list_notes returns empty when no notes`() = runTest {
        val result = notesModule.execute("list_notes", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val notes = success.data["notes"] as List<Map<String, Any?>>
        assertThat(notes).isEmpty()
    }

    @Test
    fun `list_notes returns created notes`() = runTest {
        // Create some notes
        notesModule.execute("create_note", mapOf("title" to "Note1", "content" to "C1"), mockContext)
        notesModule.execute("create_note", mapOf("title" to "Note2", "content" to "C2"), mockContext)
        
        val result = notesModule.execute("list_notes", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val notes = success.data["notes"] as List<Map<String, Any?>>
        assertThat(notes).hasSize(2)
    }

    @Test
    fun `append_note requires title`() = runTest {
        val result = notesModule.execute(
            "append_note",
            mapOf("content" to "More content"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `append_note requires content`() = runTest {
        val result = notesModule.execute(
            "append_note",
            mapOf("title" to "Test"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `append_note returns not found for missing note`() = runTest {
        val result = notesModule.execute(
            "append_note",
            mapOf("title" to "Missing", "content" to "Extra"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `append_note adds content to existing note`() = runTest {
        // Create note
        notesModule.execute(
            "create_note",
            mapOf("title" to "Appendable", "content" to "Original"),
            mockContext
        )
        
        // Append
        val result = notesModule.execute(
            "append_note",
            mapOf("title" to "Appendable", "content" to "Appended"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        
        // Verify content
        val noteFile = File(tempDir, "sentinel_notes/Appendable.txt")
        val content = noteFile.readText()
        assertThat(content).contains("Original")
        assertThat(content).contains("Appended")
    }

    @Test
    fun `delete_note requires title`() = runTest {
        val result = notesModule.execute("delete_note", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `delete_note returns not found for missing note`() = runTest {
        val result = notesModule.execute(
            "delete_note",
            mapOf("title" to "Missing"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `delete_note removes existing note`() = runTest {
        // Create note
        notesModule.execute(
            "create_note",
            mapOf("title" to "Deletable", "content" to "Soon gone"),
            mockContext
        )
        
        val noteFile = File(tempDir, "sentinel_notes/Deletable.txt")
        assertThat(noteFile.exists()).isTrue()
        
        // Delete
        val result = notesModule.execute(
            "delete_note",
            mapOf("title" to "Deletable"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        assertThat(noteFile.exists()).isFalse()
    }

    @Test
    fun `search_notes requires query`() = runTest {
        val result = notesModule.execute("search_notes", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `search_notes finds matching notes`() = runTest {
        // Create notes
        notesModule.execute("create_note", mapOf("title" to "Shopping", "content" to "Buy milk and eggs"), mockContext)
        notesModule.execute("create_note", mapOf("title" to "Work", "content" to "Finish report"), mockContext)
        
        val result = notesModule.execute(
            "search_notes",
            mapOf("query" to "milk"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val results = success.data["results"] as List<Map<String, Any?>>
        assertThat(results).hasSize(1)
        assertThat(results[0]["title"]).isEqualTo("Shopping")
    }

    @Test
    fun `search_notes is case insensitive`() = runTest {
        notesModule.execute("create_note", mapOf("title" to "Test", "content" to "UPPERCASE word"), mockContext)
        
        val result = notesModule.execute(
            "search_notes",
            mapOf("query" to "uppercase"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val results = success.data["results"] as List<Map<String, Any?>>
        assertThat(results).hasSize(1)
    }

    @Test
    fun `execute returns error for unknown operation`() = runTest {
        val result = notesModule.execute("unknown_op", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `create_note operation has correct parameter types`() {
        val createOp = notesModule.operations.find { it.operationId == "create_note" }!!
        
        val titleParam = createOp.parameters.find { it.name == "title" }
        assertThat(titleParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(titleParam?.required).isTrue()
        
        val contentParam = createOp.parameters.find { it.name == "content" }
        assertThat(contentParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(contentParam?.required).isTrue()
        
        val tagsParam = createOp.parameters.find { it.name == "tags" }
        assertThat(tagsParam?.type).isEqualTo(ParameterType.ARRAY)
        assertThat(tagsParam?.required).isFalse()
    }
}
