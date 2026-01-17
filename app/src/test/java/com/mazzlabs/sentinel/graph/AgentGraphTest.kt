package com.mazzlabs.sentinel.graph

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentGraphTest {

    @Test
    fun `invoke routes through nodes and ends`() = runTest {
        val graph = AgentGraph.Builder()
            .addNode("first", PassthroughNode("first"))
            .addNode("second", PassthroughNode("second"))
            .setEntryPoint("first")
            .addEdge("first", "second")
            .addEdge("second", AgentGraph.END)
            .build()

        val initial = AgentState(maxIterations = 10)
        val result = graph.invoke(initial)

        assertThat(result.isComplete).isTrue()
        assertThat(result.history).containsAtLeast("first", "second")
    }

    @Test
    fun `invoke sets error when node missing`() = runTest {
        val graph = AgentGraph.Builder()
            .addNode("first", PassthroughNode("first"))
            .setEntryPoint("first")
            .addEdge("first", "missing")
            .build()

        val result = graph.invoke(AgentState(maxIterations = 10))

        assertThat(result.error).contains("Node not found")
        assertThat(result.isComplete).isTrue()
    }

    @Test
    fun `invoke sets error when edge missing`() = runTest {
        val graph = AgentGraph.Builder()
            .addNode("solo", PassthroughNode("solo"))
            .setEntryPoint("solo")
            .build()

        val result = graph.invoke(AgentState(maxIterations = 10))

        assertThat(result.error).contains("No edge")
        assertThat(result.isComplete).isTrue()
    }

    @Test
    fun `invoke stops at max iterations`() = runTest {
        val graph = AgentGraph.Builder()
            .addNode("loop", PassthroughNode("loop"))
            .setEntryPoint("loop")
            .addEdge("loop", "loop")
            .build()

        val result = graph.invoke(AgentState(maxIterations = 2))

        assertThat(result.error).contains("Max iterations")
        assertThat(result.isComplete).isTrue()
    }

    private class PassthroughNode(private val name: String) : AgentNode {
        override suspend fun process(state: AgentState): AgentState {
            return state.copy(currentNode = name)
        }
    }
}