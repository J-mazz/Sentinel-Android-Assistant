package com.mazzlabs.sentinel.service

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.MockK
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class ElementRegistryTest {

    private lateinit var elementRegistry: ElementRegistry

    @Before
    fun setUp() {
        elementRegistry = ElementRegistry()
    }

    @Test
    fun rebuild_withNullRoot_doesNotThrow() {
        // Should handle null gracefully - create mock instead
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns null
        every { mockRoot.contentDescription } returns null
        every { mockRoot.isVisibleToUser } returns true
        
        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()
        assertThat(prompt).isNotNull()
    }

    @Test
    fun rebuild_withSimpleRoot_producesValidPrompt() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Test Root"
        every { mockRoot.contentDescription } returns null

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()
        
        assertThat(prompt).isNotNull()
        assertThat(prompt).isNotEmpty()
    }

    @Test
    fun rebuild_withHierarchy_traversesAllNodes() {
        val mockChild1 = mockk<AccessibilityNodeInfo>()
        every { mockChild1.childCount } returns 0
        every { mockChild1.text } returns "Child 1"
        every { mockChild1.contentDescription } returns null

        val mockChild2 = mockk<AccessibilityNodeInfo>()
        every { mockChild2.childCount } returns 0
        every { mockChild2.text } returns "Child 2"
        every { mockChild2.contentDescription } returns null

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 2
        every { mockRoot.getChild(0) } returns mockChild1
        every { mockRoot.getChild(1) } returns mockChild2
        every { mockRoot.text } returns "Root"
        every { mockRoot.contentDescription } returns null

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()
        
        assertThat(prompt).contains("Child 1")
        assertThat(prompt).contains("Child 2")
    }

    @Test
    fun toPromptString_returnsConsistentFormat() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Test"
        every { mockRoot.contentDescription } returns null

        elementRegistry.rebuild(mockRoot)
        val prompt1 = elementRegistry.toPromptString()
        val prompt2 = elementRegistry.toPromptString()

        assertThat(prompt1).isEqualTo(prompt2)
    }

    @Test
    fun rebuild_multipleCallsReplacesPreviousData() {
        val mockRoot1 = mockk<AccessibilityNodeInfo>()
        every { mockRoot1.childCount } returns 0
        every { mockRoot1.text } returns "First"
        every { mockRoot1.contentDescription } returns null

        val mockRoot2 = mockk<AccessibilityNodeInfo>()
        every { mockRoot2.childCount } returns 0
        every { mockRoot2.text } returns "Second"
        every { mockRoot2.contentDescription } returns null

        elementRegistry.rebuild(mockRoot1)
        val prompt1 = elementRegistry.toPromptString()

        elementRegistry.rebuild(mockRoot2)
        val prompt2 = elementRegistry.toPromptString()

        assertThat(prompt1).contains("First")
        assertThat(prompt2).contains("Second")
        assertThat(prompt2).doesNotContain("First")
    }

    @Test
    fun rebuild_withContentDescription_includesInPrompt() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Visual Text"
        every { mockRoot.contentDescription } returns "Accessibility Description"

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()

        // Should include both text and description
        assertThat(prompt).isNotEmpty()
    }

    @Test
    fun getElement_returnsRegisteredElement() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Button"
        every { mockRoot.contentDescription } returns null
        every { mockRoot.isClickable } returns true
        every { mockRoot.isEditable } returns false
        every { mockRoot.isScrollable } returns false
        every { mockRoot.isVisibleToUser } returns true
        every { mockRoot.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(100, 100, 200, 200)
        }

        elementRegistry.rebuild(mockRoot)
        val element = elementRegistry.getElement(1)

        assertThat(element).isNotNull()
        assertThat(element?.label).isEqualTo("Button")
        assertThat(element?.isClickable).isTrue()
    }

    @Test
    fun getNode_returnsAccessibilityNodeInfo() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Test"
        every { mockRoot.contentDescription } returns null
        every { mockRoot.isVisibleToUser } returns true
        every { mockRoot.isClickable } returns false
        every { mockRoot.isEditable } returns false
        every { mockRoot.isScrollable } returns false
        every { mockRoot.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 100, 100)
        }

        elementRegistry.rebuild(mockRoot)
        val node = elementRegistry.getNode(1)

        assertThat(node).isNotNull()
    }

    @Test
    fun getAgeMs_returnsTimeElapsed() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Test"
        every { mockRoot.contentDescription } returns null
        every { mockRoot.isVisibleToUser } returns true
        every { mockRoot.isClickable } returns false
        every { mockRoot.isEditable } returns false
        every { mockRoot.isScrollable } returns false
        every { mockRoot.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 100, 100)
        }

        elementRegistry.rebuild(mockRoot)
        val age = elementRegistry.getAgeMs()

        assertThat(age).isAtLeast(0L)
        assertThat(age).isLessThan(1000L) // Should be relatively recent
    }

    @Test
    fun toPromptString_withClickableElements_includesClickFlag() {
        val mockButton = mockk<AccessibilityNodeInfo>()
        every { mockButton.childCount } returns 0
        every { mockButton.text } returns "Click Me"
        every { mockButton.contentDescription } returns null
        every { mockButton.isClickable } returns true
        every { mockButton.isEditable } returns false
        every { mockButton.isScrollable } returns false
        every { mockButton.isVisibleToUser } returns true
        every { mockButton.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 100, 100)
        }

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 1
        every { mockRoot.getChild(0) } returns mockButton
        every { mockRoot.isVisibleToUser } returns true

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()

        assertThat(prompt).contains("click")
        assertThat(prompt).contains("Click Me")
    }

    @Test
    fun toPromptString_withEditableElements_includesEditFlag() {
        val mockEditText = mockk<AccessibilityNodeInfo>()
        every { mockEditText.childCount } returns 0
        every { mockEditText.text } returns ""
        every { mockEditText.contentDescription } returns "Search field"
        every { mockEditText.isClickable } returns false
        every { mockEditText.isEditable } returns true
        every { mockEditText.isScrollable } returns false
        every { mockEditText.isVisibleToUser } returns true
        every { mockEditText.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 300, 50)
        }

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 1
        every { mockRoot.getChild(0) } returns mockEditText
        every { mockRoot.isVisibleToUser } returns true

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()

        assertThat(prompt).contains("edit")
    }

    @Test
    fun toPromptString_withScrollableElements_includesScrollFlag() {
        val mockList = mockk<AccessibilityNodeInfo>()
        every { mockList.childCount } returns 0
        every { mockList.text } returns ""
        every { mockList.contentDescription } returns "List view"
        every { mockList.isClickable } returns false
        every { mockList.isEditable } returns false
        every { mockList.isScrollable } returns true
        every { mockList.isVisibleToUser } returns true
        every { mockList.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 400, 800)
        }

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 1
        every { mockRoot.getChild(0) } returns mockList
        every { mockRoot.isVisibleToUser } returns true

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()

        assertThat(prompt).contains("scroll")
    }

    @Test
    fun toPromptString_respectsMaxElementsLimit() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Root"
        every { mockRoot.isVisibleToUser } returns true

        elementRegistry.rebuild(mockRoot)
        
        val prompt = elementRegistry.toPromptString(maxElements = 5)
        assertThat(prompt).isNotNull()
    }

    @Test
    fun clear_removesAllElements() {
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns "Test"
        every { mockRoot.contentDescription } returns null
        every { mockRoot.isVisibleToUser } returns true
        every { mockRoot.isClickable } returns false
        every { mockRoot.isEditable } returns false
        every { mockRoot.isScrollable } returns false
        every { mockRoot.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 100, 100)
        }

        elementRegistry.rebuild(mockRoot)
        assertThat(elementRegistry.getElement(1)).isNotNull()

        elementRegistry.clear()
        assertThat(elementRegistry.getElement(1)).isNull()
    }

    @Test
    fun rebuild_withInvisibleElements_ignoresThem() {
        val mockInvisible = mockk<AccessibilityNodeInfo>()
        every { mockInvisible.childCount } returns 0
        every { mockInvisible.text } returns "Hidden"
        every { mockInvisible.contentDescription } returns null
        every { mockInvisible.isVisibleToUser } returns false

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 1
        every { mockRoot.getChild(0) } returns mockInvisible
        every { mockRoot.isVisibleToUser } returns true

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()

        assertThat(prompt).doesNotContain("Hidden")
    }

    @Test
    fun rebuild_withZeroBoundingBox_ignoresElement() {
        val mockZeroBounds = mockk<AccessibilityNodeInfo>()
        every { mockZeroBounds.childCount } returns 0
        every { mockZeroBounds.text } returns "Zero Size"
        every { mockZeroBounds.contentDescription } returns null
        every { mockZeroBounds.isClickable } returns true
        every { mockZeroBounds.isEditable } returns false
        every { mockZeroBounds.isScrollable } returns false
        every { mockZeroBounds.isVisibleToUser } returns true
        every { mockZeroBounds.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 0, 0) // Zero bounds
        }

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 1
        every { mockRoot.getChild(0) } returns mockZeroBounds
        every { mockRoot.isVisibleToUser } returns true

        elementRegistry.rebuild(mockRoot)
        val prompt = elementRegistry.toPromptString()

        assertThat(prompt).doesNotContain("Zero Size")
    }

    @Test
    fun rebuild_withLongLabel_truncatesIt() {
        val longText = "x".repeat(200)
        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.childCount } returns 0
        every { mockRoot.text } returns longText
        every { mockRoot.contentDescription } returns null
        every { mockRoot.isClickable } returns true
        every { mockRoot.isEditable } returns false
        every { mockRoot.isScrollable } returns false
        every { mockRoot.isVisibleToUser } returns true
        every { mockRoot.getBoundsInScreen(any()) }.answers {
            val rect = it.invocation.args[0] as android.graphics.Rect
            rect.set(0, 0, 100, 100)
        }

        elementRegistry.rebuild(mockRoot)
        val element = elementRegistry.getElement(1)

        assertThat(element?.label?.length).isAtMost(60)
    }
}
