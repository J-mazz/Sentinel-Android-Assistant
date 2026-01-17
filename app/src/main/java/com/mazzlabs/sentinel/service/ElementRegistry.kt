package com.mazzlabs.sentinel.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicInteger

/**
 * ElementRegistry - Indexed mapping of UI elements for grounded actions.
 */
class ElementRegistry {

    private val elements = mutableMapOf<Int, RegisteredElement>()
    private val nodeMap = mutableMapOf<Int, AccessibilityNodeInfo>()
    private val nextId = AtomicInteger(1)
    private var timestamp: Long = 0

    data class RegisteredElement(
        val id: Int,
        val label: String,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val isScrollable: Boolean
    )

    fun rebuild(root: AccessibilityNodeInfo): Int {
        clear()
        timestamp = System.currentTimeMillis()
        traverse(root)
        return elements.size
    }

    private fun traverse(node: AccessibilityNodeInfo) {
        if (!node.isVisibleToUser) return

        val element = extractElement(node)
        if (element != null) {
            elements[element.id] = element
            nodeMap[element.id] = node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverse(child)
            }
        }
    }

    private fun extractElement(node: AccessibilityNodeInfo): RegisteredElement? {
        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName?.substringAfterLast("/")

        if (text.isNullOrEmpty() &&
            contentDesc.isNullOrEmpty() &&
            viewId.isNullOrEmpty() &&
            !node.isClickable &&
            !node.isEditable &&
            !node.isScrollable) {
            return null
        }

        val label = when {
            !text.isNullOrBlank() -> text
            !contentDesc.isNullOrBlank() -> contentDesc
            !viewId.isNullOrBlank() -> viewId.replace("_", " ")
            node.isEditable -> "[text field]"
            node.isClickable -> "[button]"
            else -> "[element]"
        }.take(60)

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null

        val id = nextId.getAndIncrement()

        return RegisteredElement(
            id = id,
            label = label,
            bounds = bounds,
            isClickable = node.isClickable,
            isEditable = node.isEditable,
            isScrollable = node.isScrollable
        )
    }

    fun getElement(id: Int): RegisteredElement? = elements[id]

    fun getNode(id: Int): AccessibilityNodeInfo? = nodeMap[id]

    fun toPromptString(maxElements: Int = 60): String {
        if (elements.isEmpty()) return "[No interactive elements visible]"

        val important = elements.values
            .filter { it.isClickable || it.isEditable || it.isScrollable }
            .take(maxElements)

        if (important.isEmpty()) return "[No interactive elements visible]"

        return buildString {
            appendLine("Available UI elements (use element_id):")
            important.forEach { element ->
                val flags = buildList {
                    if (element.isClickable) add("click")
                    if (element.isEditable) add("edit")
                    if (element.isScrollable) add("scroll")
                }.joinToString("|")

                appendLine("  ${element.id}. [${flags}] ${element.label}")
            }
        }
    }

    fun getAgeMs(): Long = System.currentTimeMillis() - timestamp

    fun clear() {
        elements.clear()
        nodeMap.clear()
        nextId.set(1)
    }
}
