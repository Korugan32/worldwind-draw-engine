package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolygonPartsTest {
    private fun box(lat: Double, lon: Double, size: Double) = listOf(
        GeoPosition(lat, lon), GeoPosition(lat, lon + size), GeoPosition(lat + size, lon + size), GeoPosition(lat + size, lon),
    )

    @Test
    fun aSingleRingIsOnePartWithoutHoles() {
        val ring = box(0.0, 0.0, 10.0)
        assertEquals(listOf(listOf(ring)), PolygonParts.split(listOf(ring)))
    }

    @Test
    fun aRingInsideAnotherIsAHoleOfItsPart() {
        val outer = box(0.0, 0.0, 10.0)
        val hole = box(2.0, 2.0, 3.0)
        assertEquals(listOf(listOf(outer, hole)), PolygonParts.split(listOf(outer, hole)))
    }

    @Test
    fun ringsAwayFromEachOtherAreSeparatePartsInTheOrderOfTheirOutlines() {
        val a = box(0.0, 0.0, 10.0)
        val b = box(0.0, 30.0, 5.0)
        assertEquals(listOf(listOf(a), listOf(b)), PolygonParts.split(listOf(a, b)))
        assertEquals(listOf(listOf(b), listOf(a)), PolygonParts.split(listOf(b, a)))
    }

    @Test
    fun everyHoleGoesToThePartItIsIn() {
        val a = box(0.0, 0.0, 10.0)
        val b = box(0.0, 30.0, 10.0)
        val holeInA = box(2.0, 2.0, 3.0)
        val holeInB = box(2.0, 32.0, 3.0)
        val parts = PolygonParts.split(listOf(holeInB, a, holeInA, b)) // in no useful order
        assertEquals(listOf(listOf(a, holeInA), listOf(b, holeInB)), parts)
    }

    @Test
    fun anIslandInALakeIsAPartOfItsOwn() {
        val country = box(0.0, 0.0, 20.0)
        val lake = box(2.0, 2.0, 16.0)
        val island = box(7.0, 7.0, 6.0)
        assertEquals(listOf(listOf(country, lake), listOf(island)), PolygonParts.split(listOf(country, lake, island)))
    }

    @Test
    fun aHoleBelongsToTheInnermostRingAroundIt() {
        // A hole in the island, which is in the lake, which is in the country: the hole is the island's, not the country's.
        val country = box(0.0, 0.0, 30.0)
        val lake = box(2.0, 2.0, 26.0)
        val island = box(8.0, 8.0, 14.0)
        val pond = box(12.0, 12.0, 4.0)
        assertEquals(listOf(listOf(country, lake), listOf(island, pond)), PolygonParts.split(listOf(country, lake, island, pond)))
    }

    @Test
    fun ringsOfFewerThanThreePointsAreLeftOut() {
        val square = box(0.0, 0.0, 10.0)
        val line = listOf(GeoPosition(30.0, 30.0), GeoPosition(31.0, 31.0))
        assertEquals(listOf(listOf(square)), PolygonParts.split(listOf(square, line)))
        assertTrue(PolygonParts.split(listOf(line)).isEmpty())
        assertTrue(PolygonParts.split(emptyList()).isEmpty())
    }
}
