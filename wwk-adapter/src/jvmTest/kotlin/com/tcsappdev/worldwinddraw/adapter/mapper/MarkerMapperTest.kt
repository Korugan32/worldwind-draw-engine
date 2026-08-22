package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.AltitudeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkerMapperTest {
    private fun marker(
        style: ShapeStyle = ShapeStyle.SolidColor(ColorRgba(0, 255, 0)),
        size: Float = 16f,
    ) = DrawnMarker(
        id = EntityId("marker"),
        style = style,
        position = GeoPosition(41.0082, 28.9784),
        size = size,
    )

    @Test
    fun setsClampToGround() {
        // Same terrain-occlusion issue as Polygon/Path (see PolygonMapperTest) — a
        // Placemark left at ABSOLUTE altitude 0 can render behind higher terrain, since
        // PlacemarkAttributes.isDepthTest defaults to true.
        val placemark = marker().toWwkPlacemark()

        assertEquals(AltitudeMode.CLAMP_TO_GROUND, placemark.altitudeMode)
    }

    @Test
    fun carriesThePositionAndTheEntitysStyle() {
        val placemark = marker(ShapeStyle.SolidColor(ColorRgba(255, 0, 0)), size = 40f).toWwkPlacemark()

        assertEquals(41.0082, placemark.position.latitude.inDegrees)
        assertEquals(28.9784, placemark.position.longitude.inDegrees)
        assertEquals(1f, placemark.attributes.imageColor.red)
        assertEquals(40.0, placemark.attributes.imageScale)
    }
}
