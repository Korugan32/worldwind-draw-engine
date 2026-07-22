package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryOpsTest {

    @Test
    fun insertVertexAddsPointAtCorrectPosition() {
        val ring = listOf<GeoPosition>(
            GeoPosition(latitude = 12.0, longitude = 90.5),
            GeoPosition(latitude = 20.0, longitude = 100.0),
            GeoPosition(latitude = 45.0, longitude = 70.0),
            GeoPosition(latitude = 60.0, longitude = 80.0),
        )
        val newPoint = GeoPosition(latitude = 30.0, longitude = 60.0)
        val result = GeometryOps.insertVertex(ring = ring, afterEdgeIndex = 0, position = newPoint)
        assertEquals(newPoint, result[1])
        assertEquals(5, result.size)
    }

    private val unitSquare = listOf(
        GeoPosition(0.0, 0.0),
        GeoPosition(0.0, 10.0),
        GeoPosition(10.0, 10.0),
        GeoPosition(10.0, 0.0),
    )

    private data class PointInPolygonCase(val description: String, val point: GeoPosition, val expected: Boolean)

    @Test
    fun isPointInPolygon_tableDriven() {
        val cases = listOf(
            PointInPolygonCase("center is inside", GeoPosition(5.0, 5.0), true),
            PointInPolygonCase("asymmetric point is inside", GeoPosition(3.0, 7.0), true),
            PointInPolygonCase("far outside", GeoPosition(50.0, 50.0), false),
            PointInPolygonCase("just outside below the ring", GeoPosition(-1.0, 5.0), false),
            PointInPolygonCase("just outside to the right", GeoPosition(5.0, 15.0), false),
        )

        for (case in cases) {
            assertEquals(case.expected, GeometryOps.isPointInPolygon(case.point, unitSquare), case.description)
        }
    }

    @Test
    fun isPointInPolygon_regressionForDirectionalCrossingBug() {
        // While refactoring the crossing check from division to multiplication, we briefly had
        // a version that silently flipped direction on edges going from a higher to a lower
        // longitude, which made this exact point (dead center of the square) return `false`
        // instead of `true`. Kept as an explicit regression case so that bug can't come back
        // unnoticed.
        assertEquals(true, GeometryOps.isPointInPolygon(GeoPosition(5.0, 5.0), unitSquare))
    }

    @Test
    fun nearestEdge_picksClosestSegmentOnClosedRing() {
        val hit = GeometryOps.nearestEdge(point = GeoPosition(-1.0, 5.0), ring = unitSquare, closed = true)
        assertEquals(0, hit.edgeIndex)
        assertEquals(GeoPosition(0.0, 5.0), hit.closestPoint)
        assertTrue(abs(hit.distance - 1.0) < 1e-9)
    }

    @Test
    fun nearestEdge_openPolylineExcludesClosingSegment() {
        // (5,5) sits exactly on the diagonal from the last point back to the first, which only
        // exists if the ring is wrongly treated as closed.
        val line = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 10.0), GeoPosition(10.0, 10.0))
        val hit = GeometryOps.nearestEdge(point = GeoPosition(5.0, 5.0), ring = line, closed = false)
        assertTrue(hit.edgeIndex < line.size - 1)
        assertTrue(abs(hit.distance - 5.0) < 1e-9)
    }

    @Test
    fun nearestEdgeAndInsertVertexWorkTogether() {
        val hit = GeometryOps.nearestEdge(point = GeoPosition(-1.0, 5.0), ring = unitSquare, closed = true)
        val updated = GeometryOps.insertVertex(ring = unitSquare, afterEdgeIndex = hit.edgeIndex, position = hit.closestPoint)
        assertEquals(5, updated.size)
        assertEquals(GeoPosition(0.0, 5.0), updated[1])
    }
}