package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathSimplifierTest {
    private fun p(lat: Double, lon: Double) = GeoPosition(lat, lon)

    @Test
    fun aToleranceOfZeroKeepsEveryPoint() {
        val path = List(10) { p(0.0, it * 0.1) }
        assertEquals(path.indices.toList(), PathSimplifier.simplifyPath(path, 0.0))
        assertEquals(path.indices.toList(), PathSimplifier.simplifyRing(path, 0.0))
    }

    @Test
    fun pointsOnOneStraightLineCollapseToTheEnds() {
        val onTheEquator = List(20) { p(0.0, it * 0.05) }
        assertEquals(listOf(0, 19), PathSimplifier.simplifyPath(onTheEquator, 1.0))
        val onAMeridian = List(20) { p(it * 0.05, 10.0) }
        assertEquals(listOf(0, 19), PathSimplifier.simplifyPath(onAMeridian, 1.0))
    }

    @Test
    fun aBendLargerThanTheToleranceIsKeptAndASmallerOneIsDropped() {
        // About 0.9 degrees (100 km) off the straight line at the middle point.
        val path = listOf(p(0.0, 0.0), p(0.45, 1.0), p(0.0, 2.0))
        assertEquals(listOf(0, 1, 2), PathSimplifier.simplifyPath(path, 10_000.0))
        assertEquals(listOf(0, 2), PathSimplifier.simplifyPath(path, 200_000.0))
    }

    @Test
    fun theFirstAndLastPointOfAPathAreAlwaysKept() {
        val path = List(50) { p(it * 0.001, it * 0.001) }
        val kept = PathSimplifier.simplifyPath(path, 1_000_000.0)
        assertEquals(0, kept.first())
        assertEquals(49, kept.last())
    }

    @Test
    fun aSquareWithPointsOnItsSidesKeepsItsFourCorners() {
        val ring = listOf(
            p(0.0, 0.0), p(0.0, 1.0), p(0.0, 2.0),
            p(1.0, 2.0), p(2.0, 2.0),
            p(2.0, 1.0), p(2.0, 0.0),
            p(1.0, 0.0),
        )
        val kept = PathSimplifier.simplifyRing(ring, 1_000.0)
        assertEquals(setOf(0, 2, 4, 6), kept.toSet())
    }

    @Test
    fun aRingKeepsAtLeastThreePointsHoweverBigTheTolerance() {
        val circle = List(100) { i ->
            val a = 2 * PI * i / 100
            p(sin(a), cos(a))
        }
        val kept = PathSimplifier.simplifyRing(circle, 10_000_000.0)
        assertTrue(kept.size >= 3, "a ring needs 3 points to enclose anything, got $kept")
    }

    @Test
    fun aRingIsSimplifiedNoMatterWhichPointItStartsAt() {
        // A spike at the very first point must survive: the cut for the two halves starts from it.
        val ring = listOf(p(5.0, 0.0), p(0.0, -1.0), p(0.0, 0.0), p(0.0, 1.0))
        val kept = PathSimplifier.simplifyRing(ring, 1_000.0)
        assertTrue(0 in kept)
        val rotated = ring.drop(1) + ring.first() // the same ring, starting somewhere else
        val keptRotated = PathSimplifier.simplifyRing(rotated, 1_000.0)
        assertEquals(kept.size, keptRotated.size)
    }

    @Test
    fun theIndicesAreStrictlyIncreasingAndASubsetOfTheOriginals() {
        val ring = List(500) { i ->
            val a = 2 * PI * i / 500
            p(10 * sin(a) + 0.02 * sin(40 * a), 10 * cos(a))
        }
        val kept = PathSimplifier.simplifyRing(ring, 5_000.0)
        assertEquals(kept.sorted(), kept)
        assertEquals(kept.size, kept.toSet().size)
        assertTrue(kept.all { it in ring.indices })
    }

    @Test
    fun aBiggerToleranceNeverKeepsMorePoints() {
        val ring = List(2000) { i ->
            val a = 2 * PI * i / 2000
            p(20 * sin(a) + 0.5 * sin(25 * a), 20 * cos(a) + 0.3 * cos(17 * a))
        }
        val counts = listOf(100.0, 1_000.0, 10_000.0, 100_000.0, 1_000_000.0).map { PathSimplifier.simplifyRing(ring, it).size }
        assertEquals(counts.sortedDescending(), counts, "counts by growing tolerance: $counts")
        assertTrue(counts.first() > counts.last())
    }

    @Test
    fun everyDroppedPointIsWithinTheToleranceOfTheSimplifiedPath() {
        // Small on purpose: the check below is brute force, and the JavaScript test runner gives a test 2 seconds.
        val path = List(80) { i -> p(0.3 * sin(i * 0.2) + i * 0.02, i * 0.05) }
        val tolerance = 20_000.0
        val kept = PathSimplifier.simplifyPath(path, tolerance)
        val keptPoints = kept.map { path[it] }
        // Re-measure with an independent method: the distance to the nearest of a sampling of the simplified outline, a point
        // every 0.02 degrees (about 2 km), which is as close as the nearest sample can be off by 1.2 km.
        val outline = keptPoints.zipWithNext().flatMap { (a, b) -> listOf(a) + GreatCircle.intermediatePoints(a, b, 0.02) } + keptPoints.last()
        path.forEach { point ->
            val nearest = outline.minOf { haversineMeters(point, it) }
            assertTrue(nearest <= tolerance * 1.05 + 1_200.0, "a dropped point is $nearest m from the outline (tolerance $tolerance)")
        }
    }

    @Test
    fun itWorksNearThePolesWhereLongitudeDegreesAreTiny() {
        // At latitude 89.9 a degree of longitude is only about 190 m long: a ring of 100 of them is a tiny circle.
        val ring = List(36) { i -> p(89.9, i * 10.0 - 180.0) }
        val coarse = PathSimplifier.simplifyRing(ring, 50_000.0)
        assertEquals(3, coarse.size, "the whole ring is inside a 50 km tolerance: it is down to the minimum")
        val fine = PathSimplifier.simplifyRing(ring, 1.0)
        assertTrue(fine.size > 30, "with a 1 m tolerance nearly every point matters, kept ${fine.size}")
    }

    private fun haversineMeters(a: GeoPosition, b: GeoPosition): Double {
        val lat1 = a.latitude * PI / 180
        val lat2 = b.latitude * PI / 180
        val dLat = lat2 - lat1
        val dLon = (b.longitude - a.longitude) * PI / 180
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * PathSimplifier.EARTH_RADIUS_METERS * kotlin.math.asin(kotlin.math.sqrt(h))
    }
}
