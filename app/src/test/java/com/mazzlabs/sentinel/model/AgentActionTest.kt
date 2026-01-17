package com.mazzlabs.sentinel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentActionTest {

    @Test
    fun `fromJsonOrNull repairs unquoted values`() {
        val json = "{\"action\":CLICK,\"target\":\"ok\"}"
        val action = AgentAction.fromJsonOrNull(json)
        assertNotNull(action)
        assertEquals(ActionType.CLICK, action?.action)
        assertEquals("ok", action?.target)
    }

    @Test
    fun `requiresConfirmation flags dangerous click`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Delete")
        assertTrue(action.requiresConfirmation())
    }
}
