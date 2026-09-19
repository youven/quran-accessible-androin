package com.youven.quranaccessible.ui

import org.junit.Assert.*
import org.junit.Test

class PageViewportTest {
    @Test fun landscapeFitsWidthAndAllowsReachingBottom() {
        val v = PageViewport(); v.resize(1000f, 400f)
        assertEquals(1000f, 600 * v.scale, 0.01f)
        v.pan(0f, -10000f)
        assertEquals(400f, v.y + 900 * v.scale, 0.01f)
    }
    @Test fun zoomKeepsWordUnderTheFinger() {
        val v = PageViewport(); v.resize(600f, 900f)
        val wordX = (300 - v.x) / v.scale; val wordY = (400 - v.y) / v.scale
        v.zoom(2f, 300f, 400f)
        assertEquals(300f, wordX * v.scale + v.x, 0.01f)
        assertEquals(400f, wordY * v.scale + v.y, 0.01f)
    }
    @Test fun zoomBoundsAndResetNeverLeavePageOffscreen() {
        val v = PageViewport(); v.resize(600f, 900f)
        v.zoom(100f, 300f, 450f); assertEquals(5f, v.scale, 0.01f)
        v.pan(10000f, 10000f); assertEquals(0f, v.x, 0.01f)
        v.zoom(0.001f, 300f, 450f); assertEquals(1f, v.scale, 0.01f)
        v.reset(); assertEquals(0f, v.y, 0.01f)
    }
}
