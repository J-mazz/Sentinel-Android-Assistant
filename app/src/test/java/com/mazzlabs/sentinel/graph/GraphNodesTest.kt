package com.mazzlabs.sentinel.graph

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.graph.nodes.ParameterExtractorNode
import com.mazzlabs.sentinel.graph.nodes.ResponseGeneratorNode
import com.mazzlabs.sentinel.graph.nodes.ToolExecutorNode
import com.mazzlabs.sentinel.graph.nodes.ToolSelectorNode
import com.mazzlabs.sentinel.graph.nodes.UIActionNode
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.ScrollDirection
import com.mazzlabs.sentinel.tools.Tool
import com.mazzlabs.sentinel.tools.ToolRegistry
import com.mazzlabs.sentinel.tools.ToolResult
import com.mazzlabs.sentinel.tools.ValidationResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GraphNodesTest {

    @Test
    fun `ToolSelectorNode selects registered tool for intent`() = runTest {
        val registry = ToolRegistry()
        registry.register(TestTool(name = "calendar_read"))

        val state = AgentState(intent = AgentIntent.READ_CALENDAR)
        val node = ToolSelectorNode(registry)

        val result = node.process(state)

        assertThat(result.selectedTool).isEqualTo("calendar_read")
        assertThat(result.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `ParameterExtractorNode uses pre-extracted entities`() = runTest {
        val registry = ToolRegistry()
        registry.register(TestTool(name = "calendar_read"))

        val state = AgentState(
            selectedTool = "calendar_read",
            extractedEntities = mapOf("title" to "Team Sync")
        )
        val node = ParameterExtractorNode(registry)

        val result = node.process(state)

        assertThat(result.toolInput).containsEntry("title", "Team Sync")
        assertThat(result.currentNode).isEqualTo("param_extractor")
    }

    @Test
    fun `ToolExecutorNode executes tool when valid and permitted`() = runTest {
        val registry = ToolRegistry()
        val tool = TestTool(name = "calendar_read")
        registry.register(tool)

        val context = mockk<Context>()
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        coEvery { tool.execute(any(), any()) } returns ToolResult.Success("calendar_read", "ok")

        val node = ToolExecutorNode(registry, context)
        val state = AgentState(selectedTool = "calendar_read", toolInput = mapOf("id" to "1"))

        val result = node.process(state)

        assertThat(result.toolResults).hasSize(1)
        assertThat(result.currentNode).isEqualTo("tool_executor")
    }

    @Test
    fun `ToolExecutorNode rejects invalid parameters`() = runTest {
        val registry = ToolRegistry()
        val tool = TestTool(
            name = "calendar_read",
            validationResult = ValidationResult.Invalid("missing id")
        )
        registry.register(tool)

        val context = mockk<Context>()
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED

        val node = ToolExecutorNode(registry, context)
        val state = AgentState(selectedTool = "calendar_read")

        val result = node.process(state)

        assertThat(result.error).contains("Invalid parameters")
    }

    @Test
    fun `ResponseGeneratorNode formats success results`() = runTest {
        val node = ResponseGeneratorNode()
        val state = AgentState(
            toolResults = listOf(
                ToolResult.Success(
                    toolName = "calendar_read",
                    message = "Found events",
                    data = mapOf("events" to listOf("A", "B"))
                )
            )
        )

        val result = node.process(state)

        assertThat(result.response).contains("Found events")
        assertThat(result.response).contains("events:")
        assertThat(result.response).contains("- A")
    }

    @Test
    fun `UIActionNode builds scroll action from entities`() = runTest {
        val node = UIActionNode()
        val state = AgentState(
            intent = AgentIntent.SCROLL_SCREEN,
            extractedEntities = mapOf("direction" to "left")
        )

        val result = node.process(state)

        assertThat(result.action?.action).isEqualTo(ActionType.SCROLL)
        assertThat(result.action?.direction).isEqualTo(ScrollDirection.LEFT)
    }

    @Test
    fun `UIActionNode builds click action with element id`() = runTest {
        val node = UIActionNode()
        val state = AgentState(
            intent = AgentIntent.CLICK_ELEMENT,
            extractedEntities = mapOf("element_id" to "42", "target" to "submit")
        )

        val result = node.process(state)

        assertThat(result.action?.action).isEqualTo(ActionType.CLICK)
        assertThat(result.action?.elementId).isEqualTo(42)
        assertThat(result.action?.target).isEqualTo("submit")
    }

    private class TestTool(
        override val name: String,
        private val validationResult: ValidationResult = ValidationResult.Valid
    ) : Tool {
        override val description: String = "test tool"
        override val parametersSchema: String = "{}"
        override val requiredPermissions: List<String> = emptyList()

        override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
            return ToolResult.Success(name, "ok")
        }

        override fun validateParams(params: Map<String, Any?>): ValidationResult {
            return validationResult
        }
    }
}