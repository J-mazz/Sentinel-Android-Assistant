package com.mazzlabs.sentinel.tools

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun `register and get returns tool`() {
        val registry = ToolRegistry()
        val tool = TestTool(name = "calendar_read")

        registry.register(tool)

        assertThat(registry.get("calendar_read")).isEqualTo(tool)
        assertThat(registry.getAll()).contains(tool)
    }

    @Test
    fun `generateToolsPrompt includes tool details`() {
        val registry = ToolRegistry()
        registry.register(TestTool(name = "alarm_create", description = "Create alarm"))

        val prompt = registry.generateToolsPrompt()

        assertThat(prompt).contains("Available tools:")
        assertThat(prompt).contains("alarm_create")
        assertThat(prompt).contains("Create alarm")
    }

    @Test
    fun `generateToolsPrompt empty registry returns empty string`() {
        val registry = ToolRegistry()

        val prompt = registry.generateToolsPrompt()

        assertThat(prompt).isEmpty()
    }

    @Test
    fun `hasPermissions returns true when all granted`() {
        val registry = ToolRegistry()
        val tool = TestTool(
            name = "sms_send",
            requiredPermissions = listOf("perm.sms")
        )
        registry.register(tool)

        val context = mockk<Context>()
        every { context.checkSelfPermission("perm.sms") } returns PackageManager.PERMISSION_GRANTED

        assertThat(registry.hasPermissions(context, "sms_send")).isTrue()
    }

    @Test
    fun `hasPermissions returns false when permission missing`() {
        val registry = ToolRegistry()
        val tool = TestTool(
            name = "sms_send",
            requiredPermissions = listOf("perm.sms")
        )
        registry.register(tool)

        val context = mockk<Context>()
        every { context.checkSelfPermission("perm.sms") } returns PackageManager.PERMISSION_DENIED

        assertThat(registry.hasPermissions(context, "sms_send")).isFalse()
    }

    @Test
    fun `hasPermissions returns false when tool missing`() {
        val registry = ToolRegistry()
        val context = mockk<Context>()

        assertThat(registry.hasPermissions(context, "missing_tool")).isFalse()
    }

    private class TestTool(
        override val name: String,
        override val description: String = "test tool",
        override val requiredPermissions: List<String> = emptyList()
    ) : Tool {
        override val parametersSchema: String = "{}"

        override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
            return ToolResult.Success(name, "ok")
        }
    }
}