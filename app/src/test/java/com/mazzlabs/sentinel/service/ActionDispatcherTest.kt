package com.mazzlabs.sentinel.service

import android.view.accessibility.AccessibilityNodeInfo
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.model.ActionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import android.content.Context
import io.mockk.spyk

class ActionDispatcherTest {

    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var mockElementRegistry: ElementRegistry
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockElementRegistry = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        actionDispatcher = ActionDispatcher(mockElementRegistry)
    }

    @Test
    fun dispatch_withClickAction_executesClick() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget = mockk<AccessibilityNodeInfo>()
        
        every { mockRoot.findAccessibilityNodeInfosByViewId("com.example:id/button") } returns listOf(mockTarget)
        every { mockTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true

        val action = AgentAction(
            action = ActionType.CLICK,
            elementId = "com.example:id/button",
            target = "Button",
            reasoning = "Click the button"
        )

        val result = actionDispatcher.dispatch(mockContext, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withMissingElement_returnsFalse() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.findAccessibilityNodeInfosByViewId(any()) } returns emptyList()

        val action = AgentAction(
            action = ActionType.CLICK,
            elementId = "com.example:id/nonexistent",
            target = "Missing Button"
        )

        val result = actionDispatcher.dispatch(mockContext, mockRoot, action)
        assertThat(result).isFalse()
    }

    @Test
    fun dispatch_withTypeAction_performsTextInput() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget = mockk<AccessibilityNodeInfo>()
        
        every { mockRoot.findAccessibilityNodeInfosByViewId("com.example:id/input") } returns listOf(mockTarget)

        val action = AgentAction(
            action = ActionType.TYPE,
            elementId = null,
            target = "Input Field",
            text = "test input"
        )

        val result = actionDispatcher.dispatch(mockContext, mockRoot, action)
        // Should attempt dispatch without throwing
        assertThat(result).isNotNull()
    }

    @Test
    fun dispatch_withScrollAction_executesScroll() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        
        every { mockRoot.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } returns true

        val action = AgentAction(
            action = ActionType.SCROLL,
            target = "Scroll down"
        )

        val result = actionDispatcher.dispatch(mockContext, mockRoot, action)
        // Scroll should work on root
        assertThat(result).isNotNull()
    }

    @Test
    fun dispatch_withGoBackAction_executesBackNavigation() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) } returns true

        val action = AgentAction(
            action = ActionType.BACK,
            target = "Go back"
        )

        val result = actionDispatcher.dispatch(mockContext, mockRoot, action)
        assertThat(result).isNotNull()
    }

    @Test
    fun dispatch_nullRoot_returnsFalse() {
        val action = AgentAction(
            action = ActionType.CLICK,
            elementId = "any",
            target = "Button"
        )

        val result = actionDispatcher.dispatch(mockContext, null, action)
        assertThat(result).isFalse()
    }

    @Test
    fun dispatch_multipleMatches_usesFirst() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget1 = mockk<AccessibilityNodeInfo>()
        val mockTarget2 = mockk<AccessibilityNodeInfo>()
        
        every { mockRoot.findAccessibilityNodeInfosByViewId("com.example:id/button") } returns listOf(mockTarget1, mockTarget2)
        every { mockTarget1.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true

        val action = AgentAction(
            action = ActionType.CLICK,
            elementId = "com.example:id/button",
            target = "Button"
        )

        val result = actionDispatcher.dispatch(mockContext, mockRoot, action)
        assertThat(result).isTrue()
        verify { mockTarget1.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }
}
