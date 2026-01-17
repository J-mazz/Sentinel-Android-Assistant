package com.mazzlabs.sentinel.graph.nodes

import android.content.Context
import android.content.pm.PackageManager
import android.test.mock.MockContext
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.graph.AgentIntent
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.ScrollDirection
import com.mazzlabs.sentinel.tools.ErrorCode
import com.mazzlabs.sentinel.tools.Tool
import com.mazzlabs.sentinel.tools.ToolRegistry
import com.mazzlabs.sentinel.tools.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GraphNodesCoverageTest {

    @Test
    fun `response generator formats success result`() = runTest {
        val state = AgentState(
            toolResults = listOf(ToolResult.Success("agent", "done", data = mapOf("value" to 3)))
        )

        val updated = ResponseGeneratorNode().process(state)

        assertThat(updated.currentNode).isEqualTo("response_generator")
        assertThat(updated.isComplete).isTrue()
        assertThat(updated.response).contains("done")
        assertThat(updated.response).contains("value: 3")
    }

    @Test
    fun `response generator handles failure result`() = runTest {
        val state = AgentState(
            toolResults = listOf(ToolResult.Failure("agent", "oops", ErrorCode.UNKNOWN))
        )

        val updated = ResponseGeneratorNode().process(state)

        assertThat(updated.response).contains("couldn't complete")
        assertThat(updated.isComplete).isTrue()
    }

    @Test
    fun `response generator falls back with no results`() = runTest {
        val updated = ResponseGeneratorNode().process(AgentState())

        assertThat(updated.response).contains("no specific")
        assertThat(updated.isComplete).isTrue()
    }

    @Test
    fun `ui action node handles navigation intents`() = runTest {
        val node = UIActionNode()

        val goBack = node.process(AgentState(intent = AgentIntent.GO_BACK))
        assertThat(goBack.action?.type).isEqualTo(ActionType.BACK)

        val goHome = node.process(AgentState(intent = AgentIntent.GO_HOME))
        assertThat(goHome.action?.type).isEqualTo(ActionType.HOME)
    }

    @Test
    fun `ui action node handles scroll and click parameters`() = runTest {
        val node = UIActionNode()

        val scrollState = AgentState(
            intent = AgentIntent.SCROLL_SCREEN,
            extractedEntities = mapOf("direction" to "up")
        )

        val scroll = node.process(scrollState)
        assertThat(scroll.action?.direction).isEqualTo(ScrollDirection.UP)

        val clickState = AgentState(
            intent = AgentIntent.CLICK_ELEMENT,
            extractedEntities = mapOf("target" to "button", "element_id" to "42")
        )

        val click = node.process(clickState)
        assertThat(click.action?.type).isEqualTo(ActionType.CLICK)
        assertThat(click.action?.elementId).isEqualTo(42)
        assertThat(click.action?.target).isEqualTo("button")
    }

    @Test
    fun `ui action node handles typing inputs`() = runTest {
        val node = UIActionNode()

        val state = AgentState(
            intent = AgentIntent.TYPE_TEXT,
            extractedEntities = mapOf("text" to "hello", "field" to "search")
        )

        val result = node.process(state)
        assertThat(result.action?.type).isEqualTo(ActionType.TYPE)
        assertThat(result.action?.text).isEqualTo("hello")
        assertThat(result.action?.target).isEqualTo("search")
    }

    @Test
    fun `tool selector chooses registered tool`() = runTest {
        val registry = ToolRegistry()
        registry.register(SimpleTool("calendar_read"))
        val node = ToolSelectorNode(registry)

        val state = AgentState(intent = AgentIntent.READ_CALENDAR)
        val updated = node.process(state)

        assertThat(updated.selectedTool).isEqualTo("calendar_read")
        assertThat(updated.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `tool selector falls back when no tool exists`() = runTest {
        val registry = ToolRegistry()
        val node = ToolSelectorNode(registry)

        val state = AgentState(intent = AgentIntent.SEND_SMS)
        val updated = node.process(state)

        assertThat(updated.selectedTool).isNull()
        assertThat(updated.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `tool executor captures success result`() = runTest {
        val registry = ToolRegistry()
        registry.register(SimpleTool("ping"))
        val context = object : MockContext() {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        }
        val node = ToolExecutorNode(registry, context)
        val state = AgentState(selectedTool = "ping")

        val updated = node.process(state)

        assertThat(updated.currentNode).isEqualTo("tool_executor")
        assertThat(updated.toolResults).hasSize(1)
        assertThat(updated.toolResults.first()).isInstanceOf(ToolResult.Success::class.java)
    }

    @Test
    fun `tool executor returns error when tool missing`() = runTest {
        val registry = ToolRegistry()
        val context = object : MockContext() {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_GRANTED
        }
        val node = ToolExecutorNode(registry, context)
        val updated = node.process(AgentState(selectedTool = "missing"))

        assertThat(updated.error).contains("Tool not found")
    }

    private class SimpleTool(override val name: String) : Tool {
        override val description: String = "simple"
        override val parametersSchema: String = "{}"
        override val requiredPermissions: List<String> = emptyList()

        override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
            return ToolResult.Success(name, "ok", data = mapOf("status" to "ok"))
        }
    }
}