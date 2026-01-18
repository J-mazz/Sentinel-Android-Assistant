package com.mazzlabs.sentinel.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.ScrollDirection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class ActionDispatcherTest {

    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var mockElementRegistry: ElementRegistry
    private lateinit var mockAccessibilityService: AccessibilityService

    @Before
    fun setUp() {
        mockElementRegistry = mockk(relaxed = true)
        mockAccessibilityService = mockk(relaxed = true)
        actionDispatcher = ActionDispatcher(mockElementRegistry)
    }

    @Test
    fun dispatch_withClickAction_executesClick() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget = mockk<AccessibilityNodeInfo>(relaxed = true)
        
        every { mockTarget.isClickable } returns true
        every { mockTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true
        every { mockRoot.findAccessibilityNodeInfosByText("Button") } returns listOf(mockTarget)

        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Button",
            reasoning = "Click the button"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withClickActionUsingElementRegistry_executesClick() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget = mockk<AccessibilityNodeInfo>(relaxed = true)
        
        every { mockTarget.isClickable } returns true
        every { mockTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true
        every { mockElementRegistry.getNode(1) } returns mockTarget

        val action = AgentAction(
            action = ActionType.CLICK,
            elementId = 1,
            target = "Registered Button"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withMissingElementRegistration_returnsFalse() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockElementRegistry.getElement(999) } returns null
        every { mockRoot.findAccessibilityNodeInfosByText(any()) } returns emptyList()

        val action = AgentAction(
            action = ActionType.CLICK,
            elementId = 999,
            target = "Missing Button"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isFalse()
    }

    @Test
    fun dispatch_withTypeAction_performsTextInput() {
        val mockRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        val mockTarget = mockk<AccessibilityNodeInfo>(relaxed = true)
        
        every { mockTarget.isEditable } returns true
        every { mockTarget.performAction(AccessibilityNodeInfo.ACTION_FOCUS) } returns true
        every { mockTarget.performAction(any(), any()) } returns true
        every { mockRoot.findAccessibilityNodeInfosByText("Input") } returns listOf(mockTarget)

        val action = AgentAction(
            action = ActionType.TYPE,
            target = "Input Field",
            text = "test input"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withTypeActionMissingText_returnsFalse() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        
        val action = AgentAction(
            action = ActionType.TYPE,
            target = "Input Field"
            // text is null
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isFalse()
    }

    @Test
    fun dispatch_withScrollActionDown_executesScroll() {
        val mockRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockAccessibilityService.resources.displayMetrics.widthPixels } returns 1080
        every { mockAccessibilityService.resources.displayMetrics.heightPixels } returns 2340
        every { mockAccessibilityService.dispatchGesture(any(), any(), any()) } returns true

        val action = AgentAction(
            action = ActionType.SCROLL,
            direction = ScrollDirection.DOWN
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withScrollActionUp_executesScroll() {
        val mockRoot = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockAccessibilityService.resources.displayMetrics.widthPixels } returns 1080
        every { mockAccessibilityService.resources.displayMetrics.heightPixels } returns 2340
        every { mockAccessibilityService.dispatchGesture(any(), any(), any()) } returns true

        val action = AgentAction(
            action = ActionType.SCROLL,
            direction = ScrollDirection.UP
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withHomeAction_executesGlobalAction() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) } returns true

        val action = AgentAction(action = ActionType.HOME)

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withBackAction_executesGlobalAction() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) } returns true

        val action = AgentAction(action = ActionType.BACK)

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withWaitAction_returnsTrue() {
        val mockRoot = mockk<AccessibilityNodeInfo>()

        val action = AgentAction(action = ActionType.WAIT)

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_withNoneAction_returnsTrue() {
        val mockRoot = mockk<AccessibilityNodeInfo>()

        val action = AgentAction(action = ActionType.NONE)

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_nullRoot_returnsFalse() {
        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Button"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, null, action)
        assertThat(result).isFalse()
    }

    @Test
    fun dispatch_clickActionNoTargetNoElementId_returnsFalse() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.findAccessibilityNodeInfosByText(any()) } returns emptyList()

        val action = AgentAction(
            action = ActionType.CLICK
            // No target, no elementId
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isFalse()
    }

    @Test
    fun dispatch_multipleClickMatches_usesFirst() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget1 = mockk<AccessibilityNodeInfo>(relaxed = true)
        val mockTarget2 = mockk<AccessibilityNodeInfo>(relaxed = true)
        
        every { mockTarget1.isClickable } returns true
        every { mockTarget2.isClickable } returns true
        every { mockTarget1.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true
        every { mockRoot.findAccessibilityNodeInfosByText("Next") } returns listOf(mockTarget1, mockTarget2)

        val action = AgentAction(
            action = ActionType.CLICK,
            target = "Next"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
        verify { mockTarget1.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }

    @Test
    fun dispatch_scrollWithElementRegistry_usesRegisteredElement() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockScrollable = mockk<AccessibilityNodeInfo>(relaxed = true)
        
        every { mockScrollable.isScrollable } returns true
        every { mockScrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } returns true
        every { mockElementRegistry.getNode(42) } returns mockScrollable

        val action = AgentAction(
            action = ActionType.SCROLL,
            elementId = 42,
            direction = ScrollDirection.DOWN
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
    }

    @Test
    fun dispatch_clickOnNonClickableNode_fallsBackToGesture() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        val mockTarget = mockk<AccessibilityNodeInfo>(relaxed = true)
        
        every { mockTarget.isClickable } returns false
        every { mockTarget.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(100, 100, 200, 200)
        }
        every { mockAccessibilityService.dispatchGesture(any(), any(), any()) } returns true
        every { mockRoot.findAccessibilityNodeInfosByText("NonClickable") } returns listOf(mockTarget)

        val action = AgentAction(
            action = ActionType.CLICK,
            target = "NonClickable"
        )

        val result = actionDispatcher.dispatch(mockAccessibilityService, mockRoot, action)
        assertThat(result).isTrue()
        verify { mockAccessibilityService.dispatchGesture(any(), any(), any()) }
    }
}
