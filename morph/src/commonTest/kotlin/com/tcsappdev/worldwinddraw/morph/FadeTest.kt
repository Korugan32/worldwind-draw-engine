package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FadeTest {
    @Test
    fun aColourKeepsItsHueAndScalesItsAlpha() {
        val faded = triangle("t", 1.0, style = solid(10, 20, 30, 200)).faded(0.5f) as DrawnPolygon
        assertEquals(solid(10, 20, 30, 100), faded.style)
    }

    @Test
    fun aFactorOfOneChangesNothingAndAFactorOfZeroMakesItInvisible() {
        val shape = triangle("t", 1.0, style = solid(10, 20, 30, 200))
        assertEquals(shape, shape.faded(1f))
        assertEquals(solid(10, 20, 30, 0), shape.faded(0f).style)
    }

    @Test
    fun aFactorOutsideZeroToOneIsHeldInside() {
        val shape = triangle("t", 1.0, style = solid(10, 20, 30, 200))
        assertEquals(shape, shape.faded(3f))
        assertEquals(solid(10, 20, 30, 0), shape.faded(-1f).style)
    }

    @Test
    fun theOutlineFadesWithTheFill() {
        val shape = triangle("t", 1.0).copy(outlineStyle = solid(0, 0, 0, 100))
        assertEquals(solid(0, 0, 0, 50), (shape.faded(0.5f) as DrawnPolygon).outlineStyle)
        assertNull((triangle("t", 1.0).faded(0.5f) as DrawnPolygon).outlineStyle)
    }

    @Test
    fun picturesFadeTheirOpacityAndTheirBackground() {
        val pattern = ShapeStyle.ImagePattern(ImageRef.Url("x"), 0.8f)
        assertEquals(ShapeStyle.ImagePattern(ImageRef.Url("x"), 0.4f), triangle("t", 1.0, style = pattern).faded(0.5f).style)
        val fill = ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 0.5f, backgroundColor = ColorRgba(1, 2, 3, 100))
        assertEquals(fill.copy(opacity = 0.25f, backgroundColor = ColorRgba(1, 2, 3, 50)), triangle("t", 1.0, style = fill).faded(0.5f).style)
    }

    @Test
    fun aHatchFadesBothColours() {
        val hatch = ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(1, 2, 3, 100), ColorRgba(4, 5, 6, 200))
        assertEquals(hatch.copy(color = ColorRgba(1, 2, 3, 50), backgroundColor = ColorRgba(4, 5, 6, 100)), triangle("t", 1.0, style = hatch).faded(0.5f).style)
    }

    @Test
    fun aMarkerFadesItsLabelToo() {
        val shape = marker("m", 1.0, 1.0, solid(0, 0, 255, 100)).copy(label = MarkerLabel("x", ColorRgba(9, 9, 9, 200), outlineColor = ColorRgba(0, 0, 0, 100)))
        val faded = shape.faded(0.5f) as DrawnMarker
        assertEquals(solid(0, 0, 255, 50), faded.style)
        assertEquals(ColorRgba(9, 9, 9, 100), faded.label?.color)
        assertEquals(ColorRgba(0, 0, 0, 50), faded.label?.outlineColor)
    }

    @Test
    fun aLineFadesAndKeepsItsPlace() {
        val shape = line("l", 0.0, 0.0, 1.0, 1.0, style = solid(0, 255, 0, 80))
        val faded = shape.faded(0.25f)
        assertEquals(solid(0, 255, 0, 20), faded.style)
        assertEquals(shape.id, faded.id)
    }
}
