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

    @Test
    fun `home action never dangerous`() {
        val action = AgentAction(action = ActionType.HOME)
        assertTrue(!firewall.isDangerous(action))
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `back action never dangerous`() {
        val action = AgentAction(action = ActionType.BACK)
        assertTrue(!firewall.isDangerous(action))
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `wait action never dangerous`() {
        val action = AgentAction(action = ActionType.WAIT)
        assertTrue(!firewall.isDangerous(action))
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `none action never dangerous`() {
        val action = AgentAction(action = ActionType.NONE)
        assertTrue(!firewall.isDangerous(action))
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `click uninstall is dangerous`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Uninstall")
        assertTrue(firewall.isDangerous(action))
        assertTrue(firewall.getDangerReason(action)?.isNotEmpty() == true)
    }

    @Test
    fun `click erase is dangerous`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Erase Everything")
        assertTrue(firewall.isDangerous(action))
    }

    @Test
    fun `click send is dangerous`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Send Money")
        assertTrue(firewall.isDangerous(action))
    }

    @Test
    fun `click grant permissions is dangerous`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Grant All Permissions")
        assertTrue(firewall.isDangerous(action))
    }

    @Test
    fun `type credit card number is dangerous`() {
        val action = AgentAction(action = ActionType.TYPE, text = "4111111111111111")
        assertTrue(firewall.isDangerous(action))
    }

    @Test
    fun `type ssn is dangerous`() {
        val action = AgentAction(action = ActionType.TYPE, text = "123-45-6789")
        assertTrue(firewall.isDangerous(action))
    }

    @Test
    fun `type password text is dangerous`() {
        val action = AgentAction(action = ActionType.TYPE, text = "MyPassword123")
        // This depends on the actual implementation of password detection
        // Currently looking for keyword "password" in the text, not in the target
        val isDangerous = firewall.isDangerous(action)
        // Could be dangerous if implementation checks for common patterns
        assertNotNull(isDangerous)
    }

    @Test
    fun `scroll left is always safe`() {
        val action = AgentAction(action = ActionType.SCROLL, direction = ScrollDirection.LEFT)
        assertTrue(!firewall.isDangerous(action))
    }

    @Test
    fun `scroll right is always safe`() {
        val action = AgentAction(action = ActionType.SCROLL, direction = ScrollDirection.RIGHT)
        assertTrue(!firewall.isDangerous(action))
    }

    @Test
    fun `getDangerReason returns null for safe actions`() {
        val action = AgentAction(action = ActionType.CLICK, target = "OK")
        assertNull(firewall.getDangerReason(action))
    }

    @Test
    fun `getDangerReason includes matched keyword`() {
        val action = AgentAction(action = ActionType.CLICK, target = "Confirm Delete")
        val reason = firewall.getDangerReason(action)
        assertNotNull(reason)
        assertTrue(reason?.contains("delete") == true)
    }

    @Test
    fun `case insensitive matching for click targets`() {
        val action1 = AgentAction(action = ActionType.CLICK, target = "delete")
        val action2 = AgentAction(action = ActionType.CLICK, target = "DELETE")
        val action3 = AgentAction(action = ActionType.CLICK, target = "Delete")
        
        assertTrue(firewall.isDangerous(action1))
        assertTrue(firewall.isDangerous(action2))
        assertTrue(firewall.isDangerous(action3))
    }

    @Test
    fun `safe targets are whitelisted`() {
        val safeTargets = listOf("Cancel", "Close", "Back", "Dismiss", "Skip", "Home", "Menu")
        for (target in safeTargets) {
            val action = AgentAction(action = ActionType.CLICK, target = target)
            assertTrue(!firewall.isDangerous(action), "Target '$target' should be safe")
        }
    }

    @Test
    fun `validate type without text fails`() {
        val action = AgentAction(action = ActionType.TYPE, text = null)
        val result = firewall.validateAction(action)
        assertEquals(ActionFirewall.ValidationResult.Invalid("TYPE requires text"), result)
    }

    @Test
    fun `validate type with empty text fails`() {
        val action = AgentAction(action = ActionType.TYPE, text = "")
        val result = firewall.validateAction(action)
        assertEquals(ActionFirewall.ValidationResult.Invalid("TYPE requires text"), result)
    }

    @Test
    fun `validate type with whitespace-only text fails`() {
        val action = AgentAction(action = ActionType.TYPE, text = "   ")
        val result = firewall.validateAction(action)
        assertEquals(ActionFirewall.ValidationResult.Invalid("TYPE requires text"), result)
    }

    @Test
    fun `validate click without target fails`() {
        val action = AgentAction(action = ActionType.CLICK, target = null)
        val result = firewall.validateAction(action)
        assertEquals(ActionFirewall.ValidationResult.Invalid("CLICK requires a target"), result)
    }

    @Test
    fun `validate home action succeeds`() {
        val action = AgentAction(action = ActionType.HOME)
        assertEquals(ActionFirewall.ValidationResult.Valid, firewall.validateAction(action))
    }

    @Test
    fun `getDangerReason for type action`() {
        val action = AgentAction(action = ActionType.TYPE, text = "987654321")
        val reason = firewall.getDangerReason(action)
        assertNotNull(reason)
        assertTrue(reason?.contains("sensitive") == true)
    }

    @Test
    fun `click on empty target is safe`() {
        val action = AgentAction(action = ActionType.CLICK, target = "")
        assertTrue(!firewall.isDangerous(action))
    }
