package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpanMoverTest {
    /** v0..v(n-1) in a line along the equator, at the given longitudes. */
    private fun line(vararg lons: Double) = lons.mapIndexed { i, lon -> Vertex(VertexId("v$i"), GeoPosition(0.0, lon)) }

    private fun List<Vertex>.at(i: Int) = this[i].position
    private fun id(i: Int) = VertexId("v$i")
    private fun assertNear(expected: Double, actual: Double, message: String = "") = assertTrue(kotlin.math.abs(expected - actual) < 1e-9, "$message expected $expected, got $actual")

    @Test
    fun withoutNeighboursOnlyTheDraggedVertexMoves() {
        val vertices = line(0.0, 1.0, 2.0, 3.0)
        val moved = SpanMover.move(vertices, id(1), null, null, GeoPosition(5.0, 1.0), isClosed = false)
        assertEquals(GeoPosition(5.0, 1.0), moved.at(1))
        assertEquals(listOf(0, 2, 3).map { vertices.at(it) }, listOf(0, 2, 3).map { moved.at(it) })
    }

    @Test
    fun hiddenVerticesBetweenTheAnchorsFollowInProportionToTheirDistanceAlongTheStretch() {
        val vertices = line(0.0, 1.0, 2.0, 3.0, 4.0)
        // v2 is dragged 2 degrees north; v0 and v4 are its anchors, v1 and v3 are hidden between.
        val moved = SpanMover.move(vertices, id(2), id(0), id(4), GeoPosition(2.0, 2.0), isClosed = false)
        assertEquals(GeoPosition(2.0, 2.0), moved.at(2))
        assertNear(1.0, moved.at(1).latitude, "v1 is halfway between its anchor and v2")
        assertNear(1.0, moved.at(3).latitude, "v3 is halfway between v2 and its anchor")
        assertEquals(vertices.at(0), moved.at(0), "the anchors stay")
        assertEquals(vertices.at(4), moved.at(4))
    }

    @Test
    fun theWeightFollowsTheDistanceNotTheCountOfVertices() {
        // v1 is a quarter of the way from v0 to v2 (longitudes 0, 1, 4).
        val vertices = line(0.0, 1.0, 4.0)
        val moved = SpanMover.move(vertices, id(2), id(0), null, GeoPosition(8.0, 4.0), isClosed = false)
        assertNear(2.0, moved.at(1).latitude, "a quarter of the 8 degree move")
    }

    @Test
    fun anchorsNextToEachOtherLeaveNothingHiddenAndOnlyTheVertexMoves() {
        val vertices = line(0.0, 1.0, 2.0)
        val moved = SpanMover.move(vertices, id(1), id(0), id(2), GeoPosition(3.0, 1.0), isClosed = false)
        assertEquals(vertices.at(0), moved.at(0))
        assertEquals(vertices.at(2), moved.at(2))
        assertEquals(GeoPosition(3.0, 1.0), moved.at(1))
    }

    @Test
    fun vertexOutsideTheStretchesStayWhereTheyAre() {
        val vertices = line(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val moved = SpanMover.move(vertices, id(3), id(1), id(5), GeoPosition(4.0, 3.0), isClosed = false)
        assertEquals(vertices.at(0), moved.at(0))
        assertEquals(vertices.at(6), moved.at(6))
        assertTrue(moved.at(2).latitude > 0.0 && moved.at(4).latitude > 0.0)
    }

    @Test
    fun theEndOfAnOpenPathOnlyHasAStretchOnOneSide() {
        val vertices = line(0.0, 1.0, 2.0, 3.0)
        // v0 is the first vertex: no previous anchor, v3 is the next one; v1 and v2 are hidden.
        val moved = SpanMover.move(vertices, id(0), null, id(3), GeoPosition(3.0, 0.0), isClosed = false)
        assertNear(2.0, moved.at(1).latitude, "two thirds of the way back to the anchor")
        assertNear(1.0, moved.at(2).latitude)
        assertEquals(vertices.at(3), moved.at(3))
    }

    @Test
    fun aStretchOfARingMayRunPastTheEndOfTheList() {
        // Six vertices around the equator-ish ring; v0 is dragged with anchors v4 (previous) and v2 (next):
        // the stretch before it is v4, v5, v0 and the one after is v0, v1, v2.
        val ring = (0 until 6).map { Vertex(VertexId("v$it"), GeoPosition(if (it % 2 == 0) 1.0 else -1.0, it * 10.0)) }
        val moved = SpanMover.move(ring, id(0), id(4), id(2), GeoPosition(5.0, 0.0), isClosed = true)
        assertTrue(moved.at(5).latitude != ring.at(5).latitude, "v5, hidden before v0 past the end of the list, moved")
        assertTrue(moved.at(1).latitude != ring.at(1).latitude, "v1, hidden after v0, moved")
        assertEquals(ring.at(4), moved.at(4))
        assertEquals(ring.at(2), moved.at(2))
        assertEquals(ring.at(3), moved.at(3), "v3 is beyond the next anchor")
    }

    @Test
    fun inAnOpenPathAnAnchorOnTheWrongSideIsIgnored() {
        val vertices = line(0.0, 1.0, 2.0, 3.0)
        // The "previous" anchor v3 comes after v2 in the list: in a path there is no stretch from v3 forward to v2.
        val moved = SpanMover.move(vertices, id(2), id(3), null, GeoPosition(4.0, 2.0), isClosed = false)
        assertEquals(vertices.at(1), moved.at(1))
        assertEquals(vertices.at(3), moved.at(3))
    }

    @Test
    fun movingAcrossTheAntimeridianKeepsLongitudesInRange() {
        val vertices = listOf(
            Vertex(VertexId("v0"), GeoPosition(0.0, 178.0)),
            Vertex(VertexId("v1"), GeoPosition(0.0, 179.0)),
            Vertex(VertexId("v2"), GeoPosition(0.0, 180.0)),
        )
        val moved = SpanMover.move(vertices, id(2), id(0), null, GeoPosition(0.0, -178.0), isClosed = false) // 2 degrees east, across the line
        assertTrue(moved.all { it.position.longitude in -180.0..180.0 })
        // v1 moved half of the 2 degrees east: from 179 to 180, which is the same place as -180 (not 90 degrees the wrong way).
        assertNear(180.0, kotlin.math.abs(moved.at(1).longitude))
    }

    @Test
    fun aStretchWhosePointsCoincideSharesTheMoveEvenlyByIndex() {
        val vertices = listOf(
            Vertex(VertexId("v0"), GeoPosition(10.0, 10.0)),
            Vertex(VertexId("v1"), GeoPosition(10.0, 10.0)),
            Vertex(VertexId("v2"), GeoPosition(10.0, 10.0)),
            Vertex(VertexId("v3"), GeoPosition(10.0, 10.0)),
        )
        val moved = SpanMover.move(vertices, id(3), id(0), null, GeoPosition(13.0, 10.0), isClosed = false)
        assertNear(11.0, moved.at(1).latitude)
        assertNear(12.0, moved.at(2).latitude)
    }

    @Test
    fun anUnknownVertexLeavesEverythingAsItWas() {
        val vertices = line(0.0, 1.0, 2.0)
        assertSame(vertices, SpanMover.move(vertices, VertexId("nope"), null, null, GeoPosition(1.0, 1.0), isClosed = false))
    }
}
