package com.mazzlabs.sentinel.core

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.io.path.createTempDirectory

/**
 * Tests for SystemPromptBuilder
 * Note: SystemPromptBuilder is an object with static methods that use Tools.getInstance()
 * which has its own dependencies. These tests focus on output format validation.
 */
class SystemPromptBuilderTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: java.io.File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("sentinel_test").toFile()
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
    fun `build produces non-empty prompt`() {
        val prompt = SystemPromptBuilder.build(mockContext, includeTools = true)
        
        assertThat(prompt).isNotEmpty()
        assertThat(prompt.length).isGreaterThan(100)
    }

    @Test
    fun `build includes agent identity`() {
        val prompt = SystemPromptBuilder.build(mockContext)
        
        assertThat(prompt).containsMatch("(?i)sentinel|assistant")
    }

    @Test
    fun `build includes current date`() {
        val prompt = SystemPromptBuilder.build(mockContext)
        
        // Should include date in some format
        assertThat(prompt).containsMatch("(?i)date")
    }

    @Test
    fun `build includes current time`() {
        val prompt = SystemPromptBuilder.build(mockContext)
        
        assertThat(prompt).containsMatch("(?i)time")
    }

    @Test
    fun `build includes timezone`() {
        val prompt = SystemPromptBuilder.build(mockContext)
        
        assertThat(prompt).containsMatch("(?i)timezone")
    }

    @Test
    fun `build includes JSON format instructions`() {
        val prompt = SystemPromptBuilder.build(mockContext, includeTools = true)
        
        assertThat(prompt).containsMatch("(?i)json")
        assertThat(prompt).containsMatch("(?i)tool|action")
    }

    @Test
    fun `build includes tool schema when includeTools is true`() {
        val prompt = SystemPromptBuilder.build(mockContext, includeTools = true)
        
        // Should include something about tools
        assertThat(prompt).containsMatch("(?i)tool|operation|module")
    }

    @Test
    fun `build without tools is shorter`() {
        val withTools = SystemPromptBuilder.build(mockContext, includeTools = true)
        val withoutTools = SystemPromptBuilder.build(mockContext, includeTools = false)
        
        assertThat(withoutTools.length).isLessThan(withTools.length)
    }

    @Test
    fun `build includes action instructions`() {
        val prompt = SystemPromptBuilder.build(mockContext, includeTools = true)
        
        assertThat(prompt).containsMatch("(?i)tap|scroll|type")
    }

    @Test
    fun `buildCompact produces shorter output`() {
        val full = SystemPromptBuilder.build(mockContext, includeTools = true)
        val compact = SystemPromptBuilder.buildCompact(mockContext)
        
        assertThat(compact.length).isLessThan(full.length)
    }

    @Test
    fun `buildCompact includes essential info`() {
        val compact = SystemPromptBuilder.buildCompact(mockContext)
        
        assertThat(compact).containsMatch("(?i)sentinel")
        assertThat(compact).containsMatch("(?i)json")
    }

    @Test
    fun `buildToolPrompt includes user query`() {
        val query = "Schedule a meeting for tomorrow at 3pm"
        val prompt = SystemPromptBuilder.buildToolPrompt(mockContext, query)
        
        assertThat(prompt).contains(query)
    }

    @Test
    fun `buildToolPrompt includes time context`() {
        val prompt = SystemPromptBuilder.buildToolPrompt(mockContext, "test query")
        
        assertThat(prompt).containsMatch("(?i)time|date")
    }

    @Test
    fun `buildToolPrompt requests JSON output`() {
        val prompt = SystemPromptBuilder.buildToolPrompt(mockContext, "test query")
        
        assertThat(prompt).containsMatch("(?i)json")
        assertThat(prompt).containsMatch("(?i)tool")
    }

    @Test
    fun `buildToolPrompt mentions none option`() {
        val prompt = SystemPromptBuilder.buildToolPrompt(mockContext, "test query")
        
        // Should tell model it can output "none" if no tool applies
        assertThat(prompt).containsMatch("(?i)none")
    }
}
