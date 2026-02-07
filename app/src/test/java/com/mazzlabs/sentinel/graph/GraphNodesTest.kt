package com.mazzlabs.sentinel.graph

import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.graph.nodes.ParameterExtractorNode
import com.mazzlabs.sentinel.graph.nodes.ResponseGeneratorNode
import com.mazzlabs.sentinel.graph.nodes.ToolExecutorNode
import com.mazzlabs.sentinel.graph.nodes.ToolSelectorNode
import com.mazzlabs.sentinel.graph.nodes.UIActionNode
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.ScrollDirection
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ToolExecutor
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GraphNodesTest {

    @Test
    fun `ToolSelectorNode selects tool for calendar intent`() = runTest {
        val state = AgentState(intent = AgentIntent.READ_CALENDAR)
        val node = ToolSelectorNode()

        val result = node.process(state)

        assertThat(result.selectedTool).isEqualTo("calendar.read_events")
        assertThat(result.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `ToolSelectorNode maps all tool intents`() = runTest {
        val node = ToolSelectorNode()

        val mappings = mapOf(
            AgentIntent.READ_CALENDAR to "calendar.read_events",
            AgentIntent.CREATE_EVENT to "calendar.create_event",
            AgentIntent.UPDATE_EVENT to "calendar.update_event",
            AgentIntent.DELETE_EVENT to "calendar.delete_event",
            AgentIntent.CREATE_ALARM to "clock.create_alarm",
            AgentIntent.LIST_ALARMS to "clock.show_alarms",
            AgentIntent.DELETE_ALARM to "clock.dismiss_alarm",
            AgentIntent.CALL_CONTACT to "contacts.call_contact",
            AgentIntent.SEND_SMS to "messaging.send_sms"
        )

        for ((intent, expectedTool) in mappings) {
            val result = node.process(AgentState(intent = intent))
            assertThat(result.selectedTool).isEqualTo(expectedTool)
        }
    }

    @Test
    fun `ToolSelectorNode returns null for UI intents`() = runTest {
        val node = ToolSelectorNode()
        val result = node.process(AgentState(intent = AgentIntent.GO_BACK))

        assertThat(result.selectedTool).isNull()
        assertThat(result.currentNode).isEqualTo("tool_selector")
    }

    @Test
    fun `ParameterExtractorNode uses pre-extracted entities`() = runTest {
        val toolExecutor = mockk<ToolExecutor>()

        val state = AgentState(
            selectedTool = "calendar.read_events",
            extractedEntities = mapOf("title" to "Team Sync")
        )
        val node = ParameterExtractorNode(toolExecutor)

        val result = node.process(state)

        assertThat(result.toolInput).containsEntry("title", "Team Sync")
        assertThat(result.currentNode).isEqualTo("param_extractor")
    }

    @Test
    fun `ToolExecutorNode executes tool and appends result`() = runTest {
        val toolExecutor = mockk<ToolExecutor>()
        coEvery { toolExecutor.execute("calendar.read_events", any()) } returns
            ToolResponse.Success("calendar", "read_events", "Found 2 events")

        val node = ToolExecutorNode(toolExecutor)
        val state = AgentState(
            selectedTool = "calendar.read_events",
            toolInput = mapOf("date" to "2024-01-01")
        )

        val result = node.process(state)

        assertThat(result.toolResults).hasSize(1)
        assertThat(result.toolResults.first()).isInstanceOf(ToolResponse.Success::class.java)
        assertThat(result.currentNode).isEqualTo("tool_executor")
    }

    @Test
    fun `ToolExecutorNode returns error when no tool selected`() = runTest {
        val toolExecutor = mockk<ToolExecutor>()
        val node = ToolExecutorNode(toolExecutor)
        val result = node.process(AgentState(selectedTool = null))

        assertThat(result.error).contains("No tool selected")
    }

    @Test
    fun `ResponseGeneratorNode formats success results`() = runTest {
        val node = ResponseGeneratorNode()
        val state = AgentState(
            toolResults = listOf(
                ToolResponse.Success(
                    moduleId = "calendar",
                    operationId = "read_events",
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
    fun `ResponseGeneratorNode formats error results`() = runTest {
        val node = ResponseGeneratorNode()
        val state = AgentState(
            toolResults = listOf(
                ToolResponse.Error("calendar", "read_events", ErrorCode.SYSTEM_ERROR, "DB error")
            )
        )

        val result = node.process(state)

        assertThat(result.response).contains("couldn't complete")
        assertThat(result.response).contains("DB error")
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
}
