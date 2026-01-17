package com.mazzlabs.sentinel.security

import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.model.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionFirewallTest {

    private val firewall = ActionFirewall()

    @Test
    fun `click dangerous target is flagged`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Delete account")
        assertTrue(firewall.isDangerous(action))
        assertTrue(firewall.getDangerReason(action)?.contains("delete") == true)
    }

    @Test
    fun `click safe target is not flagged`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Cancel")
        assertTrue(!firewall.isDangerous(action))
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `type sensitive text is flagged`() {
        val action = AgentAction(action = ActionType.TYPE, text = "4111111111111111")
        assertTrue(firewall.isDangerous(action))
        assertNotNull(firewall.getDangerReason(action))
    }

    @Test
    fun `type missing text is invalid`() {
        val action = AgentAction(action = ActionType.TYPE)

        val result = firewall.validateAction(action)

        assertEquals(ActionFirewall.ValidationResult.Invalid("TYPE requires text"), result)
    }

    @Test
    fun `scroll actions are always safe`() {
        val action = AgentAction(action = ActionType.SCROLL, direction = ScrollDirection.UP)

        assertTrue(!firewall.isDangerous(action))
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `validateAction enforces required fields`() {
        val missingTarget = AgentAction(action = ActionType.CLICK)
        val result = firewall.validateAction(missingTarget)
        assertEquals(ActionFirewall.ValidationResult.Invalid("CLICK requires a target"), result)

        val missingScroll = AgentAction(action = ActionType.SCROLL)
        val scrollResult = firewall.validateAction(missingScroll)
        assertEquals(ActionFirewall.ValidationResult.Invalid("SCROLL requires direction"), scrollResult)

        val okScroll = AgentAction(action = ActionType.SCROLL, direction = ScrollDirection.DOWN)
        assertEquals(ActionFirewall.ValidationResult.Valid, firewall.validateAction(okScroll))
    }
}
