package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.abs
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OutlineWalkTest {
    // Three corners round the equator, 120 degrees apart: every edge is 120 degrees long, so the places are easy to say.
    private val equator = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 120.0), GeoPosition(0.0, -120.0))

    private fun assertNear(expected: GeoPosition, actual: GeoPosition, message: String = "") {
        assertTrue(abs(expected.latitude - actual.latitude) < 1e-9, "$message latitude ${actual.latitude}, expected ${expected.latitude}")
        assertTrue(abs(expected.longitude - actual.longitude) < 1e-9, "$message longitude ${actual.longitude}, expected ${expected.longitude}")
    }

    @Test
    fun theLengthIsTheSumOfTheEdgesAndTheWayRoundTheEquatorIsAFullCircle() {
        assertTrue(abs(OutlineWalk(equator).length - 2 * PI) < 1e-9)
    }

    @Test
    fun thePenIsAtTheStartAtZeroAndBackAtItAtOne() {
        val walk = OutlineWalk(equator)
        assertEquals(equator[0], walk.pointAt(0.0))
        assertEquals(equator[0], walk.pointAt(1.0))
    }

    @Test
    fun aThirdOfTheWayRoundIsTheSecondCornerAndASixthIsHalfWayToIt() {
        val walk = OutlineWalk(equator)
        assertNear(GeoPosition(0.0, 120.0), walk.pointAt(1.0 / 3))
        assertNear(GeoPosition(0.0, 60.0), walk.pointAt(1.0 / 6))
    }

    @Test
    fun theCutLiesOnTheDrawnArcNotOnTheStraightLineInLatitudeAndLongitude() {
        // Two points on the 45th parallel: the great circle between them goes north of the parallel, so half way is further north than 45.
        val walk = OutlineWalk(listOf(GeoPosition(45.0, 0.0), GeoPosition(45.0, 90.0), GeoPosition(60.0, 45.0)))
        val middleOfTheFirstEdge = walk.pathTo(GreatCircle.angleBetween(GeoPosition(45.0, 0.0), GeoPosition(45.0, 90.0)) / 2 / walk.length).last()
        assertTrue(middleOfTheFirstEdge.latitude > 50.0, "latitude was ${middleOfTheFirstEdge.latitude}")
    }

    @Test
    fun theDrawnPartIsTheCornersPassedThenWhereThePenIs() {
        val walk = OutlineWalk(equator)
        assertEquals(listOf(equator[0]), walk.pathTo(0.0))
        val path = walk.pathTo(0.5)
        assertEquals(listOf(equator[0], equator[1]), path.take(2))
        assertEquals(3, path.size)
        assertNear(GeoPosition(0.0, 180.0).let { GeoPosition(0.0, if (path.last().longitude < 0) -180.0 else 180.0) }, path.last())
    }

    @Test
    fun atOneTheWholeOutlineIsDrawnAndItEndsOnTheStartExactly() {
        val path = OutlineWalk(equator).pathTo(1.0)
        assertEquals(equator + equator[0], path)
    }

    @Test
    fun exactlyOnACornerThePathEndsOnThatCornerWithNothingAfterIt() {
        val path = OutlineWalk(equator).pathTo(1.0 / 3)
        assertEquals(2, path.size)
        assertNear(equator[1], path.last())
    }

    @Test
    fun whatIsDrawnOnlyEverGrows() {
        val walk = OutlineWalk(listOf(GeoPosition(10.0, 20.0), GeoPosition(30.0, 25.0), GeoPosition(28.0, 50.0), GeoPosition(5.0, 45.0)))
        var before = walk.pathTo(0.0)
        for (i in 1..100) {
            val now = walk.pathTo(i / 100.0)
            assertTrue(now.size >= before.size)
            assertEquals(before.dropLast(1), now.take(before.size - 1), "the corners already passed do not change at step $i")
            before = now
        }
    }

    @Test
    fun startingAtAnotherCornerStartsThereAndComesBackToIt() {
        val walk = OutlineWalk(equator, startIndex = 1)
        assertEquals(equator[1], walk.pointAt(0.0))
        assertEquals(listOf(equator[1], equator[2], equator[0], equator[1]), walk.pathTo(1.0))
    }

    @Test
    fun goingTheOtherWayRoundVisitsTheCornersInReverse() {
        val walk = OutlineWalk(equator, startIndex = 0, reversed = true)
        assertEquals(listOf(equator[0], equator[2], equator[1], equator[0]), walk.pathTo(1.0))
    }

    @Test
    fun reversedKeepsTheSameStartingCorner() {
        for (start in equator.indices) {
            assertEquals(equator[start], OutlineWalk(equator, start, reversed = true).pointAt(0.0), "start $start")
        }
    }

    @Test
    fun outsideZeroToOneThePenStopsAtTheEnds() {
        val walk = OutlineWalk(equator)
        assertEquals(walk.pathTo(0.0), walk.pathTo(-3.0))
        assertEquals(walk.pathTo(1.0), walk.pathTo(7.0))
    }

    @Test
    fun aRepeatedCornerIsAnEdgeOfNoLengthAndDoesNotStopThePen() {
        val walk = OutlineWalk(listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 0.0), GeoPosition(0.0, 90.0), GeoPosition(45.0, 45.0)))
        val path = walk.pathTo(0.2)
        assertTrue(path.last().longitude > 0.0)
        assertTrue(abs(walk.pointAt(0.2).longitude - path.last().longitude) < 1e-9)
    }

    @Test
    fun aRingWithNoLengthStaysAtItsStart() {
        val walk = OutlineWalk(listOf(GeoPosition(10.0, 10.0), GeoPosition(10.0, 10.0), GeoPosition(10.0, 10.0)))
        assertEquals(GeoPosition(10.0, 10.0), walk.pointAt(0.5))
    }

    @Test
    fun whatCannotBeWalkedIsRefused() {
        assertFailsWith<IllegalArgumentException> { OutlineWalk(listOf(GeoPosition(0.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { OutlineWalk(equator, startIndex = 3) }
        assertFailsWith<IllegalArgumentException> { OutlineWalk(equator).pointAt(Double.NaN) }
    }

    private val bent = listOf(GeoPosition(10.0, 20.0), GeoPosition(30.0, 25.0), GeoPosition(28.0, 50.0), GeoPosition(5.0, 45.0))

    @Test
    fun aStretchFromTheStartIsWhatThePathToItsEndIs() {
        val walk = OutlineWalk(bent)
        for (to in listOf(0.1, 0.33, 0.5, 0.9, 1.0)) assertEquals(walk.pathTo(to), walk.pathBetween(0.0, to), "to $to")
    }

    @Test
    fun aStretchBeginsAndEndsWhereThePenWas() {
        val walk = OutlineWalk(bent)
        val stretch = walk.pathBetween(0.2, 0.7)
        assertNear(walk.pointAt(0.2), stretch.first())
        assertNear(walk.pointAt(0.7), stretch.last())
    }

    @Test
    fun stretchesThatFollowEachOtherJoinUpAndTogetherAreThePath() {
        val walk = OutlineWalk(bent)
        val cuts = listOf(0.0, 0.15, 0.4, 0.4001, 0.75, 1.0)
        val stretches = cuts.zipWithNext { a, b -> walk.pathBetween(a, b) }
        stretches.zipWithNext { before, after -> assertNear(before.last(), after.first()) }
        val joined = stretches.flatMapIndexed { i, s -> if (i == 0) s else s.drop(1) }
        assertNear(bent.first(), joined.first())
        assertNear(bent.first(), joined.last())
        for (corner in bent) assertTrue(joined.any { abs(it.latitude - corner.latitude) < 1e-9 && abs(it.longitude - corner.longitude) < 1e-9 }, "corner $corner is missing")
    }

    @Test
    fun aStretchWithinOneEdgeIsTwoPointsAndNoStretchIsOnePoint() {
        val walk = OutlineWalk(equator)
        assertEquals(2, walk.pathBetween(0.05, 0.1).size)
        assertEquals(1, walk.pathBetween(0.5, 0.5).size)
        assertEquals(1, walk.pathBetween(0.6, 0.2).size)
    }

    // --- a route: open, from the first point to the last ---

    private val route = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0), GeoPosition(30.0, 40.0))

    @Test
    fun aRouteEndsAtItsLastPointAndDoesNotComeBack() {
        val walk = OutlineWalk(route, closed = false)
        assertEquals(route, walk.pathTo(1.0))
        assertEquals(route.last(), walk.pointAt(1.0))
        assertEquals(route.first(), walk.pointAt(0.0))
        assertTrue(abs(walk.length - (GreatCircle.angleBetween(route[0], route[1]) + GreatCircle.angleBetween(route[1], route[2]))) < 1e-9)
    }

    @Test
    fun aRouteWalkedBackwardsStartsAtTheOtherEnd() {
        val walk = OutlineWalk(route, reversed = true, closed = false)
        assertEquals(route.reversed(), walk.pathTo(1.0))
    }

    @Test
    fun aRouteHasNoOtherStartThanItsFirstPoint() {
        assertFailsWith<IllegalArgumentException> { OutlineWalk(route, startIndex = 1, closed = false) }
        assertEquals(2, OutlineWalk(listOf(route[0], route[1]), closed = false).pathTo(1.0).size)
    }

    @Test
    fun thePenGoesInTheDirectionOfTheEdgeItIsOn() {
        val walk = OutlineWalk(route, closed = false)
        val firstEdge = walk.length.let { GreatCircle.angleBetween(route[0], route[1]) / it }
        assertTrue(abs(walk.bearingAt(firstEdge / 2) - PI / 2) < 1e-9, "east along the equator")
        assertTrue(abs(walk.bearingAt(0.0) - PI / 2) < 1e-9)
        assertTrue(walk.bearingAt((1 + firstEdge) / 2) < 0.5, "turned to the north on the second edge: ${walk.bearingAt((1 + firstEdge) / 2)}")
    }

    @Test
    fun atTheEndThePenIsGoingTheWayItArrivedIn() {
        val walk = OutlineWalk(route, closed = false)
        val arrived = GreatCircle.bearing(GreatCircle.pointAt(route[1], route[2], 0.999), route[2])
        val apart = abs(walk.bearingAt(1.0) - arrived).let { minOf(it, 2 * PI - it) } // 0 and 2 PI are the same way
        assertTrue(apart < 0.05, "was ${walk.bearingAt(1.0)}, arrived $arrived")
    }

    @Test
    fun theDirectionAlongALongEdgeChanges() {
        val walk = OutlineWalk(listOf(GeoPosition(45.0, 0.0), GeoPosition(45.0, 90.0)), closed = false)
        assertTrue(walk.bearingAt(0.0) < walk.bearingAt(0.5) && walk.bearingAt(0.5) < walk.bearingAt(0.999))
    }

    @Test
    fun theFractionAtACornerIsHowFarRoundItIs() {
        val walk = OutlineWalk(route, closed = false)
        assertEquals(0.0, walk.fractionAtCorner(0))
        assertEquals(1.0, walk.fractionAtCorner(2))
        assertTrue(abs(walk.pointAt(walk.fractionAtCorner(1)).longitude - 40.0) < 1e-6)
        assertFailsWith<IllegalArgumentException> { walk.fractionAtCorner(3) }
    }
}
