package com.youven.quranaccessible.ui

import kotlin.math.max
import kotlin.math.min

/** All offsets are screen pixels; document coordinates remain unchanged by zoom. */
class PageViewport {
    var width = 0f; private set
    var height = 0f; private set
    var scale = 1f; private set
    var minimum = 1f; private set
    var x = 0f; private set
    var y = 0f; private set
    fun resize(w: Float, h: Float) {
        width = w; height = h
        minimum = if (w > h) w / 600f else min(w / 600f, h / 900f)
        reset()
    }
    fun reset() { scale = minimum; x = (width - 600 * scale) / 2; y = 0f; clamp() }
    fun zoom(factor: Float, focusX: Float, focusY: Float) {
        val next = (scale * factor).coerceIn(minimum, minimum * 5)
        val ratio = next / scale
        x = focusX - (focusX - x) * ratio
        y = focusY - (focusY - y) * ratio
        scale = next
        clamp()
    }
    fun pan(dx: Float, dy: Float) { x += dx; y += dy; clamp() }
    private fun clamp() {
        val overflowX = max(0f, 600 * scale - width)
        val overflowY = max(0f, 900 * scale - height)
        x = if (overflowX == 0f) (width - 600 * scale) / 2 else x.coerceIn(-overflowX, 0f)
        y = if (overflowY == 0f) (height - 900 * scale) / 2 else y.coerceIn(-overflowY, 0f)
    }
}
