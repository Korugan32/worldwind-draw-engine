package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

// jvmTest for the same reason as ImagePatternMapperTest: the tile becomes a WWK image, which needs platform code (the JVM one here).
class HatchMapperTest {
    private val red = ColorRgba(255, 0, 0)

    @Test
    fun aPolygonInteriorGetsTheGeneratedTileAndAWhiteColourSoItsPixelsAreNotTinted() {
        val attributes = ShapeStyle.Hatch(HatchPattern.Cross, red).toWwkShapeAttributes()

        assertNotNull(attributes.interiorImageSource)
        assertEquals(1f, attributes.interiorColor.red)
        assertEquals(1f, attributes.interiorColor.green)
        assertEquals(1f, attributes.interiorColor.blue)
        assertEquals(1f, attributes.interiorColor.alpha)
    }

    @Test
    fun theSameLookSharesOneImageSoWwkKeepsOneTextureForAllShapesWithIt() {
        val first = ShapeStyle.Hatch(HatchPattern.DiagonalUp, red, spacing = 10).toWwkShapeAttributes().interiorImageSource
        val again = ShapeStyle.Hatch(HatchPattern.DiagonalUp, red, spacing = 10).toWwkShapeAttributes().interiorImageSource
        val other = ShapeStyle.Hatch(HatchPattern.DiagonalUp, red, spacing = 11).toWwkShapeAttributes().interiorImageSource

        assertSame(first, again)
        assertNotSame(first, other)
    }

    @Test
    fun everyPartOfTheLookIsPartOfWhatMakesTheTile() {
        val base = ShapeStyle.Hatch(HatchPattern.Dots, red).toWwkShapeAttributes().interiorImageSource
        val looks = listOf(
            ShapeStyle.Hatch(HatchPattern.Horizontal, red),
            ShapeStyle.Hatch(HatchPattern.Dots, ColorRgba(0, 0, 255)),
            ShapeStyle.Hatch(HatchPattern.Dots, red, backgroundColor = ColorRgba(0, 255, 0, 100)),
            ShapeStyle.Hatch(HatchPattern.Dots, red, thickness = 3f),
        )
        for (look in looks) assertNotSame(base, look.toWwkShapeAttributes().interiorImageSource, "$look")
    }

    @Test
    fun aLineIsDrawnInTheHatchingsColourAsThereIsNoInteriorToHatch() {
        val attributes = ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(255, 0, 0, 128)).toWwkLineAttributes(width = 5f)

        assertEquals(5f, attributes.outlineWidth)
        assertEquals(1f, attributes.outlineColor.red)
        assertEquals(0f, attributes.outlineColor.green)
        assertEquals(128 / 255f, attributes.outlineColor.alpha, 0.01f)
        assertNull(attributes.outlineImageSource)
        assertNull(attributes.interiorImageSource)
    }
}
