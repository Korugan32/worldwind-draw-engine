package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.AltitudeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// jvmTest, not commonTest, for the same reason as MarkerMapperTest: PlacemarkAttributes()
// can't be constructed in Android's plain host unit tests (see StyleAttributesMapperTest).
class MarkerHighlightMapperTest {
    private val marker = DrawnMarker(
        id = EntityId("m"),
        style = ShapeStyle.SolidColor(ColorRgba(0, 255, 120)),
        position = GeoPosition(41.0, 29.0),
        size = 30f,
    )

    @Test
    fun highlightIsALargerNonPickableFrameClampedToTheGround() {
        val highlight = marker.toWwkHighlight(ColorRgba(0, 255, 0))

        assertTrue(highlight.attributes.imageScale > marker.size)
        assertFalse(highlight.isPickEnabled)
        assertEquals(AltitudeMode.CLAMP_TO_GROUND, highlight.altitudeMode)
        assertEquals(1f, highlight.attributes.imageColor.green)
    }
}
