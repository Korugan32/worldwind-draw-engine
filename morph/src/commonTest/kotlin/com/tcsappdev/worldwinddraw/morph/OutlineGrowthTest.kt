package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineGrowthTest {
    // an outline with one corner of its own (x1) between c2 and c3, and the outline it becomes, which has two others (n1, n2) there
    private val before = listOf(vertex("c1", 0.0, 0.0), vertex("c2", 0.0, 10.0), vertex("x1", 5.0, 12.0), vertex("c3", 10.0, 10.0), vertex("c4", 10.0, 0.0))
    private val after = listOf(vertex("c1", 0.0, 0.0), vertex("c2", 0.0, 10.0), vertex("n1", 0.0, 20.0), vertex("n2", 10.0, 20.0), vertex("c3", 10.0, 10.0), vertex("c4", 10.0, 0.0))

    private fun List<Slot>.at(t: Float) = map { it.at(t) }

    /** How far [p] is from the closed outline [ring]. */
    private fun distanceToOutline(p: GeoPosition, ring: List<Vertex>): Double = ring.indices.minOf { i ->
        val a = ring[i].position
        val b = ring[(i + 1) % ring.size].position
        val dx = b.longitude - a.longitude
        val dy = b.latitude - a.latitude
        val along = if (dx == 0.0 && dy == 0.0) 0.0 else (((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        hypot(p.latitude - (a.latitude + dy * along), p.longitude - (a.longitude + dx * along))
    }

    @Test
    fun everyCornerOfEitherOutlineGetsAPlaceInTheOneInBetween() {
        val slots = assertNotNull(alignOutlines(before, after))
        assertEquals(setOf("c1", "c2", "c3", "c4", "x1", "n1", "n2"), slots.map { it.vertex.id.id }.toSet())
        assertEquals(7, slots.size)
    }

    @Test
    fun theOldCornersStartWhereTheyWereAndTheNewOnesEndWhereTheyAre() {
        val slots = assertNotNull(alignOutlines(before, after))
        for (corner in before) assertEquals(corner.position, slots.first { it.vertex.id == corner.id }.from)
        for (corner in after) assertEquals(corner.position, slots.first { it.vertex.id == corner.id }.to)
    }

    @Test
    fun theOutlineStartsAsExactlyTheOldOneAndEndsAsExactlyTheNewOne() {
        val slots = assertNotNull(alignOutlines(before, after))
        for (slot in slots) {
            assertEquals(0.0, distanceToOutline(slot.at(0f).position, before), 1e-9, "${slot.vertex.id} at the start")
            assertEquals(0.0, distanceToOutline(slot.at(1f).position, after), 1e-9, "${slot.vertex.id} at the end")
        }
    }

    @Test
    fun theCornersComeOutInTheOrderAroundTheOutline() {
        val ids = assertNotNull(alignOutlines(before, after)).map { it.vertex.id.id }
        assertEquals(listOf("c1", "c2"), ids.take(2))
        assertEquals(listOf("c3", "c4"), ids.takeLast(2))
        assertEquals(setOf("x1", "n1", "n2"), ids.subList(2, 5).toSet()) // all the changed ones between c2 and c3
    }

    @Test
    fun theCornersAreInTheOrderOfHowFarAlongTheirOwnStretchTheyAreWhicheverOutlineTheyAreFrom() {
        // x1 is half way along the old stretch c2-x1-c3, n1 a third and n2 two thirds along the new one: n1, x1, n2
        assertEquals(listOf("c1", "c2", "n1", "x1", "n2", "c3", "c4"), assertNotNull(alignOutlines(before, after)).map { it.vertex.id.id })
    }

    @Test
    fun aCornerInBothOutlinesGoesFromWhereItWasToWhereItIs() {
        val moved = after.map { if (it.id.id == "c2") vertex("c2", 3.0, 8.0) else it }
        val c2 = assertNotNull(alignOutlines(before, moved)).first { it.vertex.id.id == "c2" }
        assertEquals(GeoPosition(0.0, 10.0), c2.from)
        assertEquals(GeoPosition(3.0, 8.0), c2.to)
    }

    @Test
    fun theTagsOfACornerInBothOutlinesChangeAtTheHalfWayPoint() {
        val tagged = before.map { if (it.id.id == "c1") it.copy(metadata = mapOf("k" to "old")) else it }
        val retagged = after.map { if (it.id.id == "c1") it.copy(metadata = mapOf("k" to "new")) else it }
        val c1 = assertNotNull(alignOutlines(tagged, retagged)).first { it.vertex.id.id == "c1" }
        assertEquals(mapOf("k" to "old"), c1.at(0.25f).metadata)
        assertEquals(mapOf("k" to "new"), c1.at(0.75f).metadata)
    }

    @Test
    fun anEdgeAcrossTheAntimeridianIsLaidOverTheShortWay() {
        val old = listOf(vertex("c1", 0.0, 170.0), vertex("c2", 0.0, -170.0), vertex("c3", 10.0, -170.0), vertex("c4", 10.0, 170.0))
        val new = listOf(vertex("c1", 0.0, 170.0), vertex("n1", -5.0, -175.0), vertex("c2", 0.0, -170.0), vertex("c3", 10.0, -170.0), vertex("c4", 10.0, 170.0))
        val n1 = assertNotNull(alignOutlines(old, new)).first { it.vertex.id.id == "n1" }.from
        // the new stretch c1-n1-c2 has legs of hypot(5, 15) and hypot(5, 5); the old edge is 20 degrees across the antimeridian
        val share = hypot(5.0, 15.0) / (hypot(5.0, 15.0) + hypot(5.0, 5.0))
        assertEquals(0.0, n1.latitude, 1e-9)
        assertEquals(170.0 + 20.0 * share - 360.0, n1.longitude, 1e-9)
    }

    @Test
    fun theOtherOutlineMayGoRoundTheOtherWayOrStartAtAnotherCorner() {
        val expected = assertNotNull(alignOutlines(before, after)).map { it.vertex.id.id }.toSet()
        assertEquals(expected, assertNotNull(alignOutlines(before, after.reversed())).map { it.vertex.id.id }.toSet())
        val rotated = after.drop(3) + after.take(3)
        assertEquals(expected, assertNotNull(alignOutlines(before, rotated)).map { it.vertex.id.id }.toSet())
        // and it is the same outline whichever way it was written
        for (slot in assertNotNull(alignOutlines(before, after.reversed()))) assertEquals(0.0, distanceToOutline(slot.at(0f).position, before), 1e-9)
    }

    @Test
    fun fewerThanThreeCornersInCommonCannotBeAligned() {
        assertNull(alignOutlines(before, listOf(vertex("c1", 0.0, 0.0), vertex("c2", 0.0, 10.0), vertex("z1", 9.0, 9.0), vertex("z2", 1.0, 9.0))))
    }

    @Test
    fun commonCornersInADifferentOrderCannotBeAligned() {
        val swapped = listOf(vertex("c1", 0.0, 0.0), vertex("c3", 10.0, 10.0), vertex("c2", 0.0, 10.0), vertex("c4", 10.0, 0.0))
        assertNull(alignOutlines(before, swapped))
    }

    @Test
    fun aRingWithTheSameIdTwiceCannotBeAligned() {
        val twice = before + vertex("c1", 3.0, 3.0)
        assertNull(alignOutlines(twice, after))
        assertNull(alignOutlines(before, twice))
        // also when it is a corner that only one of them has
        val onlyInOne = before + vertex("x1", 7.0, 7.0)
        assertNull(alignOutlines(onlyInOne, after))
        assertNull(alignOutlines(before, after + vertex("n1", 7.0, 7.0)))
    }

    @Test
    fun anOutlineThatDidNotChangeAlignsToItself() {
        val slots = assertNotNull(alignOutlines(before, before))
        assertEquals(before.map { it.id }, slots.map { it.vertex.id })
        assertTrue(slots.all { it.from == it.to })
    }

    @Test
    fun theCornersOfTheNewStretchAreSpacedByItsLengthNotByHowManyThereAre() {
        // new stretch c2 - n1 - n2 - c3 has legs of 10, 10 and 10, but make n2 very close to c3: the old edge c2-c3 is 10 long
        val to = listOf(vertex("c1", 0.0, 0.0), vertex("c2", 0.0, 10.0), vertex("n1", 0.0, 20.0), vertex("n2", 9.0, 20.0), vertex("c3", 10.0, 10.0), vertex("c4", 10.0, 0.0))
        val slots = assertNotNull(alignOutlines(before.filter { it.id.id != "x1" }, to))
        val n1 = slots.first { it.vertex.id.id == "n1" }.from
        val n2 = slots.first { it.vertex.id.id == "n2" }.from
        // legs: 10, 9, hypot(1, 10): n1 is 10 / 29.05 and n2 is 19 / 29.05 along the 10 long old edge
        val total = 10.0 + 9.0 + hypot(1.0, 10.0)
        assertEquals(10.0 * 10.0 / total, n1.latitude, 1e-9)
        assertEquals(10.0 * 19.0 / total, n2.latitude, 1e-9)
    }
}
