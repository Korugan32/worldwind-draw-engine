package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonCenter
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageFillRasterizerTest {
    private val WHOLE_BOX = ColorRgba(0, 0, 0, 0)
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
        // A fully transparent background draws nothing but keeps the result the shape's whole bounding box (without any background the
        // result shrinks to the picture's own rectangle), so a pixel's place is its place in the box. Pass null to get the shrunk result.
        background: ColorRgba? = WHOLE_BOX,
        repeat: Boolean = false,
        anchorX: Float? = null,
        anchorY: Float? = null,
        extendEdges: Boolean = false,
        edgeSoftness: Float = 0f,
    ) = ImageFillRasterizer.render(source, rings, fit, scale, opacity, maxDimension, background, repeat, anchorX, anchorY, extendEdges, edgeSoftness)

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
    fun theBackgroundOwnAlphaIsItsOpacityInsideTheFillAndTheOpacityFadesBothTogether() {
        val picture = solid(40, 20, red)
        val result = assertNotNull(
            render(picture, listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, opacity = 0.5f, background = ColorRgba(0, 255, 0, 128))
        )
        assertEquals(0x40, result.pixel(50, 10).alpha(), "background only: its own alpha 0.5, faded by the opacity 0.5")
        assertEquals(0xFF00FF00.toInt() and 0x00FFFFFF, result.pixel(50, 10) and 0x00FFFFFF, "and pure green")
        // An opaque red picture over the half transparent green: the picture wins, and the whole is faded by the opacity.
        val onTop = result.pixel(50, 50)
        assertEquals(0x80, onTop.alpha(), "the picture covers the background, then 0.5 of that")
        assertEquals(0xFF0000, onTop and 0xFFFFFF, "pure red: the background does not tint an opaque picture")
    }

    @Test
    fun aBackgroundOfThePictureColourLooksTheSameInsideAndAroundThePicture() {
        // The flag case: a red picture on a red background has no seam, however translucent the fill is.
        val result = assertNotNull(
            render(solid(40, 20, red), listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, opacity = 0.55f, background = ColorRgba(255, 0, 0))
        )
        assertEquals(result.pixel(50, 10), result.pixel(50, 50), "the same colour and the same alpha in the picture and in the band above it")
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

    // -- extending the edges

    @Test
    fun extendedEdgesCarryTheOutermostPixelsOutwardsSoStripesKeepRunning() {
        // Three vertical stripes in a wide picture, contained in a square: bands above and below it.
        val stripes = PixelImage(30, 10, IntArray(300) { if (it % 30 < 10) red else if (it % 30 < 20) green else blue })
        val plain = assertNotNull(render(stripes, listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain))
        assertEquals(0, plain.pixel(10, 10).alpha(), "without it the band above the picture is empty")

        val extended = assertNotNull(render(stripes, listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, extendEdges = true))
        for (y in listOf(10, 50, 90)) {
            assertEquals(red, extended.pixel(15, y), "the left stripe runs up and down past the picture (y $y)")
            assertEquals(green, extended.pixel(50, y), "the middle one")
            assertEquals(blue, extended.pixel(85, y), "the right one")
        }
    }

    @Test
    fun extendedEdgesAlsoFillTheSidesOfASmallOffCentrePicture() {
        // Left half red, right half blue, drawn at half size in the middle: the red edge goes left, the blue one right.
        val result = assertNotNull(render(redAndBlue(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f, extendEdges = true))
        assertEquals(red, result.pixel(5, 50), "left of the picture")
        assertEquals(red, result.pixel(5, 5), "and the corner")
        assertEquals(blue, result.pixel(95, 50), "right of the picture")
        assertEquals(blue, result.pixel(95, 95))
    }

    @Test
    fun extendedEdgesDoNotChangeWhatIsInsideThePictureAndTheBackgroundStaysUnderneath() {
        val picture = redAndBlue()
        val box = listOf(square(0.0, 0.0, 2.0))
        val without = assertNotNull(render(picture, box, ImageFit.Contain))
        val with = assertNotNull(render(picture, box, ImageFit.Contain, extendEdges = true, background = ColorRgba(0, 255, 0)))
        assertEquals(without.pixel(30, 50), with.pixel(30, 50), "inside the picture")
        assertEquals(without.pixel(70, 50), with.pixel(70, 50))
        assertEquals(red, with.pixel(10, 10), "outside it the picture's edge wins over the background")
    }

    // -- soft edges

    private val boxShape = listOf(square(0.0, 0.0, 2.0))
    private val redSquare = solid(40, 40, red)

    @Test
    fun noSoftnessIsExactlyTheSharpPicture() {
        val plain = assertNotNull(render(quadrants(), boxShape, ImageFit.Stretch, opacity = 0.8f))
        val zero = assertNotNull(render(quadrants(), boxShape, ImageFit.Stretch, opacity = 0.8f, edgeSoftness = 0f))
        assertTrue(plain.image.argb.contentEquals(zero.image.argb))
    }

    @Test
    fun theFadeGoesFromNothingAtTheEdgeToFullInTheMiddleAndKeepsTheColour() {
        val result = assertNotNull(render(redSquare, boxShape, ImageFit.Stretch, edgeSoftness = 1f))
        assertEquals(255, result.pixel(50, 50).alpha(), "the middle is untouched")
        assertTrue(result.pixel(0, 50).alpha() < 5, "the edge is almost gone, was ${result.pixel(0, 50).alpha()}")
        assertTrue(result.pixel(50, 0).alpha() < 5 && result.pixel(99, 50).alpha() < 5 && result.pixel(50, 99).alpha() < 5, "on every side")
        assertTrue(result.pixel(25, 50).alpha() in 110..145, "half way is about half, was ${result.pixel(25, 50).alpha()}")
        val alphas = (0..50).map { result.pixel(it, 50).alpha() }
        assertEquals(alphas, alphas.sorted(), "and it only ever grows towards the middle")
        assertEquals(0xFF0000, result.pixel(25, 50) and 0xFFFFFF, "the faded colour is still the picture's red")
    }

    @Test
    fun theSoftnessIsTheFractionOfHalfTheShorterSideThatFades() {
        val result = assertNotNull(render(redSquare, boxShape, ImageFit.Stretch, edgeSoftness = 0.2f))
        // 0.2 of half of 100 pixels: a band of 10 pixels.
        assertEquals(255, result.pixel(15, 50).alpha(), "beyond the band")
        // The pixel's centre is 5.5 pixels in: 0.55 of the band, which the smoothstep makes 0.575.
        assertTrue(result.pixel(5, 50).alpha() in 140..155, "half way through it, was ${result.pixel(5, 50).alpha()}")
        assertTrue(result.pixel(1, 50).alpha() < 20, "near the edge, was ${result.pixel(1, 50).alpha()}")
    }

    @Test
    fun aSoftEdgeMeltsIntoTheBackgroundColourInsteadOfIntoNothing() {
        val result = assertNotNull(render(redSquare, boxShape, ImageFit.Stretch, background = ColorRgba(0, 255, 0), edgeSoftness = 1f))
        assertEquals(red, result.pixel(50, 50))
        val edge = result.pixel(0, 50)
        assertEquals(255, edge.alpha(), "the background is there")
        assertTrue(((edge shr 8) and 0xFF) > 240 && ((edge shr 16) and 0xFF) < 15, "and the edge is nearly all of it")
    }

    @Test
    fun theFadeFollowsThePicturesEdgeNotTheBoxEdge() {
        val result = assertNotNull(render(redSquare, boxShape, ImageFit.Stretch, scale = 0.5f, edgeSoftness = 1f, background = null))
        assertEquals(255, result.pixel(result.image.width / 2, result.image.height / 2).alpha())
        assertTrue(result.pixel(0, result.image.height / 2).alpha() < 5, "the edge of the small picture fades")
    }

    @Test
    fun everyCopyOfATiledPictureFadesAtItsOwnEdges() {
        val result = assertNotNull(render(redSquare, boxShape, ImageFit.Stretch, scale = 0.5f, repeat = true, anchorX = 0.25f, anchorY = 0.25f, edgeSoftness = 1f))
        assertEquals(255, result.pixel(25, 25).alpha(), "the middle of a copy")
        assertTrue(result.pixel(49, 25).alpha() < 10 && result.pixel(50, 25).alpha() < 10, "its edge is where the next copy starts")
    }

    @Test
    fun extendedEdgesHaveNothingToFadeIntoSoTheSoftnessChangesNothing() {
        val sharp = assertNotNull(render(quadrants(), boxShape, ImageFit.Contain, scale = 0.5f, extendEdges = true))
        val soft = assertNotNull(render(quadrants(), boxShape, ImageFit.Contain, scale = 0.5f, extendEdges = true, edgeSoftness = 1f))
        assertTrue(sharp.image.argb.contentEquals(soft.image.argb))
    }

    @Test
    fun aSoftnessOutsideZeroToOneIsRejected() {
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImageFill(ImageRef.Bytes("k", byteArrayOf(1)), edgeSoftness = -0.1f) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImageFill(ImageRef.Bytes("k", byteArrayOf(1)), edgeSoftness = 1.1f) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImageFill(ImageRef.Bytes("k", byteArrayOf(1)), edgeSoftness = Float.NaN) }
    }

    // -- the picture's own rectangle


    private fun assertNear(expected: Double, actual: Double, message: String) =
        assertTrue(kotlin.math.abs(expected - actual) < 0.05, "$message: expected about $expected, got $actual")

    @Test
    fun withNothingAroundThePictureTheResultIsOnlyThePicturesOwnRectangleOfTheBox() {
        // A 2:1 picture contained in the 2 x 2 degree box covers the middle band: latitude 0.5..1.5.
        val result = assertNotNull(render(solid(40, 20, red), listOf(square(0.0, 0.0, 2.0)), ImageFit.Contain, background = null))
        assertNear(0.5, result.minLatitude, "south edge")
        assertNear(1.5, result.maxLatitude, "north edge")
        assertNear(0.0, result.minLongitude, "west edge")
        assertNear(2.0, result.maxLongitude, "east edge")
        assertEquals(100, result.image.width)
        assertEquals(50, result.image.height, "2:1 like the picture")
        assertTrue(result.opaque().size == result.image.width * result.image.height, "and all of it is picture: nothing empty is stored")
    }

    @Test
    fun aSmallPictureGetsAllTheResolutionInsteadOfALittleOfIt() {
        val picture = quadrants()
        val box = listOf(square(0.0, 0.0, 2.0))
        val inBox = assertNotNull(render(picture, box, ImageFit.Stretch, scale = 0.25f))
        val alone = assertNotNull(render(picture, box, ImageFit.Stretch, scale = 0.25f, background = null))
        assertEquals(100, inBox.image.width, "the whole box, with the picture a quarter of it")
        assertEquals(100, alone.image.width, "only the picture, as sharp as the whole box would be")
        assertEquals(red, alone.pixel(10, 10))
        assertEquals(green, alone.pixel(90, 10))
        assertEquals(blue, alone.pixel(10, 90))
        assertEquals(yellow, alone.pixel(90, 90))
        assertNear(0.75, alone.minLatitude, "south edge of a quarter size picture in the middle")
        assertNear(1.25, alone.maxLatitude, "north edge")
        assertNear(0.75, alone.minLongitude, "west edge")
        assertNear(1.25, alone.maxLongitude, "east edge")
    }

    @Test
    fun aPictureThatGoesPastTheBoxIsCutAtTheBox() {
        // Anchored on the west edge, half of it is outside the box: what is kept starts at the box's west edge.
        val result = assertNotNull(
            render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f, anchorX = 0f, anchorY = 0.5f, background = null)
        )
        assertNear(0.0, result.minLongitude, "the box's west edge")
        assertNear(0.5, result.maxLongitude, "the east edge of the picture")
        assertNear(0.5, result.minLatitude, "south edge")
        assertNear(1.5, result.maxLatitude, "north edge")
        assertEquals(green, result.pixel(result.image.width - 5, 5), "only the picture's east half is left: its top right")
    }

    @Test
    fun aBackgroundExtendedEdgesOrTilesNeedTheWholeBoxSoTheyKeepIt() {
        val box = listOf(square(0.0, 0.0, 2.0))
        val picture = solid(40, 20, red)
        val results = listOf(
            render(picture, box, ImageFit.Contain, background = ColorRgba(0, 255, 0)),
            render(picture, box, ImageFit.Contain, background = null, extendEdges = true),
            render(picture, box, ImageFit.Contain, background = null, repeat = true, scale = 0.5f),
        ).map { assertNotNull(it) }
        for (result in results) {
            assertNear(0.0, result.minLatitude, "south edge")
            assertNear(2.0, result.maxLatitude, "north edge")
            assertEquals(100, result.image.height, "the box is square")
        }
    }

    @Test
    fun aPictureEntirelyOutsideTheBoxStillGivesAnEmptyResultNotNothing() {
        val result = assertNotNull(
            render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.25f, anchorX = 3f, anchorY = 0.5f, background = null)
        )
        assertTrue(result.opaque().isEmpty(), "nothing of the picture is in the box")
    }

    // -- anchor and repeat

    @Test
    fun anAnchorPutsThePictureCentreAtThatFractionOfTheBox() {
        // Half the box (50 of 100 pixels) is the picture; by default it is in the middle, 25..75.
        val middle = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f))
        assertEquals(red, middle.pixel(30, 30))
        assertEquals(0, middle.pixel(10, 10).alpha())

        // Centred on a quarter of the way in from the north-west corner it covers 0..50.
        val moved = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f, anchorX = 0.25f, anchorY = 0.25f))
        assertEquals(red, moved.pixel(10, 10))
        assertEquals(yellow, moved.pixel(40, 40))
        assertEquals(0, moved.pixel(75, 75).alpha(), "nothing beyond it when it is not repeated")
    }

    @Test
    fun anAnchorCanBeGivenOnOneAxisAndTheOtherStaysWhereFitPutsIt() {
        val result = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f, anchorX = 0.25f))
        assertEquals(red, result.pixel(10, 30), "x moved to 0..50, y still 25..75")
        assertEquals(0, result.pixel(10, 10).alpha(), "above the picture")
        assertEquals(0, result.pixel(70, 50).alpha(), "east of the picture")
    }

    @Test
    fun repeatTilesThePictureAcrossTheShapeStartingWhereItWouldHaveBeen() {
        // Tiles of 50 pixels, one starting at the north-west corner: a 2 by 2 pattern of the four-colour picture. The picture has just 4
        // pixels, so only the middle of each quarter of a tile is a pure colour (the rest is blended between neighbours): 12 and 37 here.
        val result = assertNotNull(
            render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f, repeat = true, anchorX = 0.25f, anchorY = 0.25f)
        )
        for ((x, y) in listOf(12 to 12, 62 to 12, 12 to 62, 62 to 62)) assertEquals(red, result.pixel(x, y), "north-west quarter of the tile at ($x, $y)")
        for ((x, y) in listOf(37 to 12, 87 to 12, 37 to 62)) assertEquals(green, result.pixel(x, y), "north-east quarter at ($x, $y)")
        for ((x, y) in listOf(12 to 37, 62 to 87)) assertEquals(blue, result.pixel(x, y), "south-west quarter at ($x, $y)")
        for ((x, y) in listOf(37 to 37, 87 to 87)) assertEquals(yellow, result.pixel(x, y), "south-east quarter at ($x, $y)")
    }

    @Test
    fun repeatedTilesAlsoCoverTheBoxOnTheSideOfTheAnchorWhereOnlyPartOfATileFits() {
        // Centred in the middle at half size, the tile spans 25..75; the copies cover what is left of the box on every side.
        val result = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, scale = 0.5f, repeat = true))
        assertTrue(result.opaque().size >= 99 * 99, "the whole square is covered")
        assertEquals(yellow, result.pixel(12, 12), "the south-east quarter of the tile that begins at -25")
    }

    @Test
    fun aPictureThatAlreadyFillsTheBoxLooksTheSameRepeatedOrNot() {
        val once = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch))
        val tiled = assertNotNull(render(quadrants(), listOf(square(0.0, 0.0, 2.0)), ImageFit.Stretch, repeat = true))
        // Next to a pixel of the picture's own edge a tiled picture blends a little of the far side (1% here), a clamped one of itself:
        // the same colour to within a few levels (10 of 255).
        for ((x, y) in listOf(25 to 25, 75 to 25, 25 to 75, 75 to 75, 50 to 50)) {
            val a = once.pixel(x, y)
            val b = tiled.pixel(x, y)
            for (shift in listOf(24, 16, 8, 0)) {
                assertTrue(abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)) <= 10, "at ($x, $y), channel $shift: ${a.toUInt().toString(16)} vs ${b.toUInt().toString(16)}")
            }
        }
    }

    @Test
    fun anImageFillWithANonFiniteAnchorIsRejected() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            com.tcsappdev.worldwinddraw.core.style.ShapeStyle.ImageFill(
                com.tcsappdev.worldwinddraw.core.style.ImageRef.Bytes("k", byteArrayOf(1)), anchorX = Float.NaN,
            )
        }
    }
}
