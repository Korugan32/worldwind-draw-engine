package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * JVM-only, not commonTest: see the comment in [StyleAttributesMapperTest] for why
 * [toWwkPlacemarkAttributes] can't run under Android's plain host unit tests.
 */
class PlacemarkStyleAttributesMapperTest {
    @Test
    fun solidColorMapsToImageColorAndScaleWithNoImageSource() {
        // A marker has no interior either, and (unlike a polyline) no outline — its "shape"
        // is the imageColor square PlacemarkAttributes draws when imageSource is null.
        val attributes = ShapeStyle.SolidColor(ColorRgba(0, 255, 0)).toWwkPlacemarkAttributes(size = 24f)

        assertEquals(1f, attributes.imageColor.green)
        assertEquals(24.0, attributes.imageScale)
        assertNull(attributes.imageSource)
    }
}
