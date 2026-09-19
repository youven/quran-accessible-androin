package com.youven.quranaccessible.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.youven.quranaccessible.data.ChapterStart
import com.youven.quranaccessible.data.LoadedPage
import com.youven.quranaccessible.data.QuranMetadata
import kotlin.math.abs

/** QCF words are font ligatures, drawn individually RTL without Unicode reshaping. */
class MushafTextView(context: Context) : View(context) {
    private val viewport = PageViewport()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ornamentPath = Path()
    private var loaded: LoadedPage? = null

    var activeVerse: String? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoom(detector.scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    })

    var onScrollListener: ((distanceX: Float, distanceY: Float) -> Unit)? = null
    var onTapListener: (() -> Unit)? = null

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!pinch.isInProgress && viewport.scale > viewport.minimum * 1.05f) {
                viewport.pan(-distanceX, -distanceY)
                invalidate()
            }
            onScrollListener?.invoke(distanceX, distanceY)
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (viewport.scale > viewport.minimum * 1.1f) viewport.reset() else viewport.zoom(2f, e.x, e.y)
            invalidate()
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performClick()
            onTapListener?.invoke()
            return true
        }
    })

    private var initialX = 0f
    private var initialY = 0f

    init {
        // The adjacent accessible reading mode exposes actual Unicode, never QCF codes.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(page: LoadedPage) {
        if (loaded !== page) {
            loaded = page
            viewport.reset()
            invalidate()
        }
    }

    fun zoomBy(factor: Float) {
        viewport.zoom(factor, width / 2f, height / 2f)
        invalidate()
    }

    fun resetZoom() {
        viewport.reset()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        viewport.resize(w.toFloat(), h.toFloat())
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        gestures.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                if (viewport.scale > viewport.minimum * 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinch.isInProgress || viewport.scale > viewport.minimum * 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else {
                    val dx = abs(event.x - initialX)
                    val dy = abs(event.y - initialY)
                    if (dx > dy && dx > 8f) {
                        // Horizontal swipe: let HorizontalPager navigate pages
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (dy > dx && dy > 8f) {
                        // Vertical drag: disallow so pager doesn't falsely trigger
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = loaded ?: return

        canvas.save()
        canvas.translate(viewport.x, viewport.y)
        canvas.scale(viewport.scale, viewport.scale)

        // 1. Parchment paper background filling the full device aspect ratio
        ink.color = Color.rgb(255, 253, 244)
        ink.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 600f, viewport.pageHeight, ink)

        // 2. Measure line widths to scale text dynamically to fill full width
        ink.typeface = result.font
        ink.textSize = 32f
        val widths = result.page.lines.mapValues { (_, words) ->
            words.sumOf { ink.measureText(it.glyph).toDouble() }.toFloat()
        }

        // Maximize page width and enlarge text size: target 574f (out of 600f)
        val targetWidth = if (result.page.number <= 2) 480f else 574f
        val maxW = (widths.values.maxOrNull() ?: targetWidth).coerceAtLeast(300f)
        val scaleFactor = (targetWidth / maxW).coerceIn(1.0f, 1.35f)
        val fontSize = 32f * scaleFactor

        // 3. Dynamic vertical line layout matching actual device screen height
        val pageH = viewport.pageHeight
        val lineSpacing: Float
        val startTop: Float

        if (result.page.number <= 2) {
            lineSpacing = ((pageH - 220f) / 10f).coerceIn(52f, 68f)
            startTop = ((pageH - 8f * lineSpacing - 40f) / 2f).coerceAtLeast(130f)
        } else {
            lineSpacing = ((pageH - 90f) / 16f).coerceIn(52f, 66f)
            val textBlockHeight = 15f * lineSpacing
            startTop = ((pageH - textBlockHeight - 36f) / 2f).coerceAtLeast(24f)
        }

        fun baseline(line: Int): Float = startTop + line * lineSpacing

        // 4. Highlight active reciting verse in light brown (بني فاتح)
        val currentActive = activeVerse
        if (!currentActive.isNullOrBlank()) {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(175, 226, 206, 176) // Light brown / بني فاتح
            ink.typeface = result.font
            ink.textSize = fontSize

            result.page.lines.forEach { (line, words) ->
                if (words.any { it.verse == currentActive }) {
                    val lineWidth = words.sumOf { ink.measureText(it.glyph).toDouble() }.toFloat()
                    var right = (600f + lineWidth) / 2f
                    var minX = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    words.forEach { word ->
                        val wWidth = ink.measureText(word.glyph)
                        val wLeft = right - wWidth
                        val wRight = right
                        if (word.verse == currentActive) {
                            minX = minOf(minX, wLeft)
                            maxX = maxOf(maxX, wRight)
                        }
                        right -= wWidth
                    }
                    if (minX < maxX) {
                        val bLine = baseline(line)
                        val top = bLine - fontSize * 0.85f
                        val bottom = bLine + fontSize * 0.28f
                        canvas.drawRoundRect(minX - 4f, top, maxX + 4f, bottom, 8f, 8f, highlightPaint)
                    }
                }
            }
        }

        // 5. Draw Quran verses text
        ink.color = Color.rgb(32, 39, 32)
        result.page.lines.forEach { (line, words) ->
            ink.typeface = result.font
            ink.textSize = fontSize
            ink.textAlign = Paint.Align.LEFT
            val lineWidth = words.sumOf { ink.measureText(it.glyph).toDouble() }.toFloat()
            var right = (600f + lineWidth) / 2f
            words.forEach { word ->
                right -= ink.measureText(word.glyph)
                canvas.drawText(word.glyph, right, baseline(line), ink)
            }
        }

        // 5. Draw Surah Headers with Madinah Mushaf Ornaments
        result.page.starts.forEach { start ->
            val headerBaseline = baseline(start.headerLine)
            drawMadaniSurahHeader(canvas, start, headerBaseline, lineSpacing, result)

            // Basmala line
            start.basmalaLine?.let { line ->
                ink.typeface = result.basmalaFont
                ink.textSize = (fontSize * 0.95f).coerceIn(30f, 36f)
                ink.textAlign = Paint.Align.LEFT
                val glyphs = listOf("\uFC41", "\uFC42", "\uFC43", "\uFC44")
                var right = (600f + glyphs.sumOf { ink.measureText(it).toDouble() }.toFloat()) / 2f
                glyphs.forEach { glyph ->
                    right -= ink.measureText(glyph)
                    canvas.drawText(glyph, right, baseline(line), ink)
                }
            }
        }

        // 6. Draw Page Number at the bottom center
        ink.typeface = Typeface.DEFAULT
        ink.textSize = 17f
        ink.color = Color.rgb(100, 110, 100)
        ink.textAlign = Paint.Align.CENTER
        val pageNumY = (pageH - 22f).coerceAtLeast(baseline(if (result.page.number <= 2) 8 else 15) + 26f)
        canvas.drawText(QuranMetadata.toArabicDigits(result.page.number), 300f, pageNumY, ink)

        canvas.restore()
    }

    /**
     * Draws an authentic Madinah Mushaf Surah Header ornament:
     * - Antique gold double-line outer border with stepped corner notches
     * - Warm parchment gold banner background
     * - Central cartouche with cusped arches enclosing the Surah title
     * - Symmetrical arabesque scrollwork and 8-pointed Islamic stars on the side panels
     */
    private fun drawMadaniSurahHeader(
        canvas: Canvas,
        start: ChapterStart,
        baselineY: Float,
        lineSpacing: Float,
        result: LoadedPage
    ) {
        val boxHeight = (lineSpacing * 0.90f).coerceIn(44f, 54f)
        val top = baselineY - boxHeight * 0.73f
        val bottom = baselineY + boxHeight * 0.27f
        val left = 14f
        val right = 586f
        val midY = (top + bottom) / 2f

        // 1. Outer decorative banner background
        framePaint.style = Paint.Style.FILL
        framePaint.color = Color.rgb(247, 240, 222)
        canvas.drawRect(left, top, right, bottom, framePaint)

        // 2. Outer antique gold border
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 2.2f
        framePaint.color = Color.rgb(148, 110, 36)
        canvas.drawRect(left, top, right, bottom, framePaint)

        // 3. Inner fine gold border
        framePaint.strokeWidth = 1.2f
        framePaint.color = Color.rgb(198, 158, 58)
        canvas.drawRect(left + 3.5f, top + 3.5f, right - 3.5f, bottom - 3.5f, framePaint)

        // 4. Stepped corner ornaments (classic Madinah notched corners)
        val cornerSize = 7f
        framePaint.style = Paint.Style.FILL
        framePaint.color = Color.rgb(148, 110, 36)
        canvas.drawRect(left, top, left + cornerSize, top + cornerSize, framePaint)
        canvas.drawRect(right - cornerSize, top, right, top + cornerSize, framePaint)
        canvas.drawRect(left, bottom - cornerSize, left + cornerSize, bottom, framePaint)
        canvas.drawRect(right - cornerSize, bottom - cornerSize, right, bottom, framePaint)

        framePaint.color = Color.rgb(255, 248, 228)
        val dotRadius = 1.6f
        canvas.drawCircle(left + cornerSize / 2f, top + cornerSize / 2f, dotRadius, framePaint)
        canvas.drawCircle(right - cornerSize / 2f, top + cornerSize / 2f, dotRadius, framePaint)
        canvas.drawCircle(left + cornerSize / 2f, bottom - cornerSize / 2f, dotRadius, framePaint)
        canvas.drawCircle(right - cornerSize / 2f, bottom - cornerSize / 2f, dotRadius, framePaint)

        // 5. Central Cartouche for Surah Title
        val cartoucheW = 236f
        val cStart = 300f - cartoucheW / 2f
        val cEnd = 300f + cartoucheW / 2f
        val cTop = top + 2.5f
        val cBottom = bottom - 2.5f

        ornamentPath.reset()
        // Scalloped Islamic cartouche with arched sides and top/bottom crown peaks
        ornamentPath.moveTo(cStart + 16f, cTop)
        ornamentPath.lineTo(290f, cTop)
        ornamentPath.quadTo(300f, cTop - 3.5f, 310f, cTop)
        ornamentPath.lineTo(cEnd - 16f, cTop)
        ornamentPath.quadTo(cEnd + 6f, midY, cEnd - 16f, cBottom)
        ornamentPath.lineTo(310f, cBottom)
        ornamentPath.quadTo(300f, cBottom + 3.5f, 290f, cBottom)
        ornamentPath.lineTo(cStart + 16f, cBottom)
        ornamentPath.quadTo(cStart - 6f, midY, cStart + 16f, cTop)
        ornamentPath.close()

        // Cartouche ivory fill
        framePaint.style = Paint.Style.FILL
        framePaint.color = Color.rgb(255, 253, 247)
        canvas.drawPath(ornamentPath, framePaint)

        // Cartouche gold border
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 1.6f
        framePaint.color = Color.rgb(156, 118, 40)
        canvas.drawPath(ornamentPath, framePaint)

        // 6. Side Wings Arabesque Ornaments & 8-pointed Rosettes
        drawSideOrnament(canvas, left + cornerSize + 6f, cStart - 6f, midY, top + 5f, bottom - 5f)
        drawSideOrnament(canvas, cEnd + 6f, right - cornerSize - 6f, midY, top + 5f, bottom - 5f)

        // 7. Surah Title Glyphs
        ink.typeface = result.titleFont
        ink.textSize = 34f
        ink.color = Color.rgb(28, 35, 28)
        val chapterGlyph = (0xE000 + start.chapter.toString().toInt(16)).toChar().toString()
        val titleGlyphs = listOf("\uE000", chapterGlyph)
        val totalW = titleGlyphs.sumOf { ink.measureText(it).toDouble() }.toFloat() + 8f
        var titleRight = 300f + totalW / 2f
        titleGlyphs.forEach { glyph ->
            titleRight -= ink.measureText(glyph)
            canvas.drawText(glyph, titleRight, baselineY, ink)
            titleRight -= 8f
        }
    }

    private fun drawSideOrnament(canvas: Canvas, x1: Float, x2: Float, midY: Float, top: Float, bottom: Float) {
        val cx = (x1 + x2) / 2f
        framePaint.style = Paint.Style.STROKE
        framePaint.strokeWidth = 1f
        framePaint.color = Color.rgb(186, 148, 64)

        // Horizontal connecting flourishes
        canvas.drawLine(x1 + 4f, midY, cx - 13f, midY, framePaint)
        canvas.drawLine(cx + 13f, midY, x2 - 4f, midY, framePaint)

        // Curving arabesque scrollwork
        ornamentPath.reset()
        ornamentPath.moveTo(x1 + 8f, midY)
        ornamentPath.quadTo((x1 + cx) / 2f, top + 3f, cx - 8f, midY - 3f)
        canvas.drawPath(ornamentPath, framePaint)

        ornamentPath.reset()
        ornamentPath.moveTo(x1 + 8f, midY)
        ornamentPath.quadTo((x1 + cx) / 2f, bottom - 3f, cx - 8f, midY + 3f)
        canvas.drawPath(ornamentPath, framePaint)

        ornamentPath.reset()
        ornamentPath.moveTo(x2 - 8f, midY)
        ornamentPath.quadTo((x2 + cx) / 2f, top + 3f, cx + 8f, midY - 3f)
        canvas.drawPath(ornamentPath, framePaint)

        ornamentPath.reset()
        ornamentPath.moveTo(x2 - 8f, midY)
        ornamentPath.quadTo((x2 + cx) / 2f, bottom - 3f, cx + 8f, midY + 3f)
        canvas.drawPath(ornamentPath, framePaint)

        // Center 8-pointed star rosette (نجمة إسلامية ثمانية)
        val r = 7f
        framePaint.style = Paint.Style.FILL
        framePaint.color = Color.rgb(150, 112, 38)
        ornamentPath.reset()
        ornamentPath.moveTo(cx, midY - r)
        ornamentPath.lineTo(cx + r, midY)
        ornamentPath.lineTo(cx, midY + r)
        ornamentPath.lineTo(cx - r, midY)
        ornamentPath.close()
        canvas.drawPath(ornamentPath, framePaint)

        val rD = r * 0.707f
        ornamentPath.reset()
        ornamentPath.moveTo(cx - rD, midY - rD)
        ornamentPath.lineTo(cx + rD, midY - rD)
        ornamentPath.lineTo(cx + rD, midY + rD)
        ornamentPath.lineTo(cx - rD, midY + rD)
        ornamentPath.close()
        canvas.drawPath(ornamentPath, framePaint)

        // Center gold dot
        framePaint.color = Color.rgb(255, 248, 220)
        canvas.drawCircle(cx, midY, 2f, framePaint)
    }
}
