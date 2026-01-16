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
import java.io.ByteArrayInputStream
import java.io.File

class TerminalModuleTest {

    private lateinit var mockContext: Context
    private lateinit var terminalModule: TerminalModule
    private lateinit var tempDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("sentinel_test_files")
        cacheDir = createTempDir("sentinel_test_cache")
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.getExternalFilesDir(any()) } returns null
        every { mockContext.packageName } returns "com.mazzlabs.sentinel.test"
        terminalModule = TerminalModule()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        cacheDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `moduleId is terminal`() {
        assertThat(terminalModule.moduleId).isEqualTo("terminal")
    }

    @Test
    fun `description mentions shell commands`() {
        assertThat(terminalModule.description).containsMatch("(?i)shell|command")
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(terminalModule.requiredPermissions).isEmpty()
    }

    @Test
    fun `operations contains expected operations`() {
        val opIds = terminalModule.operations.map { it.operationId }
        assertThat(opIds).containsAtLeast(
            "execute",
            "list_files",
            "read_file",
            "write_file",
            "get_env"
        )
    }

    @Test
    fun `execute operation requires command`() = runTest {
        val result = terminalModule.execute("execute", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
        assertThat(result.message).contains("command")
    }

    @Test
    fun `execute runs simple command`() = runTest {
        val result = terminalModule.execute(
            "execute",
            mapOf("command" to "echo hello"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.data["output"] as String).contains("hello")
        assertThat(success.data["exit_code"]).isEqualTo(0)
    }

    @Test
    fun `execute captures exit code for failed command`() = runTest {
        val result = terminalModule.execute(
            "execute",
            mapOf("command" to "exit 42"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.data["exit_code"]).isEqualTo(42)
    }

    @Test
    fun `execute blocks dangerous su command`() = runTest {
        val result = terminalModule.execute(
            "execute",
            mapOf("command" to "su -c whoami"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.PERMISSION_DENIED)
        assertThat(result.message).containsMatch("(?i)blocked")
    }

    @Test
    fun `execute blocks rm -rf root`() = runTest {
        val result = terminalModule.execute(
            "execute",
            mapOf("command" to "rm -rf /"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.PERMISSION_DENIED)
    }

    @Test
    fun `list_files requires path`() = runTest {
        val result = terminalModule.execute("list_files", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `list_files returns not found for missing path`() = runTest {
        val result = terminalModule.execute(
            "list_files",
            mapOf("path" to "/nonexistent/path"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `list_files lists directory contents`() = runTest {
        // Create some files
        File(tempDir, "file1.txt").writeText("content1")
        File(tempDir, "file2.txt").writeText("content2")
        File(tempDir, "subdir").mkdir()
        
        val result = terminalModule.execute(
            "list_files",
            mapOf("path" to tempDir.absolutePath),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val files = success.data["files"] as List<Map<String, Any?>>
        assertThat(files.map { it["name"] }).containsAtLeast("file1.txt", "file2.txt", "subdir/")
    }

    @Test
    fun `list_files hides hidden files by default`() = runTest {
        File(tempDir, ".hidden").writeText("secret")
        File(tempDir, "visible.txt").writeText("public")
        
        val result = terminalModule.execute(
            "list_files",
            mapOf("path" to tempDir.absolutePath),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val files = success.data["files"] as List<Map<String, Any?>>
        val names = files.map { it["name"] }
        assertThat(names).contains("visible.txt")
        assertThat(names).doesNotContain(".hidden")
    }

    @Test
    fun `list_files shows hidden files when requested`() = runTest {
        File(tempDir, ".hidden").writeText("secret")
        
        val result = terminalModule.execute(
            "list_files",
            mapOf("path" to tempDir.absolutePath, "show_hidden" to true),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val files = success.data["files"] as List<Map<String, Any?>>
        assertThat(files.map { it["name"] }).contains(".hidden")
    }

    @Test
    fun `list_files with details includes size`() = runTest {
        File(tempDir, "test.txt").writeText("12345")
        
        val result = terminalModule.execute(
            "list_files",
            mapOf("path" to tempDir.absolutePath, "details" to true),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        @Suppress("UNCHECKED_CAST")
        val files = success.data["files"] as List<Map<String, Any?>>
        val testFile = files.find { (it["name"] as String).contains("test.txt") }
        assertThat(testFile).isNotNull()
        assertThat(testFile!!["size"]).isEqualTo(5L)
    }

    @Test
    fun `read_file requires path`() = runTest {
        val result = terminalModule.execute("read_file", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `read_file returns not found for missing file`() = runTest {
        val result = terminalModule.execute(
            "read_file",
            mapOf("path" to "/nonexistent/file.txt"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `read_file returns file content`() = runTest {
        val testFile = File(tempDir, "readable.txt")
        testFile.writeText("Line 1\nLine 2\nLine 3")
        
        val result = terminalModule.execute(
            "read_file",
            mapOf("path" to testFile.absolutePath),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.data["content"] as String).contains("Line 1")
        assertThat(success.data["total_lines"]).isEqualTo(3)
    }

    @Test
    fun `read_file respects max_lines`() = runTest {
        val testFile = File(tempDir, "longfile.txt")
        testFile.writeText((1..100).joinToString("\n") { "Line $it" })
        
        val result = terminalModule.execute(
            "read_file",
            mapOf("path" to testFile.absolutePath, "max_lines" to 5),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.data["lines_read"]).isEqualTo(5)
        assertThat((success.data["content"] as String).lines()).hasSize(5)
    }

    @Test
    fun `read_file respects offset`() = runTest {
        val testFile = File(tempDir, "offsetfile.txt")
        testFile.writeText("Line 1\nLine 2\nLine 3\nLine 4\nLine 5")
        
        val result = terminalModule.execute(
            "read_file",
            mapOf("path" to testFile.absolutePath, "offset" to 2, "max_lines" to 2),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        val content = success.data["content"] as String
        assertThat(content).contains("Line 3")
        assertThat(content).contains("Line 4")
        assertThat(content).doesNotContain("Line 1")
        assertThat(content).doesNotContain("Line 2")
    }

    @Test
    fun `write_file requires path`() = runTest {
        val result = terminalModule.execute(
            "write_file",
            mapOf("content" to "test"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `write_file requires content`() = runTest {
        val result = terminalModule.execute(
            "write_file",
            mapOf("path" to "${tempDir.absolutePath}/test.txt"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.INVALID_PARAMS)
    }

    @Test
    fun `write_file blocks writing outside app directories`() = runTest {
        val result = terminalModule.execute(
            "write_file",
            mapOf("path" to "/etc/passwd", "content" to "hacked"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.PERMISSION_DENIED)
    }

    @Test
    fun `write_file writes to app directory`() = runTest {
        val targetPath = "${tempDir.absolutePath}/written.txt"
        
        val result = terminalModule.execute(
            "write_file",
            mapOf("path" to targetPath, "content" to "Written content"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        
        val writtenFile = File(targetPath)
        assertThat(writtenFile.exists()).isTrue()
        assertThat(writtenFile.readText()).isEqualTo("Written content")
    }

    @Test
    fun `write_file appends when append is true`() = runTest {
        val targetPath = "${tempDir.absolutePath}/appendable.txt"
        File(targetPath).writeText("Original")
        
        val result = terminalModule.execute(
            "write_file",
            mapOf("path" to targetPath, "content" to " Appended", "append" to true),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        assertThat(File(targetPath).readText()).isEqualTo("Original Appended")
    }

    @Test
    fun `write_file overwrites when append is false`() = runTest {
        val targetPath = "${tempDir.absolutePath}/overwritable.txt"
        File(targetPath).writeText("Original")
        
        val result = terminalModule.execute(
            "write_file",
            mapOf("path" to targetPath, "content" to "New content", "append" to false),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        assertThat(File(targetPath).readText()).isEqualTo("New content")
    }

    @Test
    fun `write_file creates parent directories`() = runTest {
        val targetPath = "${tempDir.absolutePath}/deep/nested/dir/file.txt"
        
        val result = terminalModule.execute(
            "write_file",
            mapOf("path" to targetPath, "content" to "Deep content"),
            mockContext
        )
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        assertThat(File(targetPath).exists()).isTrue()
    }

    @Test
    fun `get_env returns environment info`() = runTest {
        val result = terminalModule.execute("get_env", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Success::class.java)
        val success = result as ToolResponse.Success
        assertThat(success.data).containsKey("app_files_dir")
        assertThat(success.data).containsKey("package_name")
        assertThat(success.data).containsKey("android_version")
    }

    @Test
    fun `execute returns error for unknown operation`() = runTest {
        val result = terminalModule.execute("unknown_op", emptyMap(), mockContext)
        
        assertThat(result).isInstanceOf(ToolResponse.Error::class.java)
        assertThat((result as ToolResponse.Error).errorCode).isEqualTo(ErrorCode.NOT_FOUND)
    }

    @Test
    fun `execute operation has correct parameters`() {
        val execOp = terminalModule.operations.find { it.operationId == "execute" }!!
        
        val commandParam = execOp.parameters.find { it.name == "command" }
        assertThat(commandParam?.type).isEqualTo(ParameterType.STRING)
        assertThat(commandParam?.required).isTrue()
        
        val timeoutParam = execOp.parameters.find { it.name == "timeout_ms" }
        assertThat(timeoutParam?.required).isFalse()
    }
}
