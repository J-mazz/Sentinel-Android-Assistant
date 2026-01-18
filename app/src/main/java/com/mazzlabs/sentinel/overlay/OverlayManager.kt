package com.mazzlabs.sentinel.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.mazzlabs.sentinel.R

/**
 * OverlayManager - Manages floating overlay button for agent activation
 * 
 * Provides a floating action button that users can tap to activate
 * the agent and issue voice commands.
 */
class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayManager"
    }

    interface OverlayListener {
        fun onOverlayTapped()
        fun onOverlayLongPressed()
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var listener: OverlayListener? = null
    private var isShowing = false

    // Position tracking for drag
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val layoutParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
    }

    fun setListener(listener: OverlayListener) {
        this.listener = listener
    }

    /**
     * Show the overlay button
     */
    fun show() {
        if (isShowing) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val themedContext = ContextThemeWrapper(context, R.style.Theme_Sentinel)
            overlayView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_button, null)
            
            setupTouchListener()
            
            windowManager?.addView(overlayView, layoutParams)
            isShowing = true
            
            Log.i(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    /**
     * Hide the overlay button
     */
    fun hide() {
        if (!isShowing) return

        try {
            overlayView?.let {
                windowManager?.removeView(it)
            }
            overlayView = null
            isShowing = false
            
            Log.i(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }

    /**
     * Toggle overlay visibility
     */
    fun toggle() {
        if (isShowing) hide() else show()
    }

    private fun setupTouchListener() {
        var isDragging = false
        var longPressTriggered = false

        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    longPressTriggered = false
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    // Start dragging if moved more than threshold
                    if (!isDragging && (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    if (!isDragging && !longPressTriggered) {
                        // It was a tap
                        listener?.onOverlayTapped()
                    }
                    true
                }
                
                else -> false
            }
        }

        overlayView?.setOnLongClickListener {
            longPressTriggered = true
            listener?.onOverlayLongPressed()
            true
        }
    }

    fun isShowing(): Boolean = isShowing
}
