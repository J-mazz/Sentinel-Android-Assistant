package com.mazzlabs.sentinel.graph

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class SessionManagerTest {

    private lateinit var tempDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        tempDir = createTempDir("sentinel_sessions")
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `getOrCreateSession creates new session`() {
        val manager = SessionManager(context)

        val state = manager.getOrCreateSession("alpha")

        assertThat(state.sessionId).isEqualTo("alpha")
    }

    @Test
    fun `updateSession persists and reloads history`() {
        val manager = SessionManager(context)

        val history = listOf(
            Message(role = Role.USER, content = "Hi", timestamp = 1L),
            Message(role = Role.ASSISTANT, content = "Hello", timestamp = 2L)
        )
        val state = AgentState(sessionId = "beta", conversationHistory = history)
        manager.updateSession(state)

        val reloaded = SessionManager(context).getOrCreateSession("beta")

        assertThat(reloaded.conversationHistory).hasSize(2)
        assertThat(reloaded.conversationHistory.last().content).isEqualTo("Hello")
    }

    @Test
    fun `updateSession trims history beyond limit`() {
        val manager = SessionManager(context)

        val history = (1..60).map {
            Message(role = Role.USER, content = "msg-$it", timestamp = it.toLong())
        }
        val state = AgentState(sessionId = "gamma", conversationHistory = history)
        manager.updateSession(state)

        val reloaded = SessionManager(context).getOrCreateSession("gamma")

        assertThat(reloaded.conversationHistory).hasSize(50)
        assertThat(reloaded.conversationHistory.first().content).isEqualTo("msg-11")
    }
}