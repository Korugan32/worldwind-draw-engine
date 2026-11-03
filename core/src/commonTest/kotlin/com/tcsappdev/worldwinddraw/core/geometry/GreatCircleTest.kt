package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GreatCircleTest {
    @Test
    fun anEdgeAlongTheEquatorStaysOnTheEquatorAndIsSplitEvenly() {
        val points = GreatCircle.intermediatePoints(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0), maxStepDegrees = 10.0)

        assertEquals(3, points.size)
        assertTrue(points.all { abs(it.latitude) < 1e-9 })
        assertEquals(listOf(10.0, 20.0, 30.0), points.map { it.longitude.let { lon -> kotlin.math.round(lon * 1e6) / 1e6 } })
    }

    @Test
    fun aLongEastWestEdgeAwayFromTheEquatorBulgesTowardsThePole() {
        // The great circle between two points on the 45th parallel passes north of it — which is
        // exactly why a straight line in latitude/longitude would not match the drawn edge.
        val points = GreatCircle.intermediatePoints(GeoPosition(45.0, 0.0), GeoPosition(45.0, 90.0), maxStepDegrees = 15.0)

        val middle = points[points.size / 2]
        assertTrue(middle.latitude > 50.0, "midpoint latitude was ${middle.latitude}")
        assertTrue(points.all { it.latitude > 45.0 })
    }

    @Test
    fun anEdgeShorterThanTheStepHasNothingInBetween() {
        assertTrue(GreatCircle.intermediatePoints(GeoPosition(10.0, 10.0), GeoPosition(10.1, 10.1), maxStepDegrees = 1.0).isEmpty())
    }

    @Test
    fun identicalOrOppositePointsHaveNoUniqueGreatCircle() {
        assertTrue(GreatCircle.intermediatePoints(GeoPosition(10.0, 10.0), GeoPosition(10.0, 10.0), maxStepDegrees = 1.0).isEmpty())
        assertTrue(GreatCircle.intermediatePoints(GeoPosition(0.0, 0.0), GeoPosition(0.0, 180.0), maxStepDegrees = 1.0).isEmpty())
    }

    @Test
    fun aPointDueEastAlongTheEquatorStaysOnItAndIsTheAngleAway() {
        val point = GreatCircle.destination(GeoPosition(0.0, 10.0), bearing = PI / 2, angle = 20.0 * PI / 180.0)
        assertTrue(abs(point.latitude) < 1e-9 && abs(point.longitude - 30.0) < 1e-9, "was $point")
    }

    @Test
    fun theDestinationIsExactlyTheAngleAwayInAnyDirection() {
        val from = GeoPosition(40.0, 33.0)
        for (bearing in listOf(0.0, 1.0, 2.5, 4.0, 5.9)) {
            val point = GreatCircle.destination(from, bearing, 0.3)
            assertTrue(abs(GreatCircle.angleBetween(from, point) - 0.3) < 1e-9, "bearing $bearing")
        }
    }

    @Test
    fun goingNorthRaisesTheLatitudeAndGoingAcrossTheAntimeridianWrapsTheLongitude() {
        assertTrue(GreatCircle.destination(GeoPosition(10.0, 0.0), 0.0, 0.1).latitude > 15.0)
        val across = GreatCircle.destination(GeoPosition(0.0, 179.0), PI / 2, 5.0 * PI / 180.0)
        assertTrue(across.longitude < -170.0, "was ${across.longitude}")
    }

    @Test
    fun twoPointsTheSameDistanceAwayInOneDirectionAreOneAnother() {
        val half = GreatCircle.pointAt(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0), 0.5)
        val by = GreatCircle.destination(GeoPosition(0.0, 0.0), PI / 2, 20.0 * PI / 180.0)
        assertTrue(abs(half.longitude - by.longitude) < 1e-9)
    }

    @Test
    fun theBearingIsClockwiseFromNorth() {
        val origin = GeoPosition(0.0, 0.0)
        assertTrue(abs(GreatCircle.bearing(origin, GeoPosition(10.0, 0.0)) - 0.0) < 1e-9)
        assertTrue(abs(GreatCircle.bearing(origin, GeoPosition(0.0, 10.0)) - PI / 2) < 1e-9)
        assertTrue(abs(GreatCircle.bearing(origin, GeoPosition(-10.0, 0.0)) - PI) < 1e-9)
        assertTrue(abs(GreatCircle.bearing(origin, GeoPosition(0.0, -10.0)) - 3 * PI / 2) < 1e-9)
    }

    @Test
    fun goingTheBearingTheAngleAwayGetsThere() {
        val from = GeoPosition(30.0, 20.0)
        val to = GeoPosition(45.0, 70.0)
        val there = GreatCircle.destination(from, GreatCircle.bearing(from, to), GreatCircle.angleBetween(from, to))
        assertTrue(abs(there.latitude - to.latitude) < 1e-9 && abs(there.longitude - to.longitude) < 1e-9)
    }

    @Test
    fun aLongEastWestEdgeStartsToTheNorthOfEastAndEndsToTheSouthOfIt() {
        // The arc between two points on the 45th parallel bulges to the pole: it leaves heading north of east and arrives heading south of it.
        val a = GeoPosition(45.0, 0.0)
        val b = GeoPosition(45.0, 90.0)
        assertTrue(GreatCircle.bearing(a, b) < PI / 2)
        assertTrue(GreatCircle.bearing(b, a) > 3 * PI / 2)
    }

    @Test
    fun noDirectionBetweenTheSamePoint() {
        assertEquals(0.0, GreatCircle.bearing(GeoPosition(10.0, 10.0), GeoPosition(10.0, 10.0)))
    }

    @Test
    fun theAltitudeOfAnEdgeGoesStraightFromOneEndToTheOther() {
        val points = GreatCircle.intermediatePoints(GeoPosition(0.0, 0.0, 100.0), GeoPosition(0.0, 40.0, 500.0), maxStepDegrees = 10.0)
        assertEquals(listOf(200.0, 300.0, 400.0), points.map { kotlin.math.round(it.altitude) })
    }
}
