package com.mazzlabs.sentinel.graph

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.mazzlabs.sentinel.graph.nodes.SelectionProcessorNode
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelectionProcessorNodeTest {

    @Test
    fun `SelectionProcessorNode extracts phone email and url`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val node = SelectionProcessorNode(context)
        val state = AgentState(
            intent = AgentIntent.EXTRACT_DATA_FROM_SELECTION,
            extractedEntities = mapOf(
                "selected_text" to "Call 555-123-4567 or email test@example.com at https://example.com"
            )
        )

        val result = node.process(state)

        assertThat(result.response).contains("Extracted data")
        assertThat(result.extractedEntities).containsEntry("phone", "555-123-4567")
        assertThat(result.extractedEntities).containsEntry("email", "test@example.com")
        assertThat(result.extractedEntities).containsEntry("url", "https://example.com")
        assertThat(result.isComplete).isTrue()
    }

    @Test
    fun `SelectionProcessorNode returns message when no selection text`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val node = SelectionProcessorNode(context)
        val state = AgentState(intent = AgentIntent.COPY_SELECTED)

        val result = node.process(state)

        assertThat(result.response).contains("No text available")
        assertThat(result.isComplete).isTrue()
    }
}