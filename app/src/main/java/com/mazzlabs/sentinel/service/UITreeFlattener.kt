package com.mazzlabs.sentinel.service

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.mazzlabs.sentinel.model.UIElement

/**
 * UITreeFlattener - Converts AccessibilityNodeInfo tree to semantic string
 * 
 * Security: Pure function - no side effects, no network, no storage.
 * Only reads UI state and produces a text representation.
 */
class UITreeFlattener {

    companion object {
        private const val TAG = "UITreeFlattener"
        private const val MAX_DEPTH = 20
        private const val MAX_ELEMENTS = 100
    }

    private val tempRect = Rect()

    /**
     * Flatten the accessibility node tree into a semantic string
     * Format: "Type[Label](attributes) | Type[Label](attributes) | ..."
     */
    fun flatten(root: AccessibilityNodeInfo): String {
        val elements = mutableListOf<UIElement>()
        traverseTree(root, elements, 0)
        
        if (elements.isEmpty()) {
            return "[Empty Screen]"
        }
        
        return elements.joinToString(" | ") { it.toString() }
    }

    /**
     * Get structured UI elements list
     */
    fun getElements(root: AccessibilityNodeInfo): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        traverseTree(root, elements, 0)
        return elements
    }

    private fun traverseTree(
        node: AccessibilityNodeInfo?,
        elements: MutableList<UIElement>,
        depth: Int
    ) {
        if (node == null || depth > MAX_DEPTH || elements.size >= MAX_ELEMENTS) {
            return
        }

        // Extract element info if it's interesting
        val element = extractElement(node)
        if (element != null) {
            elements.add(element)
        }

        // Traverse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    traverseTree(child, elements, depth + 1)
                } finally {
                    // Don't recycle children - system manages lifecycle
                }
            }
        }
    }

    private fun extractElement(node: AccessibilityNodeInfo): UIElement? {
        // Skip invisible or non-important nodes
        if (!node.isVisibleToUser) {
            return null
        }

        val text = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        val viewId = node.viewIdResourceName?.substringAfterLast("/")
        
        // Skip empty nodes unless they're interactive
        if (text.isNullOrEmpty() && 
            contentDesc.isNullOrEmpty() && 
            viewId.isNullOrEmpty() &&
            !node.isClickable && 
            !node.isEditable) {
            return null
        }

        // Get bounds
        node.getBoundsInScreen(tempRect)
        val bounds = "${tempRect.left},${tempRect.top},${tempRect.right},${tempRect.bottom}"

        return UIElement(
            type = mapNodeType(node),
            text = text,
            contentDescription = contentDesc,
            viewId = viewId,
            isClickable = node.isClickable,
            isEditable = node.isEditable,
            bounds = bounds
        )
    }

    private fun mapNodeType(node: AccessibilityNodeInfo): String {
        return when {
            node.isEditable -> "EditText"
            node.isCheckable -> if (node.isChecked) "CheckBox[✓]" else "CheckBox[ ]"
            node.isClickable && node.className?.contains("Button") == true -> "Button"
            node.isClickable && node.className?.contains("Image") == true -> "ImageButton"
            node.isClickable -> "Clickable"
            node.className?.contains("TextView") == true -> "Text"
            node.className?.contains("ImageView") == true -> "Image"
            node.className?.contains("RecyclerView") == true -> "List"
            node.className?.contains("ScrollView") == true -> "ScrollView"
            node.className?.contains("ViewGroup") == true -> "Container"
            else -> node.className?.toString()?.substringAfterLast(".") ?: "View"
        }
    }
}
