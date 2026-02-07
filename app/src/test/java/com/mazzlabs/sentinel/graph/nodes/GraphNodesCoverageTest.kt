package com.mazzlabs.sentinel.graph.nodes

import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.graph.AgentIntent
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.ScrollDirection
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ToolExecutor
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GraphNodesCoverageTest {

    @Test
    fun `response generator formats success result`() = runTest {
        val state = AgentState(
            toolResults = listOf(
                ToolResponse.Success("agent", "op", "done", data = mapOf("value" to 3))
            )
        )

        val updated = ResponseGeneratorNode().process(state)

        assertThat(updated.currentNode).isEqualTo("response_generator")
        assertThat(updated.isComplete).isTrue()
        assertThat(updated.response).contains("done")
        assertThat(updated.response).contains("value: 3")
    }

    @Test
    fun `response generator handles error result`() = runTest {
        val state = AgentState(
            toolResults = listOf(
                ToolResponse.Error("agent", "op", ErrorCode.SYSTEM_ERROR, "oops")
            )
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
        assertThat(goBack.action?.action).isEqualTo(ActionType.BACK)

        val goHome = node.process(AgentState(intent = AgentIntent.GO_HOME))
        assertThat(goHome.action?.action).isEqualTo(ActionType.HOME)
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
        assertThat(click.action?.action).isEqualTo(ActionType.CLICK)
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
        assertThat(result.action?.action).isEqualTo(ActionType.TYPE)
        assertThat(result.action?.text).isEqualTo("hello")
        assertThat(result.action?.target).isEqualTo("search")
    }

    @Test
    fun `tool selector chooses correct tool for intent`() = runTest {
        val node = ToolSelectorNode()

        val state = AgentState(intent = AgentIntent.READ_CALENDAR)
        val updated = node.process(state)

        assertThat(updated.selectedTool).isEqualTo("calendar.read_events")
        assertThat(updated.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `tool selector falls back when no tool for intent`() = runTest {
        val node = ToolSelectorNode()

        val state = AgentState(intent = AgentIntent.GO_HOME)
        val updated = node.process(state)

        assertThat(updated.selectedTool).isNull()
        assertThat(updated.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `tool executor captures success result`() = runTest {
        val toolExecutor = mockk<ToolExecutor>()
        coEvery { toolExecutor.execute("calendar.read_events", any()) } returns
            ToolResponse.Success("calendar", "read_events", "ok")

        val node = ToolExecutorNode(toolExecutor)
        val state = AgentState(selectedTool = "calendar.read_events")

        val updated = node.process(state)

        assertThat(updated.currentNode).isEqualTo("tool_executor")
        assertThat(updated.toolResults).hasSize(1)
        assertThat(updated.toolResults.first()).isInstanceOf(ToolResponse.Success::class.java)
    }

    @Test
    fun `tool executor returns error when tool missing`() = runTest {
        val toolExecutor = mockk<ToolExecutor>()
        val node = ToolExecutorNode(toolExecutor)
        val updated = node.process(AgentState(selectedTool = null))

        assertThat(updated.error).contains("No tool selected")
    }
}
