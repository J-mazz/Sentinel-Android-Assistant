package com.mazzlabs.sentinel.graph

import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.graph.nodes.ClarificationNode
import com.mazzlabs.sentinel.graph.nodes.ContextAnalyzerNode
import com.mazzlabs.sentinel.graph.nodes.EnhancedResponseGeneratorNode
import com.mazzlabs.sentinel.graph.nodes.PlanExecutorNode
import com.mazzlabs.sentinel.tools.framework.ErrorCode
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EnhancedGraphNodesTest {

    @Test
    fun `ContextAnalyzerNode selects editable focused element`() = runTest {
        val node = ContextAnalyzerNode()
        val state = AgentState(
            intent = AgentIntent.CLICK_ELEMENT,
            screenContext = "  1. [edit] Name\n  2. [click] Save"
        )

        val result = node.process(state)

        assertThat(result.focusedElement?.text).isEqualTo("Name")
        assertThat(result.focusedElement?.isEditable).isTrue()
    }

    @Test
    fun `PlanExecutorNode advances plan and sets intent`() = runTest {
        val node = PlanExecutorNode()
        val plan = Plan(
            goal = "Read calendar",
            steps = listOf(
                PlanStep(
                    description = "Open calendar",
                    intent = AgentIntent.READ_CALENDAR,
                    requiredEntities = emptyList(),
                    toolCall = "calendar.read_events"
                )
            )
        )
        val state = AgentState(plan = plan)

        val result = node.process(state)

        assertThat(result.intent).isEqualTo(AgentIntent.READ_CALENDAR)
        assertThat(result.selectedTool).isEqualTo("calendar.read_events")
        assertThat(result.plan?.currentStepIndex).isEqualTo(1)
    }

    @Test
    fun `ClarificationNode asks for rephrase when intent unknown`() = runTest {
        val node = ClarificationNode()
        val state = AgentState(intent = AgentIntent.UNKNOWN)

        val result = node.process(state)

        assertThat(result.needsUserInput).isTrue()
        assertThat(result.isComplete).isTrue()
        assertThat(result.response).contains("rephrase")
    }

    @Test
    fun `EnhancedResponseGeneratorNode formats tool error`() = runTest {
        val node = EnhancedResponseGeneratorNode()
        val state = AgentState(
            toolResults = listOf(
                ToolResponse.Error("calendar", "read_events", ErrorCode.SYSTEM_ERROR, "boom")
            )
        )

        val result = node.process(state)

        assertThat(result.response).contains("Error")
        assertThat(result.response).contains("boom")
        assertThat(result.currentNode).isEqualTo("response_generator")
    }
}
