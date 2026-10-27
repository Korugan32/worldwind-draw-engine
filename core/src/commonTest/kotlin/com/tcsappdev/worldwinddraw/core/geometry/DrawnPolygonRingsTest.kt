package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DrawnPolygonRingsTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))

    private fun ring(prefix: String, count: Int = 4) =
        (0 until count).map { Vertex(VertexId("$prefix$it"), GeoPosition(it.toDouble(), prefix.length.toDouble())) }

    private fun polygon(vararg extra: List<Vertex>) = DrawnPolygon(EntityId("P"), style, ring("a"), extraRings = extra.toList())

    private fun DrawnPolygon.ids() = rings.map { ring -> ring.map { it.id.id } }

    @Test
    fun aPolygonWithoutExtraRingsHasOneRingWhichIsItsVertices() {
        val plain = DrawnPolygon(EntityId("P"), style, ring("a"))
        assertEquals(1, plain.rings.size)
        assertEquals(plain.vertices, plain.rings.single())
        assertEquals(emptyList(), plain.extraRings)
    }

    @Test
    fun theFirstRingIsTheVerticesAndTheOthersFollowInOrder() {
        val polygon = polygon(ring("bb"), ring("ccc"))
        assertEquals(listOf(ring("a"), ring("bb"), ring("ccc")), polygon.rings)
        assertSame(polygon.vertices, polygon.rings[0])
    }

    @Test
    fun everyRingNeedsThreeVerticesAndNoIdMayAppearTwice() {
        assertFailsWith<IllegalArgumentException> { polygon(ring("bb", count = 2)) }
        assertFailsWith<IllegalArgumentException> { polygon(ring("a")) } // the same ids as the first ring
        assertFailsWith<IllegalArgumentException> { polygon(ring("bb"), ring("bb")) }
        polygon(ring("bb"), ring("ccc")) // distinct ids are fine
    }

    @Test
    fun aVertexIsFoundInItsRing() {
        val polygon = polygon(ring("bb"), ring("ccc"))
        assertEquals(0, polygon.ringIndexOf(VertexId("a3")))
        assertEquals(1, polygon.ringIndexOf(VertexId("bb0")))
        assertEquals(2, polygon.ringIndexOf(VertexId("ccc2")))
        assertEquals(-1, polygon.ringIndexOf(VertexId("nope")))
    }

    @Test
    fun withRingReplacesOnlyThatRing() {
        val polygon = polygon(ring("bb"), ring("ccc"))
        val replacement = ring("bb", count = 5)
        assertEquals(listOf(ring("a"), replacement, ring("ccc")), polygon.withRing(1, replacement).rings)
        val first = ring("a", count = 6)
        assertEquals(listOf(first, ring("bb"), ring("ccc")), polygon.withRing(0, first).rings)
        assertFailsWith<IllegalArgumentException> { polygon.withRing(3, replacement) }
    }

    @Test
    fun mapRingsTransformsEveryRingAndKeepsTheOrder() {
        val polygon = polygon(ring("bb"))
        val dropped = polygon.mapRings { it.drop(1) }
        assertEquals(listOf(listOf("a1", "a2", "a3"), listOf("bb1", "bb2", "bb3")), dropped.ids())
    }

    @Test
    fun anExtraRingIsAddedAtTheEnd() {
        assertEquals(listOf(ring("a"), ring("bb")), polygon().withExtraRing(ring("bb")).rings)
    }

    @Test
    fun removingARingAndInsertingItBackGivesTheSamePolygonForAnyPlace() {
        val polygon = polygon(ring("bb"), ring("ccc"))
        for (index in 0..2) {
            val removed = polygon.rings[index]
            val without = polygon.withoutRing(index)
            assertEquals(2, without.rings.size)
            assertEquals(polygon, without.withRingInserted(index, removed), "ring $index")
        }
    }

    @Test
    fun whenTheFirstRingGoesTheNextOneBecomesTheFirst() {
        val without = polygon(ring("bb")).withoutRing(0)
        assertEquals(ring("bb"), without.vertices)
        assertEquals(emptyList(), without.extraRings)
    }

    @Test
    fun aPolygonCannotLoseItsOnlyRing() {
        assertFailsWith<IllegalArgumentException> { polygon().withoutRing(0) }
    }
}
