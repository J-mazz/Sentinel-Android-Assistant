package com.mazzlabs.sentinel.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.mazzlabs.sentinel.model.ActionType
import com.mazzlabs.sentinel.model.AgentAction
import com.mazzlabs.sentinel.model.ScrollDirection

/**
 * ActionDispatcher - The Actuator
 * 
 * Parses validated JSON actions from the Cortex and executes them
 * through the Accessibility Service APIs.
 * 
 * Security: Only executes actions that have passed firewall validation.
 * All actions are atomic and reversible where possible.
 */
class ActionDispatcher {

    companion object {
        private const val TAG = "ActionDispatcher"
        private const val GESTURE_DURATION_MS = 100L
        private const val SCROLL_DURATION_MS = 300L
        private const val SCROLL_DISTANCE = 500
    }

    private val tempRect = Rect()

    /**
     * Dispatch an action through the accessibility service
     * 
     * @param service The accessibility service instance
     * @param root Root node of current window
     * @param action The validated action to execute
     * @return true if action was executed successfully
     */
    fun dispatch(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        action: AgentAction
    ): Boolean {
        Log.d(TAG, "Dispatching action: ${action.action} target: ${action.target}")
        
        return when (action.action) {
            ActionType.CLICK -> executeClick(service, root, action)
            ActionType.SCROLL -> executeScroll(service, action)
            ActionType.TYPE -> executeType(root, action)
            ActionType.HOME -> executeGlobalAction(service, AccessibilityService.GLOBAL_ACTION_HOME)
            ActionType.BACK -> executeGlobalAction(service, AccessibilityService.GLOBAL_ACTION_BACK)
            ActionType.WAIT -> executeWait()
            ActionType.NONE -> {
                Log.d(TAG, "NONE action - no operation")
                true
            }
        }
    }

    /**
     * Execute a click action on the specified target
     */
    private fun executeClick(
        service: AccessibilityService,
        root: AccessibilityNodeInfo,
        action: AgentAction
    ): Boolean {
        val target = action.target ?: run {
            Log.w(TAG, "CLICK action missing target")
            return false
        }

        // Find the target node
        val targetNode = findNodeByTarget(root, target)
        if (targetNode == null) {
            Log.w(TAG, "Target not found: $target")
            return false
        }

        return try {
            // Try direct click action first
            if (targetNode.isClickable) {
                val result = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Direct click on $target: $result")
                result
            } else {
                // Fall back to gesture-based click
                targetNode.getBoundsInScreen(tempRect)
                val centerX = tempRect.centerX().toFloat()
                val centerY = tempRect.centerY().toFloat()
                
                performTapGesture(service, centerX, centerY)
            }
        } finally {
            // Don't recycle - managed by system
        }
    }

    /**
     * Execute a scroll action
     */
    private fun executeScroll(service: AccessibilityService, action: AgentAction): Boolean {
        val direction = action.direction ?: ScrollDirection.DOWN
        
        // Get screen dimensions for scroll gesture
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        
        val (startX, startY, endX, endY) = when (direction) {
            ScrollDirection.UP -> listOf(centerX, centerY - SCROLL_DISTANCE/2, centerX, centerY + SCROLL_DISTANCE/2)
            ScrollDirection.DOWN -> listOf(centerX, centerY + SCROLL_DISTANCE/2, centerX, centerY - SCROLL_DISTANCE/2)
            ScrollDirection.LEFT -> listOf(centerX - SCROLL_DISTANCE/2, centerY, centerX + SCROLL_DISTANCE/2, centerY)
            ScrollDirection.RIGHT -> listOf(centerX + SCROLL_DISTANCE/2, centerY, centerX - SCROLL_DISTANCE/2, centerY)
        }
        
        return performSwipeGesture(service, startX, startY, endX, endY)
    }

    /**
     * Execute a type/input action
     */
    private fun executeType(root: AccessibilityNodeInfo, action: AgentAction): Boolean {
        val text = action.text ?: run {
            Log.w(TAG, "TYPE action missing text")
            return false
        }

        // Find focused editable field, or target if specified
        val targetNode = if (action.target != null) {
            findNodeByTarget(root, action.target)
        } else {
            findFocusedEditableNode(root)
        }

        if (targetNode == null) {
            Log.w(TAG, "No editable field found for TYPE action")
            return false
        }

        return try {
            // Focus the field first
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            
            // Set the text
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val result = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "TYPE action result: $result")
            result
        } finally {
            // Don't recycle
        }
    }

    /**
     * Execute a global action (HOME, BACK, etc.)
     */
    private fun executeGlobalAction(service: AccessibilityService, action: Int): Boolean {
        val result = service.performGlobalAction(action)
        Log.d(TAG, "Global action $action result: $result")
        return result
    }

    /**
     * Execute wait action (no-op with delay)
     */
    private fun executeWait(): Boolean {
        Log.d(TAG, "WAIT action - doing nothing")
        return true
    }

    /**
     * Find a node matching the target description
     */
    private fun findNodeByTarget(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val lowerTarget = target.lowercase()
        
        // Try exact text match first
        val byText = root.findAccessibilityNodeInfosByText(target)
        if (byText.isNotEmpty()) {
            return byText.firstOrNull { it.isClickable || it.isEditable }
                ?: byText.firstOrNull()
        }
        
        // Try by view ID
        val byId = root.findAccessibilityNodeInfosByViewId("*:id/$target")
        if (byId.isNotEmpty()) {
            return byId.firstOrNull()
        }
        
        // Fallback: traverse and fuzzy match
        return findNodeRecursive(root, lowerTarget)
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val nodeId = node.viewIdResourceName?.substringAfterLast("/")?.lowercase() ?: ""
        
        if (nodeText.contains(target) || nodeDesc.contains(target) || nodeId.contains(target)) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, target)
            if (result != null) return result
        }
        
        return null
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Find currently focused node
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.isEditable == true) {
            return focused
        }
        
        // Find any editable node
        return findEditableNodeRecursive(root)
    }

    private fun findEditableNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableNodeRecursive(child)
            if (result != null) return result
        }
        
        return null
    }

    /**
     * Perform a tap gesture at coordinates
     */
    private fun performTapGesture(
        service: AccessibilityService,
        x: Float,
        y: Float
    ): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION_MS))
            .build()
        
        return service.dispatchGesture(gesture, null, null)
    }

    /**
     * Perform a swipe gesture
     */
    private fun performSwipeGesture(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION_MS))
            .build()
        
        return service.dispatchGesture(gesture, null, null)
    }
}
