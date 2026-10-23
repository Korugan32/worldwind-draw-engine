package com.tcsappdev.worldwinddraw.core.geometry

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
}
