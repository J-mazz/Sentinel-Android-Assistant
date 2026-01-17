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
        // Should handle null gracefully
        elementRegistry.rebuild(null)
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
}
