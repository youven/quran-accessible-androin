package com.youven.quranaccessible.ui

import kotlin.math.max

/**
 * Viewport managing pan, zoom, and full-width scaling for the Mushaf canvas.
 * The virtual coordinate system has width 600f and dynamic pageHeight matching device aspect ratio.
 */
class PageViewport {
    var width = 0f; private set
    var height = 0f; private set
    var scale = 1f; private set
    var minimum = 1f; private set
    var x = 0f; private set
    var y = 0f; private set
    var pageHeight = 900f; private set

    fun resize(w: Float, h: Float) {
        width = w
        height = h
        if (w > 0f && h > 0f) {
            // Scale to fill full width of the screen with 0 horizontal letterboxing
            scale = w / 600f
            minimum = scale
            // Dynamic virtual page height matching screen aspect ratio
            pageHeight = (h / scale).coerceAtLeast(880f)
            x = 0f
            y = 0f
        }
        reset()
    }

    fun reset() {
        scale = minimum
        x = 0f
        y = 0f
        clamp()
    }

    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        val next = (scale * factor).coerceIn(minimum, minimum * 4f)
        val ratio = next / scale
        x = focusX - (focusX - x) * ratio
        y = focusY - (focusY - y) * ratio
        scale = next
        clamp()
    }

    fun pan(dx: Float, dy: Float) {
        x += dx
        y += dy
        clamp()
    }

    private fun clamp() {
        val overflowX = max(0f, 600f * scale - width)
        val overflowY = max(0f, pageHeight * scale - height)
        x = if (overflowX == 0f) 0f else x.coerceIn(-overflowX, 0f)
        y = if (overflowY == 0f) 0f else y.coerceIn(-overflowY, 0f)
    }
}
