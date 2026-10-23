package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// jvmTest, not commonTest: WWK's JS ImageSource.fromUrlString needs a browser `window` (it
// crashes on Node), and PlacemarkAttributes() can't be built in Android's plain host tests (see
// StyleAttributesMapperTest). The pattern itself was verified visually on Android: tiled,
// unmirrored, sized in screen pixels, opaque (see docs/PLAN.md, Faz 8).
class ImagePatternMapperTest {
    private val pattern = ShapeStyle.ImagePattern("https://example.com/water.png")

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
    fun aMarkerGetsTheImageAsItsIconAtTheRequestedSize() {
        val attributes = pattern.toWwkPlacemarkAttributes(size = 40f)

        assertNotNull(attributes.imageSource)
        assertEquals(40.0, attributes.imageScale)
    }

    @Test
    fun anAnimatedFillIsDrawnAsItsBaseUntilAnimationIsWiredIn() {
        val animated = ShapeStyle.AnimatedFill(pattern, animationId = "water")

        assertNotNull(animated.toWwkShapeAttributes().interiorImageSource)
    }
}
