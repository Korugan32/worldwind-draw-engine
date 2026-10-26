package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisvalingamTest {
    private fun p(lat: Double, lon: Double) = GeoPosition(lat, lon)

    @Test
    fun theResultHasExactlyTheAskedNumberOfPointsInIncreasingOrder() {
        val ring = List(100) { i ->
            val a = 2 * PI * i / 100
            p(5 * sin(a) + 0.1 * sin(9 * a), 5 * cos(a))
        }
        for (count in listOf(3, 10, 37, 99)) {
            val kept = Visvalingam.simplify(ring, isClosed = true, keepCount = count)
            assertEquals(count, kept.size)
            assertEquals(kept.sorted(), kept)
            assertEquals(kept.size, kept.toSet().size)
        }
    }

    @Test
    fun pointsOnAStraightLineGoFirstAndTheEndsOfAPathAlwaysStay() {
        val path = listOf(p(0.0, 0.0), p(0.0, 1.0), p(0.0, 2.0), p(3.0, 3.0), p(0.0, 4.0), p(0.0, 5.0))
        val kept = Visvalingam.simplify(path, isClosed = false, keepCount = 4)
        // 0,1,2 are collinear (the middle one costs nothing); the spike at 3 stays; the ends stay.
        assertTrue(0 in kept && 5 in kept && 3 in kept, "ends and the spike stay: $kept")
        assertEquals(4, kept.size)
        assertTrue(1 !in kept || 2 !in kept, "one of the collinear points is gone: $kept")
    }

    @Test
    fun aSquareWithPointsOnItsSidesKeepsItsCornersWhenReducedToFour() {
        val ring = listOf(
            p(0.0, 0.0), p(0.0, 1.0), p(0.0, 2.0),
            p(1.0, 2.0), p(2.0, 2.0),
            p(2.0, 1.0), p(2.0, 0.0),
            p(1.0, 0.0),
        )
        assertEquals(setOf(0, 2, 4, 6), Visvalingam.simplify(ring, isClosed = true, keepCount = 4).toSet())
    }

    @Test
    fun aBigFeatureOutlastsSmallWigglesWhenReducingHard() {
        // A circle of radius 5 with tiny wiggles and one big bump: reduced, the bump is what is left sticking out.
        val ring = List(200) { i ->
            val a = 2 * PI * i / 200
            val radius = 5.0 + 0.02 * sin(40 * a) + if (i == 100) 1.5 else 0.0
            p(radius * sin(a), radius * cos(a))
        }
        // 40 of 200 points: the smooth stretches are thinned to every 5th point or so, still far flatter than the bump.
        val kept = Visvalingam.simplify(ring, isClosed = true, keepCount = 40)
        assertTrue(100 in kept, "the bump survives: $kept")
    }

    @Test
    fun theMinimumIsKeptAndAskingForMoreThanThereIsGivesEverything() {
        val ring = List(10) { i -> p(sin(i * 0.6), cos(i * 0.6)) }
        assertEquals(3, Visvalingam.simplify(ring, isClosed = true, keepCount = 0).size, "a ring keeps 3")
        assertEquals(2, Visvalingam.simplify(ring, isClosed = false, keepCount = 0).size, "a path keeps its 2 ends")
        assertEquals(ring.indices.toList(), Visvalingam.simplify(ring, isClosed = true, keepCount = 50))
    }

    @Test
    fun theSameInputAlwaysGivesTheSameResult() {
        val ring = List(300) { i -> val a = 2 * PI * i / 300; p(3 * sin(a) + 0.2 * sin(13 * a), 3 * cos(a)) }
        assertEquals(Visvalingam.simplify(ring, true, 50), Visvalingam.simplify(ring, true, 50))
    }

    @Test
    fun aLongRingIsHandledQuickly() {
        // Small: the JavaScript test runner gives a test two seconds.
        val ring = List(4000) { i -> val a = 2 * PI * i / 4000; p(10 * sin(a) + 0.3 * sin(37 * a), 10 * cos(a)) }
        assertEquals(400, Visvalingam.simplify(ring, true, 400).size)
    }
}
