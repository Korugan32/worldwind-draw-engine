package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.AltitudeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineMapperTest {
    private fun vertex(lat: Double, lon: Double) =
        Vertex(VertexId(lat.toString() + lon), GeoPosition(lat, lon))

    private fun line(
        style: ShapeStyle = ShapeStyle.SolidColor(ColorRgba(0, 0, 255)),
        lineWidth: Float = 1f,
    ) = DrawnPolyline(
        id = EntityId("line"),
        style = style,
        vertices = listOf(vertex(41.0, 29.0), vertex(39.9, 32.8), vertex(37.0, 35.3)),
        lineWidth = lineWidth,
    )

    @Test
    fun setsFollowTerrainAndClampToGround() {
        // Same terrain-occlusion issue as Polygon (see PolygonMapperTest) — a Path is also
        // depth-tested against 3D terrain unless it's a surface shape.
        val path = line().toWwkPolyline()

        assertEquals(AltitudeMode.CLAMP_TO_GROUND, path.altitudeMode)
        assertTrue(path.isFollowTerrain)
    }

    @Test
    fun appliesTheEntitysStyleToTheOutlineNotTheInterior() {
        val path = line(ShapeStyle.SolidColor(ColorRgba(255, 0, 0)), lineWidth = 7f).toWwkPolyline()

        assertEquals(1f, path.attributes.outlineColor.red)
        assertEquals(7f, path.attributes.outlineWidth)
    }

    @Test
    fun aLineWithAltitudeIsAboveTheGroundNotOnIt() {
        val flown = line().copy(vertices = listOf(vertex(41.0, 29.0), Vertex(VertexId("up"), GeoPosition(39.9, 32.8, 100_000.0)), vertex(37.0, 35.3)))
        val path = flown.toWwkPolyline()

        assertEquals(AltitudeMode.RELATIVE_TO_GROUND, path.altitudeMode)
        assertTrue(!path.isFollowTerrain)
        assertTrue(path.positions.any { it.altitude == 100_000.0 })
    }

    @Test
    fun theAltitudeOfALongEdgeGoesStraightBetweenItsEnds() {
        val flown = line().copy(vertices = listOf(Vertex(VertexId("a"), GeoPosition(0.0, 0.0, 0.0)), Vertex(VertexId("b"), GeoPosition(0.0, 40.0, 40_000.0))))
        val altitudes = flown.toWwkPolyline().positions.map { it.altitude }
        assertTrue(altitudes.size > 2)
        assertEquals(altitudes.sorted(), altitudes)
        assertEquals(40_000.0, altitudes.last())
    }
}
