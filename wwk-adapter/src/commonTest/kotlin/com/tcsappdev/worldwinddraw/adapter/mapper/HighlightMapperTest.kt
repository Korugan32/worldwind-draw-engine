package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HighlightMapperTest {
    private fun vertex(lat: Double, lon: Double) = Vertex(VertexId(lat.toString() + lon), GeoPosition(lat, lon))

    private val COLOR = ColorRgba(red = 10, green = 200, blue = 120)

    private val polygon = DrawnPolygon(
        id = EntityId("poly"),
        style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0)),
        vertices = listOf(vertex(0.0, 0.0), vertex(1.0, 0.0), vertex(1.0, 1.0)),
        outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255)),
        outlineWidth = 4f,
    )

    private val polyline = DrawnPolyline(
        id = EntityId("line"),
        style = ShapeStyle.SolidColor(ColorRgba(255, 220, 0)),
        vertices = listOf(vertex(0.0, 0.0), vertex(1.0, 1.0)),
        lineWidth = 5f,
    )

    @Test
    fun polygonHighlightIsAnOutlineOnlyShapeThatDoesNotTouchTheFill() {
        val highlight = polygon.toWwkHighlight(COLOR)

        assertFalse(highlight.attributes.isDrawInterior)
        assertTrue(highlight.attributes.isDrawOutline)
        assertEquals(COLOR.red / 255f, highlight.attributes.outlineColor.red)
        assertEquals(COLOR.blue / 255f, highlight.attributes.outlineColor.blue)
        // The entity's own polygon is built separately and must be left exactly as styled.
        val own = polygon.toWwkPolygon()
        assertEquals(1f, own.attributes.interiorColor.red)
        assertEquals(0f, own.attributes.interiorColor.blue)
    }

    @Test
    fun polygonHighlightIsAlwaysWiderThanTheEntitysOwnOutline() {
        val highlight = polygon.toWwkHighlight(COLOR)

        assertTrue(highlight.attributes.outlineWidth > polygon.outlineWidth)
    }

    @Test
    fun polylineHighlightIsAWiderHaloThanTheLineItself() {
        val highlight = polyline.toWwkHighlight(COLOR)

        assertTrue(highlight.attributes.outlineWidth > polyline.lineWidth)
    }

    @Test
    fun highlightColorIsExactlyWhatWasAskedFor() {
        // A host-chosen colour must reach WWK untouched (alpha included) — it is not a fixed palette.
        val translucent = ColorRgba(red = 255, green = 0, blue = 0, alpha = 128)
        val highlight = polyline.toWwkHighlight(translucent)

        assertEquals(1f, highlight.attributes.outlineColor.red)
        assertEquals(128 / 255f, highlight.attributes.outlineColor.alpha)
    }

    @Test
    fun highlightsAreNotPickableSoTapsFallThroughToTheRealShape() {
        // WWK skips a renderable with isPickEnabled = false during the pick pass, so a tap on
        // the highlighted border still resolves to the tagged entity underneath.
        assertFalse(polygon.toWwkHighlight(COLOR).isPickEnabled)
        assertFalse(polyline.toWwkHighlight(COLOR).isPickEnabled)
    }

    @Test
    fun highlightsAreTerrainDrapedLikeTheShapesTheyOutline() {
        // Same terrain-occlusion rule as PolygonMapperTest/PolylineMapperTest: without both
        // settings WWK depth-tests the highlight against mountains and it can vanish.
        val polygonHighlight = polygon.toWwkHighlight(COLOR)
        val lineHighlight = polyline.toWwkHighlight(COLOR)

        assertEquals(AltitudeMode.CLAMP_TO_GROUND, polygonHighlight.altitudeMode)
        assertTrue(polygonHighlight.isFollowTerrain)
        assertEquals(AltitudeMode.CLAMP_TO_GROUND, lineHighlight.altitudeMode)
        assertTrue(lineHighlight.isFollowTerrain)
    }
}
