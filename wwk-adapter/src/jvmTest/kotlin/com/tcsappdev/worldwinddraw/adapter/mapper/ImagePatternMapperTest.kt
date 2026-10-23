package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// jvmTest, not commonTest: WWK's JS ImageSource.fromUrlString needs a browser `window` (it
// crashes on Node), PlacemarkAttributes() can't be built in Android's plain host tests (see
// StyleAttributesMapperTest), and decoding bytes is platform code — the JVM one is exercised here
// (the Android one was checked on an emulator, see docs/PLAN.md, Faz 8).
class ImagePatternMapperTest {
    private val pattern = ShapeStyle.ImagePattern("https://example.com/water.png")

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun bytesPattern(key: String, width: Int = 64, height: Int = 64, opacity: Float = 1f) =
        ShapeStyle.ImagePattern(ImageRef.Bytes(key, png(width, height)), opacity)

    // -- URL images

    @Test
    fun polygonInteriorGetsTheImageAndAWhiteColorSoItsPixelsAreNotTinted() {
        val attributes = pattern.toWwkShapeAttributes()

        assertNotNull(attributes.interiorImageSource)
        // WWK multiplies each image pixel by the interior colour; white leaves them unmodified.
        assertEquals(1f, attributes.interiorColor.red)
        assertEquals(1f, attributes.interiorColor.green)
        assertEquals(1f, attributes.interiorColor.blue)
        assertEquals(1f, attributes.interiorColor.alpha)
    }

    @Test
    fun aLineGetsTheImageOnItsOutlineAndNotItsInterior() {
        val attributes = pattern.toWwkLineAttributes(width = 6f)

        assertNotNull(attributes.outlineImageSource)
        assertEquals(6f, attributes.outlineWidth)
        assertNull(attributes.interiorImageSource)
    }

    @Test
    fun aMarkerWithAUrlImageIsDrawnAtTheImagesNaturalSize() {
        val attributes = pattern.toWwkPlacemarkAttributes(size = 40f)

        assertNotNull(attributes.imageSource)
        // The image's size isn't known until it is downloaded, and with an image WWK's scale is a
        // multiplier of its pixels — so a pixel size can't be honoured; 1.0 is "as is".
        assertEquals(1.0, attributes.imageScale)
    }

    @Test
    fun anAnimatedFillIsDrawnAsItsBaseUntilAnimationIsWiredIn() {
        val animated = ShapeStyle.AnimatedFill(pattern, animationId = "water")

        assertNotNull(animated.toWwkShapeAttributes().interiorImageSource)
    }

    // -- Opacity

    @Test
    fun opacityIsTheAlphaOfThePolygonFillColor() {
        val attributes = ShapeStyle.ImagePattern("https://example.com/a.png", opacity = 0.4f).toWwkShapeAttributes()

        assertEquals(0.4f, attributes.interiorColor.alpha)
        assertEquals(1f, attributes.interiorColor.red, "still white: opacity must not tint the image")
    }

    @Test
    fun opacityAlsoAppliesToALinesOutlineAndAMarkersIcon() {
        val style = ShapeStyle.ImagePattern("https://example.com/a.png", opacity = 0.25f)

        assertEquals(0.25f, style.toWwkLineAttributes(3f).outlineColor.alpha)
        assertEquals(0.25f, style.toWwkPlacemarkAttributes(20f).imageColor.alpha)
    }

    // -- Images given as bytes

    @Test
    fun bytesBecomeAnImageAndOpacityApplies() {
        val attributes = bytesPattern("opacity-test", opacity = 0.6f).toWwkShapeAttributes()

        assertNotNull(attributes.interiorImageSource)
        assertEquals(0.6f, attributes.interiorColor.alpha)
    }

    @Test
    fun theSameKeyGivesTheSameDecodedImageSoItIsNotDecodedAgainOnEveryRedraw() {
        val first = bytesPattern("cache-test").toWwkShapeAttributes().interiorImageSource
        val second = bytesPattern("cache-test").toWwkShapeAttributes().interiorImageSource

        // WWK compares an in-memory image by reference, so the same instance is what lets its
        // texture cache work.
        assertSame(first, second)
    }

    @Test
    fun aBigImageIsShrunkToFitTheMaximumDimensionKeepingItsAspect() {
        val loaded = ImageRef.Bytes("big-test", png(3000, 1500)).toLoadedImage()

        assertNotNull(loaded)
        assertEquals(MAX_IMAGE_DIMENSION, loaded.width)
        assertEquals(MAX_IMAGE_DIMENSION / 2, loaded.height)
    }

    @Test
    fun aSmallImageIsNotUpscaled() {
        val loaded = ImageRef.Bytes("small-test", png(200, 100)).toLoadedImage()

        assertNotNull(loaded)
        assertEquals(200, loaded.width)
        assertEquals(100, loaded.height)
    }

    @Test
    fun aMarkerWithBytesHonoursItsSizeInPixelsOnTheLongestSide() {
        val attributes = bytesPattern("marker-test", width = 200, height = 100).toWwkPlacemarkAttributes(size = 50f)

        assertEquals(0.25, attributes.imageScale) // 200 px wide image shown 50 px wide
    }

    @Test
    fun bytesThatAreNotAnImageFallBackToAGreyPlaceholderInsteadOfCrashing() {
        val broken = ShapeStyle.ImagePattern(ImageRef.Bytes("broken-test", byteArrayOf(1, 2, 3, 4)))

        val attributes = broken.toWwkShapeAttributes()

        assertNull(attributes.interiorImageSource)
        assertEquals(0.5f, attributes.interiorColor.alpha)
        assertTrue(attributes.interiorColor.red == 0.5f, "a neutral grey, not the default opaque white")
        assertNull(ImageRef.Bytes("broken-test", byteArrayOf(1, 2, 3, 4)).toLoadedImage())
    }

    // -- ImageFill on things that aren't a polygon's interior

    @Test
    fun anImageFillPolygonKeepsItsOwnInteriorInvisibleBecauseAPictureIsDrawnBelowIt() {
        val attributes = ShapeStyle.ImageFill(ImageRef.Bytes("fill-mapper", png(10, 10))).toWwkShapeAttributes()

        assertEquals(0f, attributes.interiorColor.alpha)
        assertNull(attributes.interiorImageSource)
    }

    @Test
    fun anImageFillOnALineOrMarkerBehavesLikeAPattern() {
        val fill = ShapeStyle.ImageFill(ImageRef.Bytes("fill-mapper-2", png(200, 100)), opacity = 0.5f)

        assertNotNull(fill.toWwkLineAttributes(4f).outlineImageSource)
        assertEquals(0.5f, fill.toWwkLineAttributes(4f).outlineColor.alpha)
        assertEquals(0.25, fill.toWwkPlacemarkAttributes(50f).imageScale)
    }
}
