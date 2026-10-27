package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PolygonRingsMapperTest {
    private fun ring(prefix: String, lat: Double, lon: Double, size: Double = 1.0) = listOf(
        Vertex(VertexId("${prefix}0"), GeoPosition(lat, lon)),
        Vertex(VertexId("${prefix}1"), GeoPosition(lat, lon + size)),
        Vertex(VertexId("${prefix}2"), GeoPosition(lat + size, lon + size)),
        Vertex(VertexId("${prefix}3"), GeoPosition(lat + size, lon)),
    )

    private val polygon = DrawnPolygon(
        id = EntityId("P"),
        style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0)),
        vertices = ring("o", 0.0, 0.0, 10.0),
        outlineStyle = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
        extraRings = listOf(ring("h", 3.0, 3.0), ring("p", 20.0, 20.0)),
    )

    /** What [toWwkPolygon] hands WWK for a ring: its positions with the points of the long edges between them (see [densifiedWwkPositions]). */
    private fun expectedBoundary(index: Int) = densifiedWwkPositions(polygon.rings[index].map { it.position }, isClosed = true)

    @Test
    fun everyRingBecomesABoundaryOfTheSameWwkPolygon() {
        val wwk = polygon.toWwkPolygon()

        assertEquals(3, wwk.boundaryCount)
        for (index in 0 until 3) assertEquals(expectedBoundary(index).size, wwk.getBoundary(index).size, "ring $index")
    }

    @Test
    fun theBoundariesAreTheRingsInTheirOrderEachStartingWithItsOwnFirstVertex() {
        val wwk = polygon.toWwkPolygon()

        for (index in 0 until 3) {
            val first = polygon.rings[index].first().position
            val boundary = wwk.getBoundary(index)
            assertEquals(first.latitude, boundary.first().latitude.inDegrees, 1e-9, "ring $index")
            assertEquals(first.longitude, boundary.first().longitude.inDegrees, 1e-9, "ring $index")
        }
    }

    @Test
    fun aPolygonWithOneRingStillHasOneBoundary() {
        assertEquals(1, polygon.copy(extraRings = emptyList()).toWwkPolygon().boundaryCount)
    }

    @Test
    fun theHighlightOutlinesEveryRingAndLeavesTheInteriorUnfilled() {
        val highlight = polygon.toWwkHighlight(ColorRgba(255, 255, 0))

        assertEquals(3, highlight.boundaryCount)
        assertFalse(highlight.attributes.isDrawInterior)
        assertFalse(highlight.isPickEnabled)
    }
}
