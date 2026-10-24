package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolygonCenterTest {
    private fun ring(vararg latLon: Pair<Double, Double>) = latLon.map { (lat, lon) -> GeoPosition(lat, lon) }

    private fun contains(ring: List<GeoPosition>, p: GeoPosition): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val a = ring[i]; val b = ring[j]
            if ((a.latitude > p.latitude) != (b.latitude > p.latitude) &&
                p.longitude < (b.longitude - a.longitude) * (p.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
            ) inside = !inside
            j = i
        }
        return inside
    }

    @Test
    fun theVisualCentreOfASquareIsItsMiddleAndItsRadiusIsHalfTheSide() {
        val square = ring(0.0 to 0.0, 0.0 to 10.0, 10.0 to 10.0, 10.0 to 0.0)
        val center = PolygonCenter.visualCenter(square)
        // The shrinking of longitude is by the cosine of latitude 5: tiny, so the middle is (5, 5) to a hair.
        assertTrue(abs(center.position.latitude - 5.0) < 0.05, "latitude ${center.position.latitude}")
        assertTrue(abs(center.position.longitude - 5.0) < 0.05, "longitude ${center.position.longitude}")
        // 5 degrees of latitude is about 556 km.
        assertTrue(abs(center.radiusMeters - 5 * 111_194.9) < 0.05 * 111_194.9, "radius ${center.radiusMeters}")
    }

    @Test
    fun aHorseshoeHasItsCentroidOutsideButItsVisualCentreInside() {
        // A "C": a 3 wide bar on the west and two 3 high arms reaching east, with a notch between them.
        val horseshoe = ring(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 7.0 to 10.0, 7.0 to 3.0, 3.0 to 3.0, 3.0 to 10.0, 0.0 to 10.0)
        val center = PolygonCenter.visualCenter(horseshoe)
        assertTrue(contains(horseshoe, center.position), "the centre ${center.position} must be inside the shape")
        assertTrue(center.radiusMeters > 1.4 * 111_194.9, "an arm is 3 wide: a circle of radius 1.5 degrees fits; got ${center.radiusMeters}")
    }

    @Test
    fun theRadiusIsTheDistanceToTheNearestEdge() {
        val rectangle = ring(0.0 to 0.0, 0.0 to 20.0, 4.0 to 20.0, 4.0 to 0.0) // 4 high, 20 wide
        val center = PolygonCenter.visualCenter(rectangle)
        assertTrue(abs(center.position.latitude - 2.0) < 0.05)
        // The biggest circle in a 4 high strip has radius 2 degrees, wherever along it.
        assertTrue(abs(center.radiusMeters - 2 * 111_194.9) < 0.05 * 111_194.9, "radius ${center.radiusMeters}")
    }

    @Test
    fun longitudeIsShrunkAtHighLatitudesSoACircleThereIsStillRound() {
        // At latitude 60 a degree of longitude is half as long as a degree of latitude: 20 degrees of longitude by 5
        // of latitude is a 10 by 5 rectangle on the ground, whose biggest circle has radius 2.5 degrees of latitude.
        val rectangle = ring(60.0 to 0.0, 60.0 to 20.0, 65.0 to 20.0, 65.0 to 0.0)
        val center = PolygonCenter.visualCenter(rectangle)
        // (The true middle latitude here is 62.5, so the shrinking is cos 62.5, a bit under a half: the radius is still about 2.5.)
        assertTrue(abs(center.radiusMeters - 2.5 * 111_194.9) < 0.1 * 111_194.9, "radius ${center.radiusMeters}")
        assertTrue(abs(center.position.latitude - 62.5) < 0.1)
    }

    @Test
    fun aTriangleCentreIsInsideItAndTheRadiusFitsInsideEveryEdge() {
        val triangle = ring(0.0 to 0.0, 0.0 to 12.0, 9.0 to 4.0)
        val center = PolygonCenter.visualCenter(triangle)
        assertTrue(contains(triangle, center.position))
        assertTrue(center.radiusMeters > 2.0 * 111_194.9)
    }

    @Test
    fun aRingOfFewerThanThreePointsHasNoInsideSoItIsItsFirstPointWithNoRadius() {
        val line = ring(1.0 to 2.0, 3.0 to 4.0)
        val center = PolygonCenter.visualCenter(line)
        assertEquals(GeoPosition(1.0, 2.0), center.position)
        assertEquals(0.0, center.radiusMeters)
        assertFailsWith<IllegalArgumentException> { PolygonCenter.visualCenter(emptyList()) }
    }

    @Test
    fun aVeryJaggedShapeStillFindsACentreInsideItInReasonableWork() {
        // A star with 2,000 points alternating between two radii.
        val star = List(2000) { i ->
            val angle = 2 * kotlin.math.PI * i / 2000
            val r = if (i % 2 == 0) 10.0 else 6.0
            GeoPosition(r * kotlin.math.sin(angle), r * kotlin.math.cos(angle))
        }
        val center = PolygonCenter.visualCenter(star)
        assertTrue(contains(star, center.position))
        assertTrue(center.radiusMeters > 5.0 * 111_194.9, "the inner radius is about 6: got ${center.radiusMeters}")
    }
}
