package com.tcsappdev.worldwinddraw.booleanops

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolygonOverlayTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    // Longitude is x and latitude is y: a square's corner (x, y) is v(id, lat = y, lon = x).
    private fun v(id: String, x: Double, y: Double, alt: Double = 0.0, meta: Map<String, String> = emptyMap()) =
        Vertex(VertexId(id), GeoPosition(latitude = y, longitude = x, altitude = alt), meta)

    private fun square(id: String, x: Double, y: Double, side: Double, vertexPrefix: String = id) = DrawnPolygon(
        EntityId(id), style,
        listOf(v("$vertexPrefix-sw", x, y), v("$vertexPrefix-se", x + side, y), v("$vertexPrefix-ne", x + side, y + side), v("$vertexPrefix-nw", x, y + side)),
    )

    private fun counter(): () -> VertexId {
        var n = 0
        return { VertexId("new${++n}") }
    }

    private fun area(ring: List<Vertex>): Double {
        var sum = 0.0
        for (i in ring.indices) {
            val p = ring[i].position
            val q = ring[(i + 1) % ring.size].position
            sum += p.longitude * q.latitude - q.longitude * p.latitude
        }
        return abs(sum) / 2
    }

    // The area of what the rings enclose (parts minus their holes), by the library's own way of telling parts from holes.
    private fun totalArea(rings: List<List<Vertex>>): Double =
        PolygonParts.split(rings.map { ring -> ring.map { it.position } }).sumOf { part ->
            val positions = part.map { ring -> ring.mapIndexed { i, p -> Vertex(VertexId("$i"), p) } }
            area(positions.first()) - positions.drop(1).sumOf { area(it) }
        }

    private fun partsOf(rings: List<List<Vertex>>) = PolygonParts.split(rings.map { ring -> ring.map { it.position } })

    private fun idsOf(rings: List<List<Vertex>>) = rings.flatMap { ring -> ring.map { it.id.id } }

    // ---- the three operations ----

    @Test
    fun theUnionOfTwoOverlappingSquaresIsOneRingOfTheRightArea() {
        val rings = PolygonOverlay.union(square("a", 0.0, 0.0, 10.0), square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        assertEquals(1, rings.size)
        assertEquals(175.0, totalArea(rings), 1e-9)
    }

    @Test
    fun theDifferenceOfTwoOverlappingSquaresIsAnL() {
        val rings = PolygonOverlay.difference(square("a", 0.0, 0.0, 10.0), square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        assertEquals(1, rings.size)
        assertEquals(6, rings.single().size)
        assertEquals(75.0, totalArea(rings), 1e-9)
    }

    @Test
    fun theIntersectionOfTwoOverlappingSquaresIsTheSquareTheyShare() {
        val rings = PolygonOverlay.intersection(square("a", 0.0, 0.0, 10.0), square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        assertEquals(1, rings.size)
        assertEquals(4, rings.single().size)
        assertEquals(25.0, totalArea(rings), 1e-9)
    }

    @Test
    fun shapesThatDoNotTouchHaveNoIntersectionAndTheirUnionIsBothRings() {
        val a = square("a", 0.0, 0.0, 10.0)
        val b = square("b", 20.0, 0.0, 10.0)
        assertEquals(emptyList(), PolygonOverlay.intersection(a, b))
        assertEquals(2, PolygonOverlay.union(a, b, newVertexId = counter()).size)
        assertEquals(1, PolygonOverlay.difference(a, b, newVertexId = counter()).size)
    }

    @Test
    fun shapesThatOnlyTouchHaveNoIntersectionAsAShape() {
        // they share an edge: the intersection is a line, which is not a shape
        assertEquals(emptyList(), PolygonOverlay.intersection(square("a", 0.0, 0.0, 10.0), square("b", 10.0, 0.0, 10.0)))
    }

    @Test
    fun aShapeMinusItselfIsNothingAndMinusAShapeAroundItIsNothing() {
        val a = square("a", 0.0, 0.0, 10.0)
        assertEquals(emptyList(), PolygonOverlay.difference(a, a))
        assertEquals(emptyList(), PolygonOverlay.difference(a, square("big", -5.0, -5.0, 30.0)))
    }

    @Test
    fun aDifferenceInsideAShapeMakesAHole() {
        val rings = PolygonOverlay.difference(square("a", 0.0, 0.0, 30.0), square("b", 10.0, 10.0, 10.0), newVertexId = counter())
        assertEquals(2, rings.size)
        assertEquals(800.0, totalArea(rings), 1e-9)
        assertEquals(1, partsOf(rings).size)
        assertEquals(2, partsOf(rings).single().size) // the outline and one hole
    }

    @Test
    fun aDifferenceThatCutsAShapeInTwoGivesTwoParts() {
        val bar = square("a", 0.0, 0.0, 30.0)
        val cutter = DrawnPolygon(EntityId("c"), style, listOf(v("c1", 10.0, -5.0), v("c2", 20.0, -5.0), v("c3", 20.0, 35.0), v("c4", 10.0, 35.0)))
        val rings = PolygonOverlay.difference(bar, cutter, newVertexId = counter())
        assertEquals(2, rings.size)
        assertEquals(2, partsOf(rings).size)
        assertEquals(600.0, totalArea(rings), 1e-9)
    }

    // ---- shapes of several rings ----

    @Test
    fun aShapeWithAHoleKeepsItInAUnionWithAShapeThatTouchesIt() {
        val holed = DrawnPolygon(
            EntityId("h"), style,
            listOf(v("h1", 0.0, 0.0), v("h2", 30.0, 0.0), v("h3", 30.0, 30.0), v("h4", 0.0, 30.0)),
            extraRings = listOf(listOf(v("i1", 10.0, 10.0), v("i2", 20.0, 10.0), v("i3", 20.0, 20.0), v("i4", 10.0, 20.0))),
        )
        val rings = PolygonOverlay.union(holed, square("b", 25.0, 25.0, 20.0), newVertexId = counter())
        assertEquals(2, rings.size)
        assertEquals(800.0 + 400.0 - 25.0, totalArea(rings), 1e-9)
    }

    @Test
    fun aShapeThatFillsAHoleClosesIt() {
        val holed = DrawnPolygon(
            EntityId("h"), style,
            listOf(v("h1", 0.0, 0.0), v("h2", 30.0, 0.0), v("h3", 30.0, 30.0), v("h4", 0.0, 30.0)),
            extraRings = listOf(listOf(v("i1", 10.0, 10.0), v("i2", 20.0, 10.0), v("i3", 20.0, 20.0), v("i4", 10.0, 20.0))),
        )
        val rings = PolygonOverlay.union(holed, square("plug", 8.0, 8.0, 14.0), newVertexId = counter())
        assertEquals(1, rings.size)
        assertEquals(900.0, totalArea(rings), 1e-9)
    }

    @Test
    fun anIslandInALakeIsAPartOfItsOwnAndSurvivesTheOverlay() {
        val country = listOf(v("c1", 0.0, 0.0), v("c2", 80.0, 0.0), v("c3", 80.0, 80.0), v("c4", 0.0, 80.0))
        val lake = listOf(v("l1", 10.0, 10.0), v("l2", 70.0, 10.0), v("l3", 70.0, 70.0), v("l4", 10.0, 70.0))
        val island = listOf(v("s1", 30.0, 30.0), v("s2", 50.0, 30.0), v("s3", 50.0, 50.0), v("s4", 30.0, 50.0))
        val shape = DrawnPolygon(EntityId("g"), style, country, extraRings = listOf(lake, island))
        val rings = PolygonOverlay.union(shape, square("far", 100.0, 0.0, 10.0), newVertexId = counter())
        assertEquals(4, rings.size)
        assertEquals(3, partsOf(rings).size) // the country with the lake, the island, the far square
        assertEquals(6400.0 - 3600.0 + 400.0 + 100.0, totalArea(rings), 1e-9)
    }

    // ---- the vertices ----

    @Test
    fun aVertexThatWasNotMovedKeepsItsIdItsMetadataAndItsExactPosition() {
        val a = DrawnPolygon(
            EntityId("a"), style,
            listOf(v("a1", 0.123456789, 0.987654321, alt = 42.0, meta = mapOf("name" to "corner")), v("a2", 10.0, 0.0), v("a3", 10.0, 10.0), v("a4", 0.0, 10.0)),
        )
        val rings = PolygonOverlay.union(a, square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        val kept = rings.flatten().single { it.id.id == "a1" }
        assertEquals(GeoPosition(latitude = 0.987654321, longitude = 0.123456789, altitude = 42.0), kept.position)
        assertEquals(mapOf("name" to "corner"), kept.metadata)
    }

    @Test
    fun theVerticesOfBThatEndUpOnTheOutlineKeepTheirIdsToo() {
        val rings = PolygonOverlay.union(square("a", 0.0, 0.0, 10.0), square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        val ids = idsOf(rings)
        assertTrue("b-ne" in ids && "b-nw" in ids && "b-se" in ids, ids.toString())
        assertTrue("b-sw" !in ids) // inside a: not on the outline any more
        assertTrue("a-sw" in ids && "a-se" in ids && "a-nw" in ids)
        assertTrue("a-ne" !in ids)
    }

    @Test
    fun whereTheOutlinesCrossTheVerticesAreNewAndTheirIdsComeFromTheCaller() {
        val rings = PolygonOverlay.union(square("a", 0.0, 0.0, 10.0), square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        val fresh = rings.flatten().filter { it.id.id.startsWith("new") }
        assertEquals(2, fresh.size)
        assertEquals(setOf(GeoPosition(10.0, 5.0), GeoPosition(5.0, 10.0)), fresh.map { it.position }.toSet())
    }

    @Test
    fun whenBothHaveAVertexAtTheSamePlaceTheOneOfAWins() {
        val a = DrawnPolygon(EntityId("a"), style, listOf(v("a1", 0.0, 0.0), v("a2", 10.0, 0.0), v("a3", 10.0, 10.0), v("a4", 0.0, 10.0)))
        val b = DrawnPolygon(EntityId("b"), style, listOf(v("b1", 10.0, 0.0), v("b2", 20.0, 0.0), v("b3", 20.0, 10.0), v("b4", 10.0, 10.0)))
        val ids = idsOf(PolygonOverlay.union(a, b, newVertexId = counter()))
        assertTrue("a2" in ids && "a3" in ids)
        assertTrue("b1" !in ids && "b4" !in ids)
    }

    @Test
    fun noTwoVerticesOfAResultShareAnIdEvenIfTheInputsReuseOne() {
        // the same vertex ids in both shapes, at different places
        val a = DrawnPolygon(EntityId("a"), style, listOf(v("v1", 0.0, 0.0), v("v2", 10.0, 0.0), v("v3", 10.0, 10.0), v("v4", 0.0, 10.0)))
        val b = DrawnPolygon(EntityId("b"), style, listOf(v("v1", 20.0, 0.0), v("v2", 30.0, 0.0), v("v3", 30.0, 10.0), v("v4", 20.0, 10.0)))
        val ids = idsOf(PolygonOverlay.union(a, b, newVertexId = counter()))
        assertEquals(8, ids.size)
        assertEquals(8, ids.toSet().size)
    }

    // A generator that first offers the ids of the vertices that are kept: those belong to them, the new vertices must be given others.
    @Test
    fun aGeneratedIdThatIsAlreadyInUseIsSkippedAndNoKeptVertexLosesItsId() {
        val a = square("a", 0.0, 0.0, 10.0)
        val b = square("b", 5.0, 5.0, 10.0)
        val offers = ArrayDeque(listOf("a-sw", "a-se", "a-nw", "b-se", "b-ne", "b-nw", "n1", "n2", "n3"))
        val rings = PolygonOverlay.union(a, b, newVertexId = { VertexId(offers.removeFirst()) })
        val vertices = rings.flatten()
        assertEquals(vertices.size, vertices.map { it.id.id }.toSet().size)
        for ((id, at) in listOf("a-sw" to GeoPosition(0.0, 0.0), "a-se" to GeoPosition(0.0, 10.0), "a-nw" to GeoPosition(10.0, 0.0),
            "b-se" to GeoPosition(5.0, 15.0), "b-ne" to GeoPosition(15.0, 15.0), "b-nw" to GeoPosition(15.0, 5.0))) {
            assertEquals(at, vertices.single { it.id.id == id }.position, id)
        }
    }

    @Test
    fun theRingsMakeAValidShapeOfTheModel() {
        val rings = PolygonOverlay.difference(square("a", 0.0, 0.0, 30.0), square("b", 10.0, 10.0, 10.0), newVertexId = counter())
        val shape = DrawnPolygon(EntityId("r"), style, rings.first(), extraRings = rings.drop(1))
        assertEquals(2, shape.rings.size)
    }

    // ---- the grid ----

    @Test
    fun neighboursWhoseBorderIsAHairsBreadthApartAreJoinedOnTheDefaultGrid() {
        val a = square("a", 0.0, 0.0, 10.0)
        val b = square("b", 10.0 + 1e-9, 0.0, 10.0)
        val rings = PolygonOverlay.union(a, b, newVertexId = counter())
        assertEquals(1, rings.size)
        assertEquals(200.0, totalArea(rings), 1e-6)
    }

    @Test
    fun withoutAGridTheSameNeighboursStayTwoShapes() {
        val rings = PolygonOverlay.union(square("a", 0.0, 0.0, 10.0), square("b", 10.0 + 1e-9, 0.0, 10.0), gridSize = 0.0, newVertexId = counter())
        assertEquals(2, rings.size)
    }

    @Test
    fun aGapAsWideAsACellIsNotClosed() {
        val rings = PolygonOverlay.union(square("a", 0.0, 0.0, 10.0), square("b", 10.0 + 1e-6, 0.0, 10.0), newVertexId = counter())
        assertEquals(2, rings.size)
    }

    @Test
    fun aWiderGridClosesWiderGaps() {
        val rings = PolygonOverlay.union(square("a", 0.0, 0.0, 10.0), square("b", 10.0 + 1e-6, 0.0, 10.0), gridSize = 1e-4, newVertexId = counter())
        assertEquals(1, rings.size)
    }

    @Test
    fun theGridDoesNotMoveTheVerticesThatAreKept() {
        // 0.123456789 is not on a grid of 1e-7: the result must still hold the original, not the rounded one
        val a = DrawnPolygon(EntityId("a"), style, listOf(v("a1", 0.123456789, 0.0), v("a2", 10.0, 0.0), v("a3", 10.0, 10.0), v("a4", 0.123456789, 10.0)))
        val rings = PolygonOverlay.union(a, square("b", 5.0, 5.0, 10.0), newVertexId = counter())
        assertEquals(0.123456789, rings.flatten().single { it.id.id == "a1" }.position.longitude)
    }

    @Test
    fun aNegativeOrInfiniteGridIsRefused() {
        val a = square("a", 0.0, 0.0, 10.0)
        assertFailsWith<IllegalArgumentException> { PolygonOverlay.union(a, a, gridSize = -1.0) }
        assertFailsWith<IllegalArgumentException> { PolygonOverlay.union(a, a, gridSize = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { PolygonOverlay.union(a, a, gridSize = Double.NaN) }
    }

    // ---- invalid shapes ----

    private val bowTie = DrawnPolygon(EntityId("bow"), style, listOf(v("w1", 0.0, 0.0), v("w2", 10.0, 10.0), v("w3", 10.0, 0.0), v("w4", 0.0, 10.0)))

    @Test
    fun aShapeThatCrossesItselfIsRefusedByNameInEitherPosition() {
        val ok = square("a", 0.0, 0.0, 10.0)
        val first = assertFailsWith<InvalidShapeException> { PolygonOverlay.union(bowTie, ok) }
        assertTrue("\"bow\"" in first.message.orEmpty(), first.message)
        val second = assertFailsWith<InvalidShapeException> { PolygonOverlay.difference(ok, bowTie) }
        assertTrue("\"bow\"" in second.message.orEmpty(), second.message)
        assertFailsWith<InvalidShapeException> { PolygonOverlay.intersection(ok, bowTie) }
    }

    @Test
    fun aHoleOutsideItsShapeIsInvalidTooButAHoleInsideIsNot() {
        // a ring far from the outline is a part of its own to the model, so it is the valid shape of two parts
        val twoParts = DrawnPolygon(EntityId("two"), style, listOf(v("o1", 0.0, 0.0), v("o2", 10.0, 0.0), v("o3", 10.0, 10.0), v("o4", 0.0, 10.0)),
            extraRings = listOf(listOf(v("p1", 50.0, 50.0), v("p2", 60.0, 50.0), v("p3", 60.0, 60.0), v("p4", 50.0, 60.0))))
        assertNull(PolygonOverlay.validate(twoParts))
    }

    @Test
    fun validateGivesTheReasonOrNothing() {
        assertNull(PolygonOverlay.validate(square("a", 0.0, 0.0, 10.0)))
        assertNotNull(PolygonOverlay.validate(bowTie))
    }

    @Test
    fun aShapeWithAllItsPointsInOnePlaceIsInvalid() {
        val flat = DrawnPolygon(EntityId("flat"), style, listOf(v("f1", 1.0, 1.0), v("f2", 1.0, 1.0), v("f3", 1.0, 1.0)))
        assertNotNull(PolygonOverlay.validate(flat))
        assertFailsWith<InvalidShapeException> { PolygonOverlay.union(flat, square("a", 0.0, 0.0, 10.0)) }
    }

    @Test
    fun theWorkIsDoneOnCopiesTheInputsAreNotChanged() {
        val a = square("a", 0.0, 0.0, 10.0)
        val b = square("b", 5.0, 5.0, 10.0)
        val beforeA = a.copy()
        PolygonOverlay.union(a, b, newVertexId = counter())
        assertEquals(beforeA, a)
        assertEquals(4, a.vertices.size)
    }
}
