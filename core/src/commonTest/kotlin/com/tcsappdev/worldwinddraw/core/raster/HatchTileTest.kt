package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HatchTileTest {
    private val black = ColorRgba(0, 0, 0)
    private val spacing = 12

    private fun tile(
        pattern: HatchPattern,
        color: ColorRgba = black,
        background: ColorRgba? = null,
        thickness: Float = 2f,
    ) = HatchTile.render(pattern, color, background, spacing, thickness)

    private fun PixelImage.alpha(x: Int, y: Int) = argb[y * width + x] ushr 24

    private fun PixelImage.opaque(x: Int, y: Int) = alpha(x, y) == 255
    private fun PixelImage.empty(x: Int, y: Int) = alpha(x, y) == 0

    @Test
    fun theTileIsASquareOfTheSpacing() {
        val tile = tile(HatchPattern.Horizontal)
        assertEquals(spacing, tile.width)
        assertEquals(spacing, tile.height)
    }

    @Test
    fun horizontalLinesAreTwoRowsInTheMiddleOfTheTileAndTheSameInEveryColumn() {
        val tile = tile(HatchPattern.Horizontal)
        for (x in 0 until spacing) {
            assertTrue(tile.opaque(x, 5) && tile.opaque(x, 6), "the line, column $x")
            assertTrue(tile.empty(x, 3) && tile.empty(x, 8) && tile.empty(x, 0), "and nothing beside it, column $x")
        }
    }

    @Test
    fun verticalLinesAreTheSameTurnedAround() {
        val tile = tile(HatchPattern.Vertical)
        for (y in 0 until spacing) {
            assertTrue(tile.opaque(5, y) && tile.opaque(6, y), "the line, row $y")
            assertTrue(tile.empty(3, y) && tile.empty(8, y) && tile.empty(0, y), "and nothing beside it, row $y")
        }
    }

    @Test
    fun diagonalUpFollowsTheAntiDiagonalAndRunsThroughTheCornersSoNeighbouringTilesJoin() {
        val tile = tile(HatchPattern.DiagonalUp)
        assertTrue(tile.opaque(5, 6) && tile.opaque(8, 3) && tile.opaque(2, 9), "on the line from bottom left to top right")
        assertTrue(tile.empty(5, 0) && tile.empty(0, 5) && tile.empty(11, 11 - 5), "off it")
        // The line leaves through two corners: those pixels hold a part of it, which the next tile carries on.
        assertTrue(tile.alpha(0, 11) > 0 && tile.alpha(11, 0) > 0, "the opposite corners")
        assertTrue(tile.alpha(0, 0) > 0 && tile.alpha(11, 11) > 0, "and the other two, where the neighbouring tiles' lines pass")
    }

    @Test
    fun diagonalDownIsTheMirrorImage() {
        val up = tile(HatchPattern.DiagonalUp)
        val down = tile(HatchPattern.DiagonalDown)
        for (y in 0 until spacing) for (x in 0 until spacing) {
            assertEquals(up.argb[y * spacing + x], down.argb[y * spacing + (spacing - 1 - x)], "($x, $y) mirrored")
        }
        assertTrue(down.opaque(6, 6) && down.opaque(2, 2) && down.opaque(9, 9), "on the main diagonal")
    }

    @Test
    fun crossIsBothDiagonalsTogether() {
        val up = tile(HatchPattern.DiagonalUp)
        val down = tile(HatchPattern.DiagonalDown)
        val cross = tile(HatchPattern.Cross)
        for (y in 0 until spacing) for (x in 0 until spacing) {
            assertEquals(maxOf(up.alpha(x, y), down.alpha(x, y)), cross.alpha(x, y), "($x, $y)")
        }
    }

    @Test
    fun dotsAreOneRoundDotInTheMiddleOfTheTileWhoseSizeIsTheThickness() {
        val tile = tile(HatchPattern.Dots, thickness = 4f)
        assertTrue(tile.opaque(5, 5) && tile.opaque(6, 6) && tile.opaque(5, 6), "the middle of the dot")
        assertTrue(tile.alpha(4, 6) > 200, "and its anti-aliased edge")
        assertTrue(tile.empty(0, 0) && tile.empty(0, 6) && tile.empty(11, 11), "the gaps and the corners")
        val dotPixels = (0 until spacing * spacing).count { tile.argb[it] ushr 24 > 0 }
        assertTrue(dotPixels in 10..20, "a disc of diameter 4 is about 12.6 pixels of area, was $dotPixels")
    }

    @Test
    fun everyPatternIsSymmetricAboutTheCentreOfTheTileSoTilingLeavesNoSeam() {
        for (pattern in HatchPattern.entries) {
            val tile = tile(pattern)
            for (y in 0 until spacing) for (x in 0 until spacing) {
                assertEquals(tile.argb[y * spacing + x], tile.argb[(spacing - 1 - y) * spacing + (spacing - 1 - x)], "$pattern ($x, $y)")
            }
            // What leaves the left edge comes back on the right one: the first and the last column are as close to the pattern as
            // neighbours inside the tile (a line ends at the edge exactly as it would in the middle of a bigger sheet).
            val edgeCover = (0 until spacing).sumOf { tile.alpha(0, it) }
            val mirrorCover = (0 until spacing).sumOf { tile.alpha(spacing - 1, it) }
            assertEquals(edgeCover, mirrorCover, "$pattern: the left and right columns match")
        }
    }

    @Test
    fun theLineColourAndItsOwnAlphaAreUsedAndTheGapsAreEmptyWithoutABackground() {
        val tile = tile(HatchPattern.Horizontal, color = ColorRgba(255, 0, 0, 100))
        assertEquals(0x64FF0000, tile.argb[5 * spacing + 3])
        assertEquals(0, tile.argb[0])
    }

    @Test
    fun aBackgroundFillsTheGapsAndTheLineIsLaidOverIt() {
        val tile = tile(HatchPattern.Horizontal, color = ColorRgba(255, 0, 0), background = ColorRgba(0, 0, 255, 128))
        assertEquals(0xFFFF0000.toInt(), tile.argb[5 * spacing + 3], "an opaque line wins")
        assertEquals(0x800000FF.toInt(), tile.argb[0], "the gap is the background")
    }

    @Test
    fun theEdgeOfALineIsAntiAliasedOverOnePixel() {
        // A line 1.5 pixels thick centred between two rows: the two middle rows hold it fully, the next one a quarter...
        val tile = tile(HatchPattern.Horizontal, thickness = 3f)
        assertTrue(tile.opaque(0, 5) && tile.opaque(0, 6), "inside")
        assertTrue(tile.alpha(0, 4) in 100..160 && tile.alpha(0, 7) in 100..160, "the edge rows are about half covered, was ${tile.alpha(0, 4)}")
        assertTrue(tile.empty(0, 3), "beyond")
    }

    @Test
    fun aHatchStyleRejectsASpacingOrThicknessThatCouldNotBeDrawn() {
        assertFailsWith<IllegalArgumentException> { ShapeStyle.Hatch(HatchPattern.Cross, black, spacing = 3) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.Hatch(HatchPattern.Cross, black, spacing = 500) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.Hatch(HatchPattern.Cross, black, thickness = 0f) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.Hatch(HatchPattern.Cross, black, spacing = 8, thickness = 9f) }
        ShapeStyle.Hatch(HatchPattern.Cross, black, spacing = 8, thickness = 8f)
    }
}
