package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.shape.OrientationMode
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

    @Test
    fun aTurnedMarkerIsTurnedOnTheGlobe() {
        val placemark = marker().copy(rotation = 90f).toWwkPlacemark()

        assertEquals(90.0, placemark.imageRotation.inDegrees)
        assertEquals(OrientationMode.RELATIVE_TO_GLOBE, placemark.imageRotationReference)
    }

    @Test
    fun aMarkerThatIsNotTurnedIsLeftUprightOnTheScreen() {
        val placemark = marker().toWwkPlacemark()

        assertEquals(0.0, placemark.imageRotation.inDegrees)
        assertEquals(OrientationMode.RELATIVE_TO_SCREEN, placemark.imageRotationReference)
    }

    @Test
    fun aMarkerWithAltitudeIsThatManyMetersAboveTheGround() {
        val placemark = marker().copy(position = GeoPosition(41.0, 29.0, 50_000.0)).toWwkPlacemark()

        assertEquals(AltitudeMode.RELATIVE_TO_GROUND, placemark.altitudeMode)
        assertEquals(50_000.0, placemark.position.altitude)
    }
}
