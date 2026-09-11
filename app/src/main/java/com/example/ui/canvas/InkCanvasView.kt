package com.example.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.data.export.ExportManager
import com.example.data.model.InkPoint
import com.example.data.model.InkStroke
import com.example.data.model.PageTemplate
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class InkTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
    PAN_ZOOM
}

enum class EraserMode(val title: String, val description: String) {
    STROKE("Stroke Eraser", "Removes entire stroke on contact"),
    PRECISION("Precision Eraser", "Erases within the brush radius")
}

sealed class UndoAction {
    data class AddStroke(val stroke: InkStroke) : UndoAction()
    data class DeleteStroke(val stroke: InkStroke, val index: Int) : UndoAction()
    data class EraseSession(
        val removed: List<Pair<Int, InkStroke>>,
        val added: List<Pair<Int, InkStroke>> = emptyList()
    ) : UndoAction()
    data class ClearAll(val strokes: List<InkStroke>) : UndoAction()
}

class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val PAGE_WIDTH = 1200f
        const val PAGE_HEIGHT = 1600f
        private const val MAX_UNDO_STACK_SIZE = 30
        private const val MIN_SCALE = 0.35f
        private const val MAX_SCALE = 5.0f
    }

    // Drawing State
    private val strokes = mutableListOf<InkStroke>()
    private val undoStack = ArrayDeque<UndoAction>()
    private val redoStack = ArrayDeque<UndoAction>()

    // Current Tool Settings
    var currentTool: InkTool = InkTool.PEN
    var currentColor: Int = 0xFFD0BCFF.toInt() // Lavender Glow
    var currentStrokeWidth: Float = 5f
    var eraserMode: EraserMode = EraserMode.STROKE
    var eraserRadius: Float = 32f

    // Active Eraser State
    private var isErasing = false
    private var eraserTouchX: Float? = null
    private var eraserTouchY: Float? = null
    private var lastEraserPx = 0f
    private var lastEraserPy = 0f
    private val currentEraseRemoved = mutableListOf<Pair<Int, InkStroke>>()
    private val currentEraseAdded = mutableListOf<Pair<Int, InkStroke>>()

    var currentTemplate: PageTemplate = PageTemplate.LINED
        set(value) {
            field = value
            invalidate()
        }

    var pdfBackgroundBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // Viewport Transform
    private var scaleFactor = 1.0f
    private var panX = 0f
    private var panY = 0f
    private var isFirstLayout = true

    // Active Live Drawing Stroke
    private val activePoints = mutableListOf<InkPoint>()
    private val activePath = Path()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isMultiTouch = false
    private var lastPanTouchX = 0f
    private var lastPanTouchY = 0f

    // Callback for state changes (stroke count, undo/redo availability)
    var onStateChanged: ((strokeCount: Int, canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // Pre-allocated paints to avoid GC allocations during onDraw (60+ FPS budget)
    private val paperPaint = Paint().apply {
        color = 0xFF1D1B20.toInt() // Sophisticated dark paper
        style = Paint.Style.FILL
    }
    private val paperShadowPaint = Paint().apply {
        color = 0x66000000
        style = Paint.Style.FILL
    }
    private val paperBorderPaint = Paint().apply {
        color = 0xFF49454F.toInt() // Sophisticated dark border
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val lineTemplatePaint = Paint().apply {
        color = 0xFF2B2930.toInt() // Subtle dark rule
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val marginLinePaint = Paint().apply {
        color = 0x4DFF897D.toInt() // Coral margin line with opacity
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val gridTemplatePaint = Paint().apply {
        color = 0xFF2B2930.toInt() // Subtle dark grid
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val dotTemplatePaint = Paint().apply {
        color = 0xFF49454F.toInt() // Subtle dot grid
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val eraserReticleFillPaint = Paint().apply {
        color = 0x2EFF897D.toInt() // Translucent coral reticle fill
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val eraserReticleStrokePaint = Paint().apply {
        color = 0xCCFF897D.toInt() // Coral reticle outline
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val eraserReticleDotPaint = Paint().apply {
        color = 0xFFFF897D.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val visiblePageRect = RectF()
    private val pageBoundsRect = RectF(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT)
    private val pdfDestRect = RectF()
    private val pdfRenderPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val highlighterXfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY
            val oldScale = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)

            // Keep focus point stable during zoom
            val factor = scaleFactor / oldScale
            panX = focusX - (focusX - panX) * factor
            panY = focusY - (focusY - panY) * factor

            invalidate()
            return true
        }
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isFirstLayout && w > 0 && h > 0) {
            fitPageToScreen()
            isFirstLayout = false
        }
    }

    fun fitPageToScreen() {
        if (width <= 0 || height <= 0) return
        val horizontalPadding = 48f
        val verticalPadding = 48f
        val availW = width - horizontalPadding * 2f
        val availH = height - verticalPadding * 2f

        val scaleX = availW / PAGE_WIDTH
        val scaleY = availH / PAGE_HEIGHT
        scaleFactor = min(scaleX, scaleY).coerceIn(MIN_SCALE, MAX_SCALE)

        // Center page on screen
        panX = (width - PAGE_WIDTH * scaleFactor) / 2f
        panY = (height - PAGE_HEIGHT * scaleFactor) / 2f
        invalidate()
    }

    fun setStrokes(newStrokes: List<InkStroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        undoStack.clear()
        redoStack.clear()
        notifyStateChanged()
        invalidate()
    }

    fun getStrokes(): List<InkStroke> {
        return strokes.toList()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        when (action) {
            is UndoAction.AddStroke -> {
                strokes.remove(action.stroke)
                redoStack.addLast(action)
            }
            is UndoAction.DeleteStroke -> {
                val insertIdx = action.index.coerceIn(0, strokes.size)
                strokes.add(insertIdx, action.stroke)
                redoStack.addLast(action)
            }
            is UndoAction.EraseSession -> {
                // Undo: Remove newly added sub-strokes
                for ((_, added) in action.added.reversed()) {
                    strokes.remove(added)
                }
                // Re-insert original removed strokes at their respective indices
                val sortedRemoved = action.removed.sortedBy { it.first }
                for ((idx, stroke) in sortedRemoved) {
                    val insertIdx = idx.coerceIn(0, strokes.size)
                    strokes.add(insertIdx, stroke)
                }
                redoStack.addLast(action)
            }
            is UndoAction.ClearAll -> {
                strokes.addAll(action.strokes)
                redoStack.addLast(action)
            }
        }
        notifyStateChanged()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        when (action) {
            is UndoAction.AddStroke -> {
                strokes.add(action.stroke)
                undoStack.addLast(action)
            }
            is UndoAction.DeleteStroke -> {
                strokes.remove(action.stroke)
                undoStack.addLast(action)
            }
            is UndoAction.EraseSession -> {
                // Redo: Remove restored strokes
                for ((_, stroke) in action.removed) {
                    strokes.remove(stroke)
                }
                // Re-add sub-strokes
                val sortedAdded = action.added.sortedBy { it.first }
                for ((idx, added) in sortedAdded) {
                    val insertIdx = idx.coerceIn(0, strokes.size)
                    strokes.add(insertIdx, added)
                }
                undoStack.addLast(action)
            }
            is UndoAction.ClearAll -> {
                strokes.clear()
                undoStack.addLast(action)
            }
        }
        notifyStateChanged()
        invalidate()
    }

    fun clearAllStrokes() {
        if (strokes.isEmpty()) return
        val cleared = strokes.toList()
        strokes.clear()
        pushUndo(UndoAction.ClearAll(cleared))
        notifyStateChanged()
        invalidate()
    }

    private fun pushUndo(action: UndoAction) {
        undoStack.addLast(action)
        if (undoStack.size > MAX_UNDO_STACK_SIZE) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        onStateChanged?.invoke(strokes.size, canUndo(), canRedo())
    }

    // Coordinate conversion
    private fun screenToPage(sx: Float, sy: Float): PointF {
        val px = (sx - panX) / scaleFactor
        val py = (sy - panY) / scaleFactor
        return PointF(px, py)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        if (pointerCount > 1) {
            isMultiTouch = true
            // If multi-touch pan/zoom, cancel any in-progress stroke
            if (activePoints.isNotEmpty()) {
                activePoints.clear()
                activePath.reset()
                invalidate()
            }
            // Two-finger pan
            if (event.actionMasked == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress) {
                val curMidX = (event.getX(0) + event.getX(1)) / 2f
                val curMidY = (event.getY(0) + event.getY(1)) / 2f
                if (lastPanTouchX != 0f && lastPanTouchY != 0f) {
                    val dx = curMidX - lastPanTouchX
                    val dy = curMidY - lastPanTouchY
                    panX += dx
                    panY += dy
                    invalidate()
                }
                lastPanTouchX = curMidX
                lastPanTouchY = curMidY
            } else {
                lastPanTouchX = (event.getX(0) + event.getX(1)) / 2f
                lastPanTouchY = (event.getY(0) + event.getY(1)) / 2f
            }
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            lastPanTouchX = 0f
            lastPanTouchY = 0f
        }

        if (isMultiTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                isMultiTouch = false
            }
            return true
        }

        // Single touch handling
        val sx = event.x
        val sy = event.y
        val pagePoint = screenToPage(sx, sy)

        when (currentTool) {
            InkTool.PAN_ZOOM -> {
                handlePanTouch(event, sx, sy)
            }
            InkTool.ERASER -> {
                handleEraserTouch(event, pagePoint.x, pagePoint.y)
            }
            InkTool.PEN, InkTool.HIGHLIGHTER -> {
                handleDrawTouch(event, pagePoint.x, pagePoint.y)
            }
        }

        return true
    }

    private fun handlePanTouch(event: MotionEvent, sx: Float, sy: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = sx
                lastTouchY = sy
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = sx - lastTouchX
                val dy = sy - lastTouchY
                panX += dx
                panY += dy
                lastTouchX = sx
                lastTouchY = sy
                invalidate()
            }
        }
    }

    private fun handleDrawTouch(event: MotionEvent, px: Float, py: Float) {
        // Evaluate pressure if reported meaningfully by the device (PRD 5.1)
        val rawPressure = event.pressure
        val pressure = if (rawPressure > 0.05f && rawPressure != 1.0f) {
            rawPressure.coerceIn(0.2f, 1.8f)
        } else {
            1.0f
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePoints.clear()
                activePath.reset()
                activePoints.add(InkPoint(px, py, pressure))
                activePath.moveTo(px, py)
                lastTouchX = px
                lastTouchY = py
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val historySize = event.historySize
                for (h in 0 until historySize) {
                    val hx = (event.getHistoricalX(h) - panX) / scaleFactor
                    val hy = (event.getHistoricalY(h) - panY) / scaleFactor
                    val hp = event.getHistoricalPressure(h).coerceIn(0.2f, 1.8f)
                    addSmoothPoint(hx, hy, hp)
                }
                addSmoothPoint(px, py, pressure)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                addSmoothPoint(px, py, pressure)
                if (activePoints.isNotEmpty()) {
                    val isHighlighter = (currentTool == InkTool.HIGHLIGHTER)
                    val width = if (isHighlighter) currentStrokeWidth * 3f else currentStrokeWidth
                    val stroke = InkStroke(
                        points = ArrayList(activePoints),
                        color = currentColor,
                        strokeWidth = width,
                        isHighlighter = isHighlighter,
                        bounds = InkStroke.computeBounds(activePoints, width)
                    )
                    strokes.add(stroke)
                    pushUndo(UndoAction.AddStroke(stroke))
                    activePoints.clear()
                    activePath.reset()
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activePoints.clear()
                activePath.reset()
                invalidate()
            }
        }
    }

    private fun addSmoothPoint(px: Float, py: Float, pressure: Float) {
        activePoints.add(InkPoint(px, py, pressure))
        val midX = (lastTouchX + px) / 2f
        val midY = (lastTouchY + py) / 2f
        activePath.quadTo(lastTouchX, lastTouchY, midX, midY)
        lastTouchX = px
        lastTouchY = py
    }

    private fun handleEraserTouch(event: MotionEvent, px: Float, py: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isErasing = true
                eraserTouchX = px
                eraserTouchY = py
                lastEraserPx = px
                lastEraserPy = py
                currentEraseRemoved.clear()
                currentEraseAdded.clear()
                eraseAtPoint(px, py)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                isErasing = true
                val historySize = event.historySize
                for (h in 0 until historySize) {
                    val hx = (event.getHistoricalX(h) - panX) / scaleFactor
                    val hy = (event.getHistoricalY(h) - panY) / scaleFactor
                    eraseAlongSegment(lastEraserPx, lastEraserPy, hx, hy)
                    lastEraserPx = hx
                    lastEraserPy = hy
                }
                eraseAlongSegment(lastEraserPx, lastEraserPy, px, py)
                lastEraserPx = px
                lastEraserPy = py
                eraserTouchX = px
                eraserTouchY = py
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isErasing = false
                eraserTouchX = null
                eraserTouchY = null
                if (currentEraseRemoved.isNotEmpty() || currentEraseAdded.isNotEmpty()) {
                    pushUndo(
                        UndoAction.EraseSession(
                            removed = ArrayList(currentEraseRemoved),
                            added = ArrayList(currentEraseAdded)
                        )
                    )
                    currentEraseRemoved.clear()
                    currentEraseAdded.clear()
                }
                notifyStateChanged()
                invalidate()
            }
        }
    }

    private fun eraseAlongSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dist = hypot(x2 - x1, y2 - y1)
        val step = (eraserRadius * 0.4f).coerceAtLeast(8f)
        val steps = max(1, (dist / step).toInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val ix = x1 + t * (x2 - x1)
            val iy = y1 + t * (y2 - y1)
            eraseAtPoint(ix, iy)
        }
    }

    private fun eraseAtPoint(px: Float, py: Float) {
        if (eraserMode == EraserMode.STROKE) {
            eraseStrokeAt(px, py)
        } else {
            erasePrecisionAt(px, py)
        }
    }

    private fun eraseStrokeAt(px: Float, py: Float) {
        val hitIndex = findStrokeAt(px, py, eraserRadius)
        if (hitIndex != -1) {
            val removed = strokes.removeAt(hitIndex)
            currentEraseRemoved.add(Pair(hitIndex, removed))
        }
    }

    private fun erasePrecisionAt(px: Float, py: Float) {
        val rSq = eraserRadius * eraserRadius
        // Iterate backwards so removals preserve valid indices
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            val bounds = stroke.bounds
            if (px < bounds.left - eraserRadius || px > bounds.right + eraserRadius ||
                py < bounds.top - eraserRadius || py > bounds.bottom + eraserRadius
            ) {
                continue
            }

            var anyPointInCircle = false
            for (p in stroke.points) {
                val dSq = (p.x - px) * (p.x - px) + (p.y - py) * (p.y - py)
                if (dSq <= rSq) {
                    anyPointInCircle = true
                    break
                }
            }

            if (!anyPointInCircle) {
                for (j in 0 until stroke.points.size - 1) {
                    val p1 = stroke.points[j]
                    val p2 = stroke.points[j + 1]
                    if (distSqToSegment(px, py, p1.x, p1.y, p2.x, p2.y) <= rSq) {
                        anyPointInCircle = true
                        break
                    }
                }
            }

            if (!anyPointInCircle) continue

            // Split into sub-strokes where points are outside circle
            val newSubStrokes = mutableListOf<InkStroke>()
            val curPoints = mutableListOf<InkPoint>()

            for (p in stroke.points) {
                val dSq = (p.x - px) * (p.x - px) + (p.y - py) * (p.y - py)
                if (dSq > rSq) {
                    curPoints.add(p)
                } else {
                    if (curPoints.isNotEmpty()) {
                        newSubStrokes.add(
                            InkStroke(
                                points = ArrayList(curPoints),
                                color = stroke.color,
                                strokeWidth = stroke.strokeWidth,
                                isHighlighter = stroke.isHighlighter,
                                bounds = InkStroke.computeBounds(curPoints, stroke.strokeWidth)
                            )
                        )
                        curPoints.clear()
                    }
                }
            }
            if (curPoints.isNotEmpty()) {
                newSubStrokes.add(
                    InkStroke(
                        points = ArrayList(curPoints),
                        color = stroke.color,
                        strokeWidth = stroke.strokeWidth,
                        isHighlighter = stroke.isHighlighter,
                        bounds = InkStroke.computeBounds(curPoints, stroke.strokeWidth)
                    )
                )
            }

            val removed = strokes.removeAt(i)
            currentEraseRemoved.add(Pair(i, removed))
            for (subIdx in newSubStrokes.indices) {
                val insertPos = i + subIdx
                val sub = newSubStrokes[subIdx]
                strokes.add(insertPos, sub)
                currentEraseAdded.add(Pair(insertPos, sub))
            }
        }
    }

    /**
     * Fast distance check for stroke eraser.
     * Uses bounding box pre-filtering followed by segment distance test.
     */
    private fun findStrokeAt(px: Float, py: Float, radius: Float): Int {
        val touchTolerance = (radius / scaleFactor).coerceAtLeast(16f)
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            val bounds = stroke.bounds
            if (px < bounds.left - touchTolerance || px > bounds.right + touchTolerance ||
                py < bounds.top - touchTolerance || py > bounds.bottom + touchTolerance
            ) {
                continue
            }

            val points = stroke.points
            val threshold = (stroke.strokeWidth / 2f + touchTolerance)
            val thresholdSq = threshold * threshold

            for (j in 0 until points.size - 1) {
                val p1 = points[j]
                val p2 = points[j + 1]
                val distSq = distSqToSegment(px, py, p1.x, p1.y, p2.x, p2.y)
                if (distSq <= thresholdSq) {
                    return i
                }
            }
            if (points.size == 1) {
                val p = points[0]
                val dSq = (px - p.x) * (px - p.x) + (py - p.y) * (py - p.y)
                if (dSq <= thresholdSq) {
                    return i
                }
            }
        }
        return -1
    }

    private fun distSqToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            val dxp = px - x1
            val dyp = py - y1
            return dxp * dxp + dyp * dyp
        }
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0f, 1f)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        val distX = px - projX
        val distY = py - projY
        return distX * distX + distY * distY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        // Calculate visible viewport rectangle in page space for viewport culling
        val vLeft = (-panX) / scaleFactor
        val vTop = (-panY) / scaleFactor
        val vRight = (width - panX) / scaleFactor
        val vBottom = (height - panY) / scaleFactor
        visiblePageRect.set(vLeft, vTop, vRight, vBottom)

        // 1. Draw Paper Shadow (subtle depth)
        val shadowOffset = 8f
        canvas.drawRect(
            shadowOffset,
            shadowOffset,
            PAGE_WIDTH + shadowOffset,
            PAGE_HEIGHT + shadowOffset,
            paperShadowPaint
        )

        // 2. Draw Paper Surface
        canvas.drawRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, paperPaint)
        canvas.drawRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, paperBorderPaint)

        // Clip drawing to page bounds
        canvas.save()
        canvas.clipRect(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT)

        // 3. Draw PDF or Procedural Template Background
        if (pdfBackgroundBitmap != null && !pdfBackgroundBitmap!!.isRecycled) {
            val bmp = pdfBackgroundBitmap!!
            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val scale = minOf(PAGE_WIDTH / bmpW, PAGE_HEIGHT / bmpH)
            val fitW = bmpW * scale
            val fitH = bmpH * scale
            val left = (PAGE_WIDTH - fitW) / 2f
            val top = (PAGE_HEIGHT - fitH) / 2f
            pdfDestRect.set(left, top, left + fitW, top + fitH)
            canvas.drawBitmap(bmp, null, pdfDestRect, pdfRenderPaint)
        } else {
            drawProceduralTemplate(canvas)
        }

        // 4. Draw Committed Strokes with Viewport Culling
        drawCommittedStrokes(canvas)

        // 5. Draw Active Stroke
        if (activePoints.isNotEmpty()) {
            drawActiveStroke(canvas)
        }

        // 6. Draw Eraser Reticle Indicator
        if (currentTool == InkTool.ERASER && isErasing && eraserTouchX != null && eraserTouchY != null) {
            val ex = eraserTouchX!!
            val ey = eraserTouchY!!
            canvas.drawCircle(ex, ey, eraserRadius, eraserReticleFillPaint)
            canvas.drawCircle(ex, ey, eraserRadius, eraserReticleStrokePaint)
            canvas.drawCircle(ex, ey, 3.5f, eraserReticleDotPaint)
        }

        canvas.restore() // Undo clipRect
        canvas.restore() // Undo translate & scale
    }

    private fun drawProceduralTemplate(canvas: Canvas) {
        when (currentTemplate) {
            PageTemplate.BLANK -> {
                // Blank page, nothing needed
            }
            PageTemplate.LINED -> {
                val lineSpacing = 48f
                var y = 120f
                while (y < PAGE_HEIGHT - 60f) {
                    if (y >= visiblePageRect.top - 10f && y <= visiblePageRect.bottom + 10f) {
                        canvas.drawLine(0f, y, PAGE_WIDTH, y, lineTemplatePaint)
                    }
                    y += lineSpacing
                }
                // Margin line
                val marginX = 140f
                if (marginX >= visiblePageRect.left - 10f && marginX <= visiblePageRect.right + 10f) {
                    canvas.drawLine(marginX, 0f, marginX, PAGE_HEIGHT, marginLinePaint)
                }
            }
            PageTemplate.GRID -> {
                val gridSize = 40f
                var x = gridSize
                while (x < PAGE_WIDTH) {
                    if (x >= visiblePageRect.left - 10f && x <= visiblePageRect.right + 10f) {
                        canvas.drawLine(x, 0f, x, PAGE_HEIGHT, gridTemplatePaint)
                    }
                    x += gridSize
                }
                var y = gridSize
                while (y < PAGE_HEIGHT) {
                    if (y >= visiblePageRect.top - 10f && y <= visiblePageRect.bottom + 10f) {
                        canvas.drawLine(0f, y, PAGE_WIDTH, y, gridTemplatePaint)
                    }
                    y += gridSize
                }
            }
            PageTemplate.DOT_GRID -> {
                val dotSpacing = 40f
                val dotRadius = 2.5f
                var x = dotSpacing
                while (x < PAGE_WIDTH) {
                    if (x >= visiblePageRect.left - 10f && x <= visiblePageRect.right + 10f) {
                        var y = dotSpacing
                        while (y < PAGE_HEIGHT) {
                            if (y >= visiblePageRect.top - 10f && y <= visiblePageRect.bottom + 10f) {
                                canvas.drawCircle(x, y, dotRadius, dotTemplatePaint)
                            }
                            y += dotSpacing
                        }
                    }
                    x += dotSpacing
                }
            }
        }
    }

    private fun drawCommittedStrokes(canvas: Canvas) {
        for (stroke in strokes) {
            // Viewport culling: skip stroke if its bounding box doesn't intersect visible rect
            if (!RectF.intersects(stroke.bounds, visiblePageRect)) {
                continue
            }

            val points = stroke.points
            if (points.isEmpty()) continue

            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.strokeWidth

            if (stroke.isHighlighter) {
                val c = stroke.color
                strokePaint.color = Color.argb(120, Color.red(c), Color.green(c), Color.blue(c))
                strokePaint.strokeWidth = (stroke.strokeWidth * 2.5f).coerceAtLeast(18f)
                strokePaint.xfermode = highlighterXfermode
            } else {
                strokePaint.xfermode = null
            }

            if (points.size == 1) {
                val p = points[0]
                strokePaint.style = Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, stroke.strokeWidth / 2f, strokePaint)
                strokePaint.style = Paint.Style.STROKE
                continue
            }

            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                path.quadTo(prev.x, prev.y, midX, midY)
            }
            val last = points.last()
            path.lineTo(last.x, last.y)

            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawActiveStroke(canvas: Canvas) {
        val isHighlighter = (currentTool == InkTool.HIGHLIGHTER)
        val w = if (isHighlighter) currentStrokeWidth * 3f else currentStrokeWidth

        strokePaint.strokeWidth = w
        if (isHighlighter) {
            val c = currentColor
            strokePaint.color = Color.argb(120, Color.red(c), Color.green(c), Color.blue(c))
            strokePaint.xfermode = highlighterXfermode
        } else {
            strokePaint.color = currentColor
            strokePaint.xfermode = null
        }

        if (activePoints.size == 1) {
            val p = activePoints[0]
            strokePaint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, w / 2f, strokePaint)
            strokePaint.style = Paint.Style.STROKE
        } else {
            canvas.drawPath(activePath, strokePaint)
        }
        strokePaint.xfermode = null
    }
}
