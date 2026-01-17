package com.mazzlabs.sentinel.graph

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentStateTest {

    @Test
    fun `copy updates fields and appends history`() {
        val state = AgentState(currentNode = "start", iteration = 0)

        val updated = state.copy("currentNode" to "next", "response" to "ok")

        assertThat(updated.currentNode).isEqualTo("next")
        assertThat(updated.response).isEqualTo("ok")
        assertThat(updated.history).contains("start")
        assertThat(updated.iteration).isEqualTo(1)
    }

    @Test
    fun `shouldContinue false when complete or error`() {
        val complete = AgentState(isComplete = true)
        val error = AgentState(error = "boom")

        assertThat(complete.shouldContinue()).isFalse()
        assertThat(error.shouldContinue()).isFalse()
    }

    @Test
    fun `shouldContinue false when max iterations reached`() {
        val state = AgentState(iteration = 5, maxIterations = 5)

        assertThat(state.shouldContinue()).isFalse()
    }
}