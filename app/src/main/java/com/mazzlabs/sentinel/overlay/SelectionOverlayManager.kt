package com.mazzlabs.sentinel.overlay

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * SelectionOverlayManager - Interactive screen selection overlay
 *
 * Allows user to:
 * 1. Capture screen snapshot
 * 2. Draw selection rectangle/circle
 * 3. Extract selected region for OCR/image analysis
 * 4. Send to agent for processing
 */
class SelectionOverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "SelectionOverlay"
    }

    interface SelectionListener {
        fun onSelectionComplete(selectedRegion: Rect, screenshot: Bitmap)
        fun onSelectionCanceled()
        fun onTextExtracted(text: String, region: Rect)
    }

    private var windowManager: WindowManager? = null
    private var overlayView: SelectionOverlayView? = null
    private var listener: SelectionListener? = null
    private var isShowing = false
    private var screenshot: Bitmap? = null

    fun setListener(listener: SelectionListener) {
        this.listener = listener
    }

    /**
     * Show selection overlay with frozen screen
     */
    fun show(screenBitmap: Bitmap) {
        if (isShowing) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            screenshot = screenBitmap

            overlayView = SelectionOverlayView(context, screenBitmap).apply {
                setOnSelectionListener(object : SelectionOverlayView.OnSelectionListener {
                    override fun onSelected(region: Rect) {
                        handleSelection(region)
                    }

                    override fun onCanceled() {
                        hide()
                        listener?.onSelectionCanceled()
                    }
                })
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(overlayView, params)
            isShowing = true

            Log.i(TAG, "Selection overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show selection overlay", e)
        }
    }

    private fun handleSelection(region: Rect) {
        val selectedBitmap = screenshot?.let { extractRegion(it, region) }

        if (selectedBitmap != null) {
            listener?.onSelectionComplete(region, selectedBitmap)
        }

        hide()
    }

    private fun extractRegion(source: Bitmap, region: Rect): Bitmap {
        val safeRegion = Rect(
            max(0, region.left),
            max(0, region.top),
            min(source.width, region.right),
            min(source.height, region.bottom)
        )

        return Bitmap.createBitmap(
            source,
            safeRegion.left,
            safeRegion.top,
            safeRegion.width(),
            safeRegion.height()
        )
    }

    fun hide() {
        if (!isShowing) return

        try {
            overlayView?.let { windowManager?.removeView(it) }
            overlayView = null
            screenshot?.recycle()
            screenshot = null
            isShowing = false

            Log.i(TAG, "Selection overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide selection overlay", e)
        }
    }

    fun isShowing(): Boolean = isShowing
}

/**
 * Custom view for drawing selection rectangle
 */
class SelectionOverlayView(
    context: Context,
    private val screenshot: Bitmap
) : FrameLayout(context) {

    interface OnSelectionListener {
        fun onSelected(region: Rect)
        fun onCanceled()
    }

    private var listener: OnSelectionListener? = null
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#4285F4")
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#334285F4")
        isAntiAlias = true
    }

    private val dimPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#AA000000")
    }

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false
    private var selectionMode = SelectionMode.RECTANGLE

    enum class SelectionMode {
        RECTANGLE, CIRCLE
    }

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setOnSelectionListener(listener: OnSelectionListener) {
        this.listener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawBitmap(screenshot, 0f, 0f, null)

        if (isDrawing) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            val rect = getSelectionRect()

            canvas.drawRect(rect, Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            })

            when (selectionMode) {
                SelectionMode.RECTANGLE -> {
                    canvas.drawRect(rect, fillPaint)
                    canvas.drawRect(rect, paint)
                }
                SelectionMode.CIRCLE -> {
                    val centerX = (rect.left + rect.right) / 2f
                    val centerY = (rect.top + rect.bottom) / 2f
                    val radius = min(rect.width(), rect.height()) / 2f
                    canvas.drawCircle(centerX, centerY, radius, fillPaint)
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
            }

            drawHandles(canvas, rect)
        }
    }

    private fun drawHandles(canvas: Canvas, rect: RectF) {
        val handleRadius = 20f
        val handlePaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            isAntiAlias = true
        }
        val handleBorderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#4285F4")
            isAntiAlias = true
        }

        val corners = listOf(
            PointF(rect.left, rect.top),
            PointF(rect.right, rect.top),
            PointF(rect.left, rect.bottom),
            PointF(rect.right, rect.bottom)
        )

        corners.forEach { corner ->
            canvas.drawCircle(corner.x, corner.y, handleRadius, handlePaint)
            canvas.drawCircle(corner.x, corner.y, handleRadius, handleBorderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isDrawing = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = event.x
                    currentY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    isDrawing = false
                    val rect = getSelectionRect()

                    if (rect.width() > 50 && rect.height() > 50) {
                        listener?.onSelected(
                            Rect(
                                rect.left.toInt(),
                                rect.top.toInt(),
                                rect.right.toInt(),
                                rect.bottom.toInt()
                            )
                        )
                    } else {
                        listener?.onCanceled()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDrawing = false
                listener?.onCanceled()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getSelectionRect(): RectF {
        return RectF(
            min(startX, currentX),
            min(startY, currentY),
            max(startX, currentX),
            max(startY, currentY)
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            listener?.onCanceled()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
