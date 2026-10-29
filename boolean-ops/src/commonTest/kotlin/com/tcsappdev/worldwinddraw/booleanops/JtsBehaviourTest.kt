package com.tcsappdev.worldwinddraw.booleanops

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.operation.valid.IsValidOp
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import kotlin.test.Test
import kotlin.test.assertEquals

// What the library's overlay (JTS, see docs/PLAN.md Faz 12b) does with the cases this one will depend on, written down as tests so a new version of
// it, or a platform where it behaves differently, shows up here. Not tests of our own code yet.
class JtsBehaviourTest {
    private val factory = GeometryFactory()

    private fun square(x: Double, y: Double, side: Double) = factory.createPolygon(
        arrayOf(Coordinate(x, y), Coordinate(x + side, y), Coordinate(x + side, y + side), Coordinate(x, y + side), Coordinate(x, y)),
    )

    @Test
    fun unionOfTwoOverlappingSquares() {
        val union = square(0.0, 0.0, 10.0).union(square(5.0, 5.0, 10.0))
        assertEquals(175.0, union.getArea(), 1e-9) // 100 + 100 - 25
    }

    @Test
    fun differenceMakesAnL() {
        val difference = square(0.0, 0.0, 10.0).difference(square(5.0, 5.0, 10.0))
        assertEquals(75.0, difference.getArea(), 1e-9)
    }

    private fun ring(vararg xy: Double) = (0 until xy.size step 2).map { Coordinate(xy[it], xy[it + 1]) }.let { it + it.first() }.toTypedArray()

    @Test
    fun twoNeighboursThatShareAnEdgeExactlyMakeOnePolygonWithoutAHole() {
        val left = square(0.0, 0.0, 10.0)
        val right = square(10.0, 0.0, 10.0)
        val union = OverlayNGRobust.overlay(left, right, OverlayNG.UNION)
        assertEquals(1, union.getNumGeometries())
        assertEquals(200.0, union.getArea(), 1e-9)
        assertEquals(0, (union as Polygon).getNumInteriorRing())
    }

    @Test
    fun aDifferenceThatCutsAShapeInTwoGivesAMultiPolygon() {
        val bar = square(0.0, 0.0, 30.0)
        val cutter = factory.createPolygon(ring(10.0, -5.0, 20.0, -5.0, 20.0, 35.0, 10.0, 35.0))
        val difference = OverlayNGRobust.overlay(bar, cutter, OverlayNG.DIFFERENCE)
        assertEquals(2, difference.getNumGeometries())
        assertEquals(900.0 - 10 * 30, difference.getArea(), 1e-9)
    }

    @Test
    fun aDifferenceInsideAShapeMakesAHole() {
        val outer = square(0.0, 0.0, 30.0)
        val inner = square(10.0, 10.0, 10.0)
        val difference = OverlayNGRobust.overlay(outer, inner, OverlayNG.DIFFERENCE) as Polygon
        assertEquals(1, difference.getNumInteriorRing())
        assertEquals(800.0, difference.getArea(), 1e-9)
    }

    @Test
    fun aShapeWithAHoleKeepsItsHoleInAUnionWithAnotherShape() {
        val holed = OverlayNGRobust.overlay(square(0.0, 0.0, 30.0), square(10.0, 10.0, 10.0), OverlayNG.DIFFERENCE)
        val union = OverlayNGRobust.overlay(holed, square(25.0, 25.0, 20.0), OverlayNG.UNION) as Polygon
        assertEquals(1, union.getNumInteriorRing())
        assertEquals(800.0 + 400.0 - 25.0, union.getArea(), 1e-9)
    }

    // Real borders: the same edge written by two sources differs in the last digits. Exact geometry does not close even a gap of 1e-12 degrees...
    @Test
    fun anExactOverlayDoesNotJoinNeighboursThatAreApartEvenByAHairsBreadth() {
        val a = factory.createPolygon(ring(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0))
        for (gap in listOf(1e-12, 1e-9, 1e-7)) {
            val b = factory.createPolygon(ring(10.0 + gap, 0.0, 20.0, 0.0, 20.0, 10.0, 10.0 + gap, 10.0))
            assertEquals(2, OverlayNGRobust.overlay(a, b, OverlayNG.UNION).getNumGeometries(), "gap $gap")
        }
    }

    @Test
    fun neighboursThatOverlapByAHairsBreadthAreJoined() {
        val a = factory.createPolygon(ring(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0))
        val b = factory.createPolygon(ring(10.0 - 1e-9, 0.0, 20.0, 0.0, 20.0, 10.0, 10.0 - 1e-9, 10.0))
        assertEquals(1, OverlayNGRobust.overlay(a, b, OverlayNG.UNION).getNumGeometries())
    }

    // ...but with the coordinates rounded to a grid first (OverlayNG's fixed precision), a gap smaller than a cell closes. That grid is the tolerance.
    @Test
    fun aPrecisionGridClosesGapsSmallerThanItsCell() {
        val a = factory.createPolygon(ring(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0))
        val grid = PrecisionModel(1e7) // cells of 1e-7 degrees, about a centimetre
        for ((gap, parts) in listOf(1e-12 to 1, 1e-9 to 1, 4e-8 to 1, 1e-7 to 2, 1e-6 to 2)) {
            val b = factory.createPolygon(ring(10.0 + gap, 0.0, 20.0, 0.0, 20.0, 10.0, 10.0 + gap, 10.0))
            assertEquals(parts, OverlayNG.overlay(a, b, OverlayNG.UNION, grid).getNumGeometries(), "gap $gap")
        }
    }

    @Test
    fun aBowTieIsNotValidAndTheValidityCheckSaysSo() {
        val bowTie = factory.createPolygon(ring(0.0, 0.0, 10.0, 10.0, 10.0, 0.0, 0.0, 10.0))
        assertEquals(false, IsValidOp(bowTie).isValid())
    }

    @Test
    fun topologyPreservingSimplificationIsThere() {
        val shape = factory.createPolygon(ring(0.0, 0.0, 5.0, 0.01, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0))
        val simple = TopologyPreservingSimplifier.simplify(shape, 0.1)
        assertEquals(4, simple.getNumPoints() - 1)
    }
}
