package com.tcsappdev.worldwinddraw.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HandleStyleTest {
    @Test
    fun defaultsKeepTheMidpointSmallerThanTheVertex() {
        val style = HandleStyle()

        assertTrue(style.midpointDiameter < style.vertexDiameter)
    }

    @Test
    fun acceptsCustomDiameters() {
        val style = HandleStyle(vertexDiameter = 40f, midpointDiameter = 30f)

        assertEquals(40f, style.vertexDiameter)
        assertEquals(30f, style.midpointDiameter)
    }

    @Test
    fun rejectsNonPositiveDiameters() {
        assertFailsWith<IllegalArgumentException> { HandleStyle(vertexDiameter = 0f) }
        assertFailsWith<IllegalArgumentException> { HandleStyle(midpointDiameter = -1f) }
    }

    @Test
    fun defaultColorsKeepTheRingAndPlusDarkOnAWhiteDisc() {
        val style = HandleStyle()

        assertEquals(ColorRgba(255, 255, 255), style.fillColor)
        assertTrue(style.ringColor.red < 64 && style.ringColor.green < 64 && style.ringColor.blue < 64)
        assertTrue(style.plusColor.red < 64 && style.plusColor.green < 64 && style.plusColor.blue < 64)
    }

    @Test
    fun acceptsCustomColors() {
        val style = HandleStyle(fillColor = ColorRgba(1, 2, 3), ringColor = ColorRgba(4, 5, 6), plusColor = ColorRgba(7, 8, 9))

        assertEquals(ColorRgba(4, 5, 6), style.ringColor)
        assertEquals(ColorRgba(7, 8, 9), style.plusColor)
    }

    @Test
    fun theGrabAreaIsBiggerThanTheDefaultDots() {
        val style = HandleStyle()

        assertTrue(style.touchTargetDiameter > style.vertexDiameter)
        assertEquals(style.touchTargetDiameter, style.touchSizeFor(style.midpointDiameter))
    }

    @Test
    fun aGrabAreaIsNeverSmallerThanTheHandleItself() {
        val style = HandleStyle(touchTargetDiameter = 20f)

        assertEquals(35f, style.touchSizeFor(35f))
        assertEquals(20f, style.touchSizeFor(10f))
    }

    @Test
    fun rejectsANonPositiveTouchTarget() {
        assertFailsWith<IllegalArgumentException> { HandleStyle(touchTargetDiameter = 0f) }
    }
}
