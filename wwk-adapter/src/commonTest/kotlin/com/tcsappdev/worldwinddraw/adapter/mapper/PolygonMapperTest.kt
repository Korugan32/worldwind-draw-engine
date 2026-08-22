package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.AltitudeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolygonMapperTest {
    private fun vertex(lat: Double, lon: Double) =
        Vertex(VertexId(lat.toString() + lon), GeoPosition(lat, lon))

    private fun triangle(
        style: ShapeStyle = ShapeStyle.SolidColor(ColorRgba(255, 0, 0)),
        outlineStyle: ShapeStyle? = null,
        outlineWidth: Float = 1f,
    ) = DrawnPolygon(
        id = EntityId("triangle"),
        style = style,
        vertices = listOf(vertex(36.0, 26.0), vertex(42.0, 26.0), vertex(42.0, 45.0)),
        outlineStyle = outlineStyle,
        outlineWidth = outlineWidth,
    )

    @Test
    fun setsFollowTerrainAndClampToGround() {
        // Regression test for a real bug found via visual verification in the demo app
        // (2026-08-20): with either of these left at its default, WWK renders the polygon
        // as a 3D shape depth-tested against terrain, so it's invisible behind mountains
        // even though it "exists". Both must be set together — see PolygonMapper.kt.
        val polygon = triangle().toWwkPolygon()

        assertEquals(AltitudeMode.CLAMP_TO_GROUND, polygon.altitudeMode)
        assertTrue(polygon.isFollowTerrain)
    }

    @Test
    fun carriesAllVerticesIntoASingleBoundary() {
        val polygon = triangle().toWwkPolygon()

        assertEquals(1, polygon.boundaryCount)
    }

    @Test
    fun appliesTheEntitysStyle() {
        val polygon = triangle(ShapeStyle.SolidColor(ColorRgba(0, 255, 0))).toWwkPolygon()

        assertEquals(0f, polygon.attributes.interiorColor.red)
        assertEquals(1f, polygon.attributes.interiorColor.green)
        assertEquals(0f, polygon.attributes.interiorColor.blue)
    }

    @Test
    fun withNoOutlineStyleDrawsNoOutline() {
        // A null outlineStyle is a deliberate "no border" choice, not "use WWK's default
        // red 1px" — see DrawnPolygon's KDoc.
        val polygon = triangle(outlineStyle = null).toWwkPolygon()

        assertFalse(polygon.attributes.isDrawOutline)
    }

    @Test
    fun withOutlineStyleAppliesItsColorAndWidthIndependentlyOfTheInterior() {
        val polygon = triangle(
            style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0)),
            outlineStyle = ShapeStyle.SolidColor(ColorRgba(0, 0, 255)),
            outlineWidth = 6f,
        ).toWwkPolygon()

        assertTrue(polygon.attributes.isDrawOutline)
        assertEquals(1f, polygon.attributes.outlineColor.blue)
        assertEquals(6f, polygon.attributes.outlineWidth)
        // The outline color must not have leaked into (or been overwritten by) the interior.
        assertEquals(1f, polygon.attributes.interiorColor.red)
        assertEquals(0f, polygon.attributes.interiorColor.blue)
    }
}
