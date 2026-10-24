package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonCenter
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageFillRasterizerTest {
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val yellow = 0xFFFFFF00.toInt()

    private fun solid(width: Int, height: Int, argb: Int) = PixelImage(width, height, IntArray(width * height) { argb })

    /** 2x2: red top-left, green top-right, blue bottom-left, yellow bottom-right. */
    private fun quadrants() = PixelImage(2, 2, intArrayOf(red, green, blue, yellow))

    private fun square(lat0: Double, lon0: Double, side: Double) = listOf(
        GeoPosition(lat0, lon0), GeoPosition(lat0, lon0 + side), GeoPosition(lat0 + side, lon0 + side), GeoPosition(lat0 + side, lon0),
    )

    private fun render(
        source: PixelImage,
        rings: List<List<GeoPosition>>,
        fit: ImageFit = ImageFit.Stretch,
        scale: Float = 1f,
        opacity: Float = 1f,
        maxDimension: Int = 100,
        background: ColorRgba? = null,
    ) = ImageFillRasterizer.render(source, rings, fit, scale, opacity, maxDimension, background)

    private fun RasterizedFill.pixel(x: Int, y: Int) = image.argb[y * image.width + x]
    private fun Int.alpha() = this ushr 24

    @Test
    fun aSquareAtTheEquatorGetsASquareGridWithTheGeographicBoxOfTheShape() {
        val result = assertNotNull(render(solid(4, 4, red), listOf(square(0.0, 0.0, 2.0))))

        assertEquals(100, result.image.width)
        assertEquals(100, result.image.height)
        assertEquals(0.0, result.minLatitude); assertEquals(2.0, result.maxLatitude)
        assertEquals(0.0, result.minLongitude); assertEquals(2.0, result.maxLongitude)
        assertEquals(red, result.pixel(50, 50))
        assertEquals(red, result.pixel(1, 1), "a shape that fills its box leaves no gaps")
        assertEquals(red, result.pixel(98, 98))
    }

    @Test
    fun theGridIsProportionedLikeTheGroundNotLikeTheDegrees() {
        // At 60 degrees north a degree of longitude is half as long as a degree of latitude, so a
        // box 4 degrees wide and 2 tall is square on the ground. Without this the picture would be
        // stretched sideways when laid back over the box.
        val result = assertNotNull(render(solid(4, 4, red), listOf(square(59.0, 0.0, 2.0).map { GeoPosition(it.latitude, it.longitude * 2) })))

        assertEquals(result.image.width, result.image.height)
    }

    @Test
    fun theTopLeftOfThePictureLandsInTheNorthWestOfTheShape() {
        val result = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0))))

        assertEquals(red, result.pixel(10, 10), "north-west")
        assertEquals(green, result.pixel(90, 10), "north-east")
        assertEquals(blue, result.pixel(10, 90), "south-west")
        assertEquals(yellow, result.pixel(90, 90), "south-east")
    }

    @Test
    fun theFillIsCutToTheShapeAndOutsideIsTransparent() {
        // A triangle: wide base in the south, apex in the north.
        val triangle = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 2.0), GeoPosition(2.0, 1.0))
        val result = assertNotNull(render(solid(4, 4, red), listOf(triangle)))

        assertEquals(255, result.pixel(50, 90).alpha(), "inside, near the base")
        assertEquals(0, result.pixel(5, 5).alpha(), "outside: north-west corner")
        assertEquals(0, result.pixel(94, 5).alpha(), "outside: north-east corner")
    }

    @Test
    fun theEdgeOfTheShapeIsAntiAliased() {
        val triangle = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 2.0), GeoPosition(2.0, 1.0))
        val result = assertNotNull(render(solid(4, 4, red), listOf(triangle)))

        val partial = (0 until 100).any { x -> (1 until 99).any { y -> result.pixel(x, y).alpha() in 20..235 } }
        assertTrue(partial, "some edge pixels are only partly covered")
    }

    @Test
    fun aConcaveNotchIsLeftOut() {
        // A square with a triangular bite out of its south edge.
        val ring = listOf(GeoPosition(0.0, 0.0), GeoPosition(2.0, 0.0), GeoPosition(2.0, 2.0), GeoPosition(0.0, 2.0), GeoPosition(1.0, 1.0))
        val result = assertNotNull(render(solid(4, 4, red), listOf(ring)))

        assertEquals(0, result.pixel(50, 95).alpha(), "inside the bite, at the south edge")
        assertEquals(255, result.pixel(50, 30).alpha(), "well inside the shape")
    }

    @Test
    fun aRingInsideAnotherIsAHole() {
        val outer = square(0.0, 0.0, 4.0)
        val hole = square(1.0, 1.0, 2.0)
        val result = assertNotNull(render(solid(4, 4, red), listOf(outer, hole)))

        assertEquals(0, result.pixel(50, 50).alpha(), "centre is the hole")
        assertEquals(255, result.pixel(10, 10).alpha(), "between the rings it is filled")
    }

    @Test
    fun twoSeparateRingsAreBothFilled() {
        val result = assertNotNull(render(solid(4, 4, red), listOf(square(0.0, 0.0, 1.0), square(3.0, 3.0, 1.0))))

        assertEquals(255, result.pixel(10, 90).alpha(), "south-west part")
        assertEquals(255, result.pixel(90, 10).alpha(), "north-east part")
        assertEquals(0, result.pixel(10, 10).alpha(), "the gap between them")
    }

    @Test
    fun containKeepsTheWholePictureAndLeavesTheRestEmpty() {
        // A 2:1 picture into a square box: full width, half the height, centred.
        val result = assertNotNull(render(solid(40, 20, red), listOf(square(0.0, 0.0, 2.0)), fit = ImageFit.Contain))

        assertEquals(0, result.pixel(50, 10).alpha(), "above the picture")
        assertEquals(0, result.pixel(50, 90).alpha(), "below the picture")
        assertEquals(255, result.pixel(50, 50).alpha(), "the picture, centred")
        assertEquals(255, result.pixel(3, 50).alpha(), "it spans the full width")
    }

    @Test
    fun coverFillsTheWholeBoxAndCutsWhatOverflows() {
        // The same 2:1 picture covering a square box: full height, sides cut off.
        val picture = PixelImage(2, 1, intArrayOf(red, blue)) // left half red, right half blue
        val result = assertNotNull(render(picture, listOf(square(0.0, 0.0, 2.0)), fit = ImageFit.Cover))

        assertEquals(255, result.pixel(50, 5).alpha())
        assertEquals(255, result.pixel(50, 95).alpha())
        // Only the middle half of the picture is visible, so the red/blue split stays at the centre.
        // (The picture is smoothed when enlarged, so near the split a pixel is a mix: compare which colour dominates.)
        fun Int.redPart() = (this shr 16) and 0xFF
        fun Int.bluePart() = this and 0xFF
        assertTrue(result.pixel(20, 50).redPart() > result.pixel(20, 50).bluePart())
        assertTrue(result.pixel(80, 50).bluePart() > result.pixel(80, 50).redPart())
    }

    @Test
    fun scaleShrinksThePictureAboutTheCentre() {
        val result = assertNotNull(render(solid(4, 4, red), listOf(square(0.0, 0.0, 2.0)), fit = ImageFit.Stretch, scale = 0.5f))

        assertEquals(0, result.pixel(10, 10).alpha(), "outside the shrunken picture")
        assertEquals(255, result.pixel(50, 50).alpha(), "its centre is still the centre")
        assertEquals(255, result.pixel(30, 30).alpha(), "just inside the shrunken picture")
    }

    @Test
    fun opacityFadesTheWholeFill() {
        val result = assertNotNull(render(solid(4, 4, red), listOf(square(0.0, 0.0, 2.0)), opacity = 0.5f))

        assertTrue(abs(result.pixel(50, 50).alpha() - 128) <= 1)
    }

    @Test
    fun aPicturesOwnTransparencyIsKept() {
        val halfTransparent = solid(4, 4, 0x80FF0000.toInt())
        val result = assertNotNull(render(halfTransparent, listOf(square(0.0, 0.0, 2.0)), opacity = 0.5f))

        assertTrue(abs(result.pixel(50, 50).alpha() - 64) <= 1, "0.5 of an alpha of 0.5")
    }

    @Test
    fun nothingIsDrawnForAShapeWithNoAreaOrTooWideForOneBox() {
        assertNull(render(solid(4, 4, red), listOf(listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 1.0)))), "fewer than 3 points")
        assertNull(render(solid(4, 4, red), listOf(listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 1.0), GeoPosition(0.0, 2.0)))), "no height")
        assertNull(render(solid(4, 4, red), listOf(square(0.0, -100.0, 1.0).map { GeoPosition(it.latitude, if (it.longitude > -99.5) 100.0 else -100.0) })), "spans over 180 degrees of longitude")
    }

    // -- ImageFit.Inscribed

    /** A "C": a bar 3 wide on the west and two arms 3 high reaching east, with a notch between them: its bounding box centre is in the notch, outside the shape. */
    private fun horseshoe() = listOf(
        GeoPosition(0.0, 0.0), GeoPosition(10.0, 0.0), GeoPosition(10.0, 10.0), GeoPosition(7.0, 10.0),
        GeoPosition(7.0, 3.0), GeoPosition(3.0, 3.0), GeoPosition(3.0, 10.0), GeoPosition(0.0, 10.0),
    )

    /** 20x10: the left half red, the right half blue. */
    private fun redAndBlue() = PixelImage(20, 10, IntArray(200) { if (it % 20 < 10) red else blue })

    private fun RasterizedFill.opaque() = (0 until image.height).flatMap { y -> (0 until image.width).filter { x -> pixel(x, y).alpha() > 0 }.map { x -> x to y } }

    @Test
    fun inscribedPutsTheWholePictureAtTheVisualCentreInsideTheBiggestCircleAndNotInTheBoxCentre() {
        val result = assertNotNull(render(redAndBlue(), listOf(horseshoe()), ImageFit.Inscribed, maxDimension = 300))
        val opaque = result.opaque()
        assertTrue(opaque.isNotEmpty())

        // Where the biggest inner circle is, in pixels of this result.
        val center = PolygonCenter.visualCenter(horseshoe())
        val cx = (center.position.longitude - result.minLongitude) / (result.maxLongitude - result.minLongitude) * result.image.width
        val cy = (result.maxLatitude - center.position.latitude) / (result.maxLatitude - result.minLatitude) * result.image.height
        val radius = center.radiusMeters / 111_194.9 * result.image.height / (result.maxLatitude - result.minLatitude)

        val meanX = opaque.map { it.first }.average()
        val meanY = opaque.map { it.second }.average()
        assertTrue(abs(meanX - cx) < 4 && abs(meanY - cy) < 4, "the picture is centred on ($cx, $cy), its middle is ($meanX, $meanY)")
        assertTrue(opaque.all { (x, y) -> kotlin.math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= radius * 1.05 + 1 }, "nothing sticks out of the circle of radius $radius")

        // The whole picture, 2 wide for 1 tall, half red and half blue: it was not cut.
        val width = opaque.maxOf { it.first } - opaque.minOf { it.first } + 1
        val height = opaque.maxOf { it.second } - opaque.minOf { it.second } + 1
        assertTrue(abs(width.toDouble() / height - 2.0) < 0.2, "2:1, got $width x $height")
        val reds = opaque.count { (x, y) -> result.pixel(x, y) == red }
        val blues = opaque.count { (x, y) -> result.pixel(x, y) == blue }
        assertTrue(reds > 0 && abs(reds - blues) < 0.2 * (reds + blues), "half and half: $reds red, $blues blue")

        assertEquals(0, result.pixel(result.image.width * 3 / 4, result.image.height / 2).alpha(), "the notch (the middle of the box) stays empty")
    }

    @Test
    fun inscribedPicturesFollowTheScaleAndAreCutToTheShapeWhenTheyGrowPastIt() {
        val whole = assertNotNull(render(redAndBlue(), listOf(horseshoe()), ImageFit.Inscribed, scale = 1f, maxDimension = 300)).opaque().size
        val half = assertNotNull(render(redAndBlue(), listOf(horseshoe()), ImageFit.Inscribed, scale = 0.5f, maxDimension = 300)).opaque().size
        val big = assertNotNull(render(redAndBlue(), listOf(horseshoe()), ImageFit.Inscribed, scale = 3f, maxDimension = 300))

        assertTrue(abs(half.toDouble() / whole - 0.25) < 0.05, "half the size is a quarter of the area: $half of $whole")
        assertTrue(big.opaque().size > whole, "a bigger picture covers more of the shape")
        assertEquals(0, big.pixel(big.image.width * 3 / 4, big.image.height / 2).alpha(), "but never the notch, outside the shape")
    }

    @Test
    fun inscribedAlsoWorksForAnOrdinaryBlobAndAppliesTheOpacityLikeTheOtherModes() {
        val result = assertNotNull(render(solid(4, 4, red), listOf(square(0.0, 0.0, 10.0)), ImageFit.Inscribed, opacity = 0.5f, maxDimension = 100))
        val centre = result.pixel(50, 50)
        assertEquals(0x80, centre.alpha(), "half opaque, in the middle of the square")
        assertEquals(0, result.pixel(2, 2).alpha(), "a square picture in a circle in a square: the corners of the square are empty")
    }

    // -- background colour

    @Test
    fun theBackgroundFillsWhatAContainedPictureLeavesEmptyAndNothingOutsideTheShape() {
        // A 4:2 picture contained in a square box: bands of the box above and below it are empty.
        val picture = solid(40, 20, red)
        val greenBackground = ColorRgba(0, 255, 0)
        val triangle = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 2.0), GeoPosition(2.0, 1.0))
        val squareShape = square(0.0, 0.0, 2.0)

        val inSquare = assertNotNull(render(picture, listOf(squareShape), ImageFit.Contain, background = greenBackground))
        assertEquals(red, inSquare.pixel(50, 50), "the picture wins where it is")
        assertEquals(green, inSquare.pixel(50, 10), "the empty band above it is the background")
        assertEquals(green, inSquare.pixel(50, 90), "and below it")

        val inTriangle = assertNotNull(render(picture, listOf(triangle), ImageFit.Contain, background = greenBackground))
        assertEquals(0, inTriangle.pixel(5, 10).alpha(), "outside the shape there is nothing, not even the background")
    }

    @Test
    fun theBackgroundOwnAlphaIsItsOpacityAndThePictureOpacityIsOnlyForThePicture() {
        val picture = solid(40, 20, red)
        val result = assertNotNull(
            render(picture, listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, opacity = 0.5f, background = ColorRgba(0, 255, 0, 128))
        )
        assertEquals(0x80, result.pixel(50, 10).alpha(), "background only: its own alpha")
        assertEquals(0xFF00FF00.toInt() and 0x00FFFFFF, result.pixel(50, 10) and 0x00FFFFFF, "and pure green")
        // Half transparent red over half transparent green: more opaque than either, a mix of both.
        val mixed = result.pixel(50, 50)
        assertTrue(mixed.alpha() in 0xBB..0xC4, "0.5 + 0.5 * 0.5 = 0.75 opaque, got ${mixed.alpha()}")
        assertTrue(((mixed shr 16) and 0xFF) > ((mixed shr 8) and 0xFF), "more red than green: the picture is on top")
        assertTrue(((mixed shr 8) and 0xFF) > 0, "but the background still shows through")
    }

    @Test
    fun inscribedWithABackgroundFillsTheRestOfTheShapeAroundTheWholePicture() {
        val result = assertNotNull(
            render(redAndBlue(), listOf(horseshoe()), ImageFit.Inscribed, maxDimension = 300, background = ColorRgba(0, 255, 0))
        )
        val inside = result.opaque()
        // Everywhere in the shape is now covered: the picture in the middle, green around it; the notch stays empty.
        assertTrue(inside.count { (x, y) -> result.pixel(x, y) == green } > inside.size / 3, "a good part of the shape is background")
        assertTrue(inside.any { (x, y) -> result.pixel(x, y) == red } && inside.any { (x, y) -> result.pixel(x, y) == blue })
        assertEquals(0, result.pixel(result.image.width * 3 / 4, result.image.height / 2).alpha(), "the notch is outside the shape")
    }

    @Test
    fun withoutABackgroundTheResultIsExactlyWhatItWasBefore() {
        val picture = quadrants()
        val plain = assertNotNull(render(picture, listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, opacity = 0.7f))
        val explicitNull = assertNotNull(render(picture, listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, opacity = 0.7f, background = null))
        assertTrue(plain.image.argb.contentEquals(explicitNull.image.argb))
    }
}
