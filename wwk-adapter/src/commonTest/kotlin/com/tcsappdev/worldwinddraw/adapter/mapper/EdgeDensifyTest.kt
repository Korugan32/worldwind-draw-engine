package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WWK reserves memory for 63 extra points on every edge of a polygon or path whatever the edge's length (about 6 KB per vertex),
 * which ran the heap out with a few thousand vertices. The mappers therefore divide the long edges themselves and switch WWK's
 * own subdivision off.
 */
class EdgeDensifyTest {
    private fun p(lat: Double, lon: Double) = GeoPosition(lat, lon)

    @Test
    fun shortEdgesAddNoPoints() {
        val positions = listOf(p(0.0, 0.0), p(0.0, 1.0), p(1.0, 1.0), p(1.0, 0.0))
        assertEquals(4, densifiedWwkPositions(positions, isClosed = true).size)
    }

    @Test
    fun aLongEdgeGetsPointsAlongTheGreatCircleSoItIsNeverLongerThanAFewDegrees() {
        val line = densifiedWwkPositions(listOf(p(0.0, 0.0), p(0.0, 20.0)), isClosed = false)
        assertEquals(9, line.size, "20 degrees in steps of at most 2.8125: 8 pieces, so 7 points between the 2 ends")
        assertEquals(0.0, line.first().longitude.inDegrees)
        assertEquals(20.0, line.last().longitude.inDegrees, 1e-9)
        line.zipWithNext().forEach { (a, b) -> assertTrue(b.longitude.inDegrees - a.longitude.inDegrees in 0.0..2.8125 + 1e-9) }
    }

    @Test
    fun theExtraPointsFollowTheGreatCircleNotTheStraightLatLonLine() {
        // From (60N, 0) to (60N, 60E) the shortest way bulges toward the pole: the middle is well north of 60.
        val line = densifiedWwkPositions(listOf(p(60.0, 0.0), p(60.0, 60.0)), isClosed = false)
        val middle = line[line.size / 2]
        assertTrue(middle.latitude.inDegrees > 63.0, "the middle of the arc is at ${middle.latitude.inDegrees}")
    }

    @Test
    fun aClosedRingGetsPointsOnTheEdgeBackToTheFirstVertexAndAnOpenPathDoesNot() {
        val corners = listOf(p(0.0, 0.0), p(0.0, 10.0), p(10.0, 10.0))
        val ring = densifiedWwkPositions(corners, isClosed = true)
        val path = densifiedWwkPositions(corners, isClosed = false)
        assertTrue(ring.size > path.size, "the closing edge (14 degrees long) is divided too")
        assertEquals(corners.last().latitude, path.last().latitude.inDegrees, "a path ends at its last vertex")
    }

    @Test
    fun theMappedShapesHaveWwksOwnSubdivisionSwitchedOffAndLongEdgesAlreadyDivided() {
        val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))
        val vertices = listOf(Vertex(VertexId("a"), p(0.0, 0.0)), Vertex(VertexId("b"), p(0.0, 30.0)), Vertex(VertexId("c"), p(30.0, 30.0)))

        val polygon = DrawnPolygon(EntityId("P"), style, vertices).toWwkPolygon()
        assertEquals(0, polygon.maximumNumEdgeIntervals)
        assertTrue(polygon.getBoundary(0).size > 3 * 5, "30 degree edges are divided: ${polygon.getBoundary(0).size} positions")

        val path = DrawnPolyline(EntityId("L"), style, vertices).toWwkPolyline()
        assertEquals(0, path.maximumNumEdgeIntervals)

        val highlight = DrawnPolygon(EntityId("P"), style, vertices).toWwkHighlight(ColorRgba(255, 255, 255))
        assertEquals(0, highlight.maximumNumEdgeIntervals)
        val lineHighlight = DrawnPolyline(EntityId("L"), style, vertices).toWwkHighlight(ColorRgba(255, 255, 255))
        assertEquals(0, lineHighlight.maximumNumEdgeIntervals)
    }
}
