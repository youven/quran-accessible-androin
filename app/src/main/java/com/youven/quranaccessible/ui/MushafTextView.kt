package com.youven.quranaccessible.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.youven.quranaccessible.data.LoadedPage

/** QCF words are font ligatures, drawn individually RTL without Unicode reshaping. */
class MushafTextView(context: Context) : View(context) {
    private val viewport = PageViewport()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private var loaded: LoadedPage? = null
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewport.zoom(detector.scaleFactor, detector.focusX, detector.focusY); invalidate(); return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!pinch.isInProgress) { viewport.pan(-distanceX, -distanceY); invalidate() }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (viewport.scale > viewport.minimum * 1.1f) viewport.reset() else viewport.zoom(2f, e.x, e.y)
            invalidate(); return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean { performClick(); return true }
    })
    init {
        // The adjacent accessible reading mode exposes actual Unicode, never QCF codes.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    fun bind(page: LoadedPage) { if (loaded !== page) { loaded = page; viewport.reset(); invalidate() } }
    fun zoomBy(factor: Float) { viewport.zoom(factor, width / 2f, height / 2f); invalidate() }
    fun resetZoom() { viewport.reset(); invalidate() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { viewport.resize(w.toFloat(), h.toFloat()) }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event); gestures.onTouchEvent(event)
        parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
        return true
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(247, 244, 232))
        val result = loaded ?: return
        canvas.save()
        canvas.translate(viewport.x, viewport.y)
        canvas.scale(viewport.scale, viewport.scale)
        ink.color = Color.rgb(255, 253, 244); ink.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, 600f, 900f, ink)
        ink.color = Color.rgb(32, 39, 32)
        ink.typeface = result.font; ink.textSize = 32f
        val widths = result.page.lines.mapValues { (_, words) -> words.sumOf { ink.measureText(it.glyph).toDouble() }.toFloat() }
        // A single size per page preserves relative word widths and centered short lines.
        val maxW = widths.values.maxOrNull() ?: 520f
        val fontSize = 32f * minOf(1f, 520f / maxW)
        fun baseline(line: Int) = if (result.page.number <= 2) 200f + line * 52f else 36f + line * 52f
        result.page.lines.forEach { (line, words) ->
            ink.typeface = result.font; ink.textSize = fontSize; ink.textAlign = Paint.Align.LEFT
            val lineWidth = words.sumOf { ink.measureText(it.glyph).toDouble() }.toFloat()
            var right = (600f + lineWidth) / 2f
            words.forEach { word ->
                right -= ink.measureText(word.glyph)
                canvas.drawText(word.glyph, right, baseline(line), ink)
            }
        }
        result.page.starts.forEach { start ->
            val headerBaseline = baseline(start.headerLine)
            ink.typeface = result.titleFont; ink.textSize = 34f; ink.textAlign = Paint.Align.CENTER
            // The source font maps chapter numbers in BCD to U+E001 … U+E114.
            val chapterGlyph = (0xE000 + start.chapter.toString().toInt(16)).toChar().toString()
            val titleGlyphs = listOf("\uE000", chapterGlyph)
            ink.textAlign = Paint.Align.LEFT
            var titleRight = (600f + titleGlyphs.sumOf { ink.measureText(it).toDouble() }.toFloat() + 8f) / 2
            titleGlyphs.forEach { glyph ->
                titleRight -= ink.measureText(glyph)
                canvas.drawText(glyph, titleRight, headerBaseline, ink)
                titleRight -= 8f
            }
            start.basmalaLine?.let { line ->
                ink.typeface = result.basmalaFont; ink.textSize = 32f; ink.textAlign = Paint.Align.LEFT
                val glyphs = listOf("\uFC41", "\uFC42", "\uFC43", "\uFC44")
                var right = (600f + glyphs.sumOf { ink.measureText(it).toDouble() }.toFloat()) / 2
                glyphs.forEach { glyph -> right -= ink.measureText(glyph); canvas.drawText(glyph, right, baseline(line), ink) }
            }
        }
        ink.typeface = Typeface.DEFAULT; ink.textSize = 18f; ink.textAlign = Paint.Align.CENTER
        canvas.drawText(result.page.number.toString(), 300f, 878f, ink)
        canvas.restore()
    }
}
