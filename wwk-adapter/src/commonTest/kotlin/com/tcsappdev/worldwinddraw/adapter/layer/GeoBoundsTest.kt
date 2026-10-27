package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoBoundsTest {
    private fun vertices(vararg latLon: Pair<Double, Double>) =
        latLon.mapIndexed { i, (lat, lon) -> Vertex(VertexId("v$i"), GeoPosition(lat, lon)) }

    @Test
    fun theBoxOfSomeVerticesCoversThemAll() {
        val box = GeoBounds.of(vertices(10.0 to 20.0, 30.0 to 5.0, 20.0 to 40.0))
        assertEquals(10.0, box.minLat)
        assertEquals(30.0, box.maxLat)
        assertEquals(5.0, box.minLon)
        assertEquals(40.0, box.maxLon)
    }

    @Test
    fun boxesThatOverlapOrTouchIntersectAndOthersDoNot() {
        val a = GeoBounds(0.0, 10.0, 0.0, 10.0)
        assertTrue(a.intersects(GeoBounds(5.0, 15.0, 5.0, 15.0)))
        assertTrue(a.intersects(GeoBounds(10.0, 20.0, 10.0, 20.0)), "touching at a corner counts")
        assertFalse(a.intersects(GeoBounds(11.0, 20.0, 0.0, 10.0)), "apart in latitude")
        assertFalse(a.intersects(GeoBounds(0.0, 10.0, 11.0, 20.0)), "apart in longitude")
    }

    @Test
    fun aBoxWiderThanHalfTheGlobeMightWrapAroundTheAntimeridianSoItSpansEveryLongitude() {
        // A shape with points at 170 and -170 has a box from -170 to 170: almost certainly one that crosses the antimeridian.
        val acrossTheAntimeridian = GeoBounds.of(vertices(0.0 to 170.0, 10.0 to -170.0, 5.0 to 175.0))
        assertTrue(acrossTheAntimeridian.intersects(GeoBounds(0.0, 10.0, 100.0, 110.0)), "never reported as off screen by mistake")
        assertTrue(acrossTheAntimeridian.intersects(GeoBounds(0.0, 10.0, -100.0, -90.0)))
        assertFalse(acrossTheAntimeridian.intersects(GeoBounds(50.0, 60.0, 0.0, 10.0)), "latitude still has to match")
    }

    @Test
    fun growingABoxAddsAFractionOfItsSizeOnEverySideAndStaysOnTheGlobe() {
        val grown = GeoBounds(0.0, 10.0, 0.0, 20.0).grownBy(0.5)
        assertEquals(-5.0, grown.minLat)
        assertEquals(15.0, grown.maxLat)
        assertEquals(-10.0, grown.minLon)
        assertEquals(30.0, grown.maxLon)
        val atThePole = GeoBounds(80.0, 90.0, 0.0, 10.0).grownBy(1.0)
        assertEquals(90.0, atThePole.maxLat)
    }

    @Test
    fun theBoxOfSeveralRingsHoldsThemAll() {
        fun ring(lat: Double, lon: Double) = listOf(
            Vertex(VertexId("a$lat$lon"), GeoPosition(lat, lon)),
            Vertex(VertexId("b$lat$lon"), GeoPosition(lat + 1, lon + 1)),
            Vertex(VertexId("c$lat$lon"), GeoPosition(lat, lon + 2)),
        )
        val box = GeoBounds.ofRings(listOf(ring(10.0, 20.0), ring(-5.0, 40.0)))
        assertEquals(-5.0, box.minLat)
        assertEquals(11.0, box.maxLat)
        assertEquals(20.0, box.minLon)
        assertEquals(42.0, box.maxLon)
    }
}
