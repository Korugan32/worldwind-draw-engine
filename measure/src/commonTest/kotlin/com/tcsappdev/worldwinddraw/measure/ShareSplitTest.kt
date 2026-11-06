package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.booleanops.JtsOverlayEngine
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShareSplitTest {
    // No rounding of the corners: the areas can then be checked to a millionth. (The default grid is tried in its own test.)
    private val overlay = JtsOverlayEngine(gridSize = 0.0)
    private var next = 0
    private fun newId() = VertexId("new-${next++}")

    private val style = ShapeStyle.SolidColor(ColorRgba(0, 128, 0))

    /** Metres east and north to degrees at [lat]. */
    private fun metres(lat: Double): Pair<Double, Double> {
        val phi = lat * PI / 180
        val w = sqrt(1 - Wgs84.E2 * sin(phi) * sin(phi))
        val n = Wgs84.A / w
        val m = Wgs84.A * (1 - Wgs84.E2) / (w * w * w)
        return (180 / PI / (n * cos(phi))) to (180 / PI / m)
    }

    /** A polygon from points in metres (east, north) from a south-west origin at [lat], [lon]. */
    private fun plot(lat: Double, lon: Double, vararg ring: Pair<Double, Double>, holes: List<List<Pair<Double, Double>>> = emptyList()): DrawnPolygon {
        val (perEast, perNorth) = metres(lat)
        var n = 0
        fun wrapped(longitude: Double) = if (longitude > 180.0) longitude - 360.0 else longitude
        fun v(p: Pair<Double, Double>) = Vertex(VertexId("p${n++}"), GeoPosition(lat + p.second * perNorth, wrapped(lon + p.first * perEast)))
        return DrawnPolygon(EntityId("plot"), style, ring.map { v(it) }, extraRings = holes.map { h -> h.map { v(it) } })
    }

    private fun rectangle(width: Double, height: Double, lat: Double = 39.0, lon: Double = 35.0) =
        plot(lat, lon, 0.0 to 0.0, width to 0.0, width to height, 0.0 to height)

    private fun near(expected: Double, actual: Double, relative: Double, message: String = "") =
        assertTrue(abs(expected - actual) <= abs(expected) * relative, "$message expected $expected, was $actual (allowed ${relative * 100}%)")

    private fun ShareSplitResult.areas() = pieces.map { it.area }

    /**
     * The pieces are in proportion to the shares to a ten-millionth of one another, and together they are the plot to a few hundred-thousandths
     * (the overlay's corner on an edge lies on its straight line in longitude and latitude, and an area is measured with the edge as a great-circle arc).
     */
    private fun assertShares(result: ShareSplitResult, shares: List<Double>, plot: DrawnPolygon, message: String = "") {
        val whole = result.areas().sum()
        near(Measure.area(plot), whole, 2e-5, "$message the pieces together")
        for (i in shares.indices) near(whole * shares[i] / shares.sum(), result.pieces[i].area, 1e-7, "$message piece $i")
    }

    /** How far across a piece reaches, east-west and north-south, in metres (its bounding box). */
    private fun Piece.extent(): Pair<Double, Double> {
        val points = rings.flatten().map { it.position }
        val west = points.minOf { it.longitude }; val east = points.maxOf { it.longitude }
        val south = points.minOf { it.latitude }; val north = points.maxOf { it.latitude }
        val midLat = (south + north) / 2
        return Measure.distance(GeoPosition(midLat, west), GeoPosition(midLat, east)) to
            Measure.distance(GeoPosition(south, west), GeoPosition(north, west))
    }

    // --- the shares come out in proportion ---

    @Test
    fun aHalfAndAHalfGiveTwoEqualPieces() {
        val plot = rectangle(200.0, 100.0)
        val result = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId)
        assertEquals(2, result.pieces.size)
        assertShares(result, listOf(1.0, 1.0), plot)
    }

    @Test
    fun aHalfAndTwoQuartersAreInThatProportion() {
        val plot = rectangle(300.0, 120.0)
        val result = ShareSplit.split(plot, listOf(0.5, 0.25, 0.25), overlay, ::newId)
        assertShares(result, listOf(0.5, 0.25, 0.25), plot)
    }

    @Test
    fun onlyTheProportionOfTheSharesCounts() {
        val plot = rectangle(300.0, 120.0)
        val a = ShareSplit.split(plot, listOf(1.0, 1.0, 2.0), overlay, ::newId).areas()
        val b = ShareSplit.split(plot, listOf(0.25, 0.25, 0.5), overlay, ::newId).areas()
        for (i in a.indices) near(a[i], b[i], 1e-7)
    }

    @Test
    fun theTargetAreaIsTheSharesPartOfTheWholeAndTheAreaMatchesIt() {
        val plot = rectangle(250.0, 90.0)
        val result = ShareSplit.split(plot, listOf(3.0, 1.0, 2.0), overlay, ::newId)
        val total = Measure.area(plot)
        near(total * 3 / 6, result.pieces[0].targetArea, 1e-12)
        near(total * 1 / 6, result.pieces[1].targetArea, 1e-12)
        near(total * 2 / 6, result.pieces[2].targetArea, 1e-12)
        for (p in result.pieces) near(p.targetArea, p.area, 2e-5)
    }

    @Test
    fun oneShareIsTheWholePlot() {
        val plot = rectangle(100.0, 100.0)
        val result = ShareSplit.split(plot, listOf(5.0), overlay, ::newId)
        assertEquals(1, result.pieces.size)
        near(Measure.area(plot), result.pieces.single().area, 1e-9)
    }

    @Test
    fun manySharesStillAddUpAndEachIsRight() {
        val plot = rectangle(400.0, 150.0, lat = 41.0, lon = 29.0)
        val shares = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 1.0, 1.0)
        val result = ShareSplit.split(plot, shares, overlay, ::newId)
        assertEquals(shares.size, result.pieces.size)
        assertShares(result, shares, plot)
    }

    // --- where the cuts run ---

    @Test
    fun byDefaultALongPlotIsCutAcrossItsLongWay() {
        // 400 m by 100 m in two: two pieces of 200 m by 100 m, not two strips of 400 m by 50 m.
        val result = ShareSplit.split(rectangle(400.0, 100.0), listOf(1.0, 1.0), overlay, ::newId)
        for (piece in result.pieces) {
            val (east, north) = piece.extent()
            near(200.0, east, 2e-3, "east-west"); near(100.0, north, 2e-3, "north-south")
        }
        // the cut runs north-south, which is bearing 0
        assertTrue(result.cutBearing < 1.0 || result.cutBearing > 179.0, "cut bearing ${result.cutBearing}")
    }

    @Test
    fun aTallPlotIsCutAcrossToo() {
        val result = ShareSplit.split(rectangle(100.0, 400.0), listOf(1.0, 1.0), overlay, ::newId)
        for (piece in result.pieces) {
            val (east, north) = piece.extent()
            near(100.0, east, 2e-3); near(200.0, north, 2e-3)
        }
        near(90.0, result.cutBearing, 1e-2)
    }

    @Test
    fun theCutBearingChoosesTheDirectionTheCutsRunAlong() {
        val plot = rectangle(200.0, 100.0)
        // cuts running north-south split the width: two pieces of 100 by 100
        val vertical = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId, cutBearing = 0.0)
        for (p in vertical.pieces) { val (e, n) = p.extent(); near(100.0, e, 2e-3); near(100.0, n, 2e-3) }
        // cuts running east-west split the height: two strips of 200 by 50
        val horizontal = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId, cutBearing = 90.0)
        for (p in horizontal.pieces) { val (e, n) = p.extent(); near(200.0, e, 2e-3); near(50.0, n, 2e-3) }
        near(0.0 + 90.0, horizontal.cutBearing, 1e-9)
    }

    @Test
    fun aBearingIsTakenModuloAHalfTurn() {
        val plot = rectangle(200.0, 100.0)
        val a = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId, cutBearing = 30.0)
        val b = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId, cutBearing = 210.0)
        near(30.0, a.cutBearing, 1e-9); near(30.0, b.cutBearing, 1e-9)
        near(a.pieces[0].area, b.pieces[0].area, 1e-7)
    }

    @Test
    fun aSlantedCutStillGivesTheRightAreas() {
        val plot = rectangle(300.0, 200.0)
        for (bearing in listOf(10.0, 33.0, 45.0, 77.0, 120.0, 160.0)) {
            val result = ShareSplit.split(plot, listOf(2.0, 1.0, 1.0), overlay, ::newId, cutBearing = bearing)
            assertShares(result, listOf(2.0, 1.0, 1.0), plot, "bearing $bearing")
        }
    }

    @Test
    fun theFirstPieceIsOnTheSideTheSweepStartsFrom() {
        // Cuts running north-south: the sweep goes east, so the first share is the western piece.
        val result = ShareSplit.split(rectangle(400.0, 100.0), listOf(1.0, 3.0), overlay, ::newId, cutBearing = 0.0)
        val west = result.pieces[0].rings.flatten().maxOf { it.position.longitude }
        val east = result.pieces[1].rings.flatten().minOf { it.position.longitude }
        assertTrue(west <= east + 1e-9, "the first piece is the western one")
        assertTrue(result.pieces[0].area < result.pieces[1].area)
    }

    // --- shapes that are not rectangles ---

    @Test
    fun aPlotThatIsNotConvexIsDividedRight() {
        // An L: a 300 by 200 m rectangle with a 150 by 100 m corner taken out.
        val l = plot(39.0, 35.0, 0.0 to 0.0, 300.0 to 0.0, 300.0 to 100.0, 150.0 to 100.0, 150.0 to 200.0, 0.0 to 200.0)
        val total = Measure.area(l)
        near(300.0 * 200.0 - 150.0 * 100.0, total, 1e-3)
        for (bearing in listOf(null, 0.0, 90.0, 45.0)) {
            val result = ShareSplit.split(l, listOf(1.0, 1.0, 1.0), overlay, ::newId, cutBearing = bearing)
            assertShares(result, listOf(1.0, 1.0, 1.0), l, "bearing $bearing")
        }
    }

    @Test
    fun aPlotWithAHoleIsDividedByTheAreaThatIsLeft() {
        val withHole = plot(
            39.0, 35.0, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 200.0, 0.0 to 200.0,
            holes = listOf(listOf(50.0 to 50.0, 150.0 to 50.0, 150.0 to 150.0, 50.0 to 150.0)),
        )
        near(200.0 * 200.0 - 100.0 * 100.0, Measure.area(withHole), 1e-3)
        val result = ShareSplit.split(withHole, listOf(1.0, 1.0), overlay, ::newId, cutBearing = 0.0)
        assertShares(result, listOf(1.0, 1.0), withHole)
    }

    @Test
    fun aPlotAcrossTheAntimeridianIsRefusedButStillMeasured() {
        // The overlay works on longitude and latitude as a flat plane: it cannot cut across longitude 180.
        val plot = plot(10.0, 179.9990, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 100.0, 0.0 to 100.0)
        assertFailsWith<IllegalArgumentException> { ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId) }
        near(200.0 * 100.0, Measure.area(plot), 1e-3)
    }

    @Test
    fun theAreaDoesNotDependOnWhereOnTheGlobeThePlotIs() {
        for (lat in listOf(0.0, 36.0, 42.0, 55.0, -30.0)) {
            val plot = rectangle(500.0, 200.0, lat = lat, lon = 20.0)
            val result = ShareSplit.split(plot, listOf(1.0, 2.0, 3.0), overlay, ::newId)
            assertShares(result, listOf(1.0, 2.0, 3.0), plot, "latitude $lat")
        }
    }

    @Test
    fun aBigPlotOfThreeKilometresIsStillRight() {
        val plot = rectangle(3_000.0, 1_500.0, lat = 39.0)
        val result = ShareSplit.split(plot, listOf(1.0, 1.0, 1.0, 1.0), overlay, ::newId)
        assertShares(result, listOf(1.0, 1.0, 1.0, 1.0), plot)
    }

    @Test
    fun withTheDefaultRoundingTheAreasAreStillWithinAFewTensOfThousandths() {
        // The default grid (1e-7 degrees) moves the corners of the cuts by millimetres: 0.003% of a plot of a hectare, which is still far
        // below what a plot's own boundary can be known to.
        val plot = rectangle(200.0, 100.0)
        val total = Measure.area(plot)
        val rounded = ShareSplit.split(plot, listOf(1.0, 2.0, 1.0), JtsOverlayEngine(), ::newId, cutBearing = 33.0)
        near(total * 0.25, rounded.pieces[0].area, 1e-4)
        near(total * 0.5, rounded.pieces[1].area, 1e-4)
        near(total * 0.25, rounded.pieces[2].area, 1e-4)
    }

    @Test
    fun aRotatedPlotIsCutAcrossItsLongWayToo() {
        // A 400 by 100 m rectangle turned 30 degrees: the cuts should run across it, at 30 + 90 = 120 degrees to its long side's bearing.
        val phi = 39.0 * PI / 180
        val (perEast, perNorth) = metres(39.0)
        val turn = 30.0 * PI / 180
        fun at(x: Double, y: Double) = x * cos(turn) + y * sin(turn) to (-x * sin(turn) + y * cos(turn)) // east, north
        val ring = listOf(0.0 to 0.0, 400.0 to 0.0, 400.0 to 100.0, 0.0 to 100.0).map { at(it.first, it.second) }
        val plot = plot(39.0, 35.0, *ring.toTypedArray())
        val result = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId)
        // the long side runs along bearing 60 (cos30, -sin30 is east-south-east: bearing 90 + 30 = 120); the cuts run across it, bearing 30
        val expected = 30.0
        val apart = abs(result.cutBearing - expected).let { minOf(it, 180 - it) }
        assertTrue(apart < 0.2, "cut bearing ${result.cutBearing}, expected about $expected")
        assertShares(result, listOf(1.0, 1.0), plot)
        assertTrue(phi > 0 && perEast > 0 && perNorth > 0)
    }

    // --- ids ---

    @Test
    fun theCornersOfThePlotKeepTheirIdsAndTheNewOnesHaveNewIds() {
        val plot = rectangle(200.0, 100.0)
        val originalIds = plot.vertices.map { it.id }.toSet()
        val result = ShareSplit.split(plot, listOf(1.0, 1.0), overlay, ::newId, cutBearing = 0.0)
        val ids = result.pieces.flatMap { p -> p.rings.flatten().map { it.id } }
        // each piece has two of the plot's own corners, and the other two corners of its outline are made by the cut
        assertEquals(4, ids.count { it in originalIds })
        assertEquals(4, ids.count { it.id.startsWith("new-") })
        // and the cut's corners are shared by two pieces, with a different id on each side
        assertEquals(ids.size, ids.toSet().size, "no id twice")
    }

    // --- bad input ---

    @Test
    fun noSharesOrABadShareIsRefused() {
        val plot = rectangle(100.0, 100.0)
        assertFailsWith<IllegalArgumentException> { ShareSplit.split(plot, emptyList(), overlay, ::newId) }
        assertFailsWith<IllegalArgumentException> { ShareSplit.split(plot, listOf(1.0, 0.0), overlay, ::newId) }
        assertFailsWith<IllegalArgumentException> { ShareSplit.split(plot, listOf(1.0, -1.0), overlay, ::newId) }
        assertFailsWith<IllegalArgumentException> { ShareSplit.split(plot, listOf(1.0, Double.NaN), overlay, ::newId) }
        assertFailsWith<IllegalArgumentException> { ShareSplit.split(plot, listOf(1.0, Double.POSITIVE_INFINITY), overlay, ::newId) }
    }

    @Test
    fun aPlotThatCrossesItselfIsRefused() {
        val bowtie = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 100.0, 100.0 to 0.0, 0.0 to 100.0)
        assertFailsWith<InvalidShapeException> { ShareSplit.split(bowtie, listOf(1.0, 1.0), overlay, ::newId) }
    }

    @Test
    fun thePlotItselfIsNotChanged() {
        val plot = rectangle(200.0, 100.0)
        val before = plot.copy()
        ShareSplit.split(plot, listOf(1.0, 2.0), overlay, ::newId)
        assertEquals(before, plot)
    }
}
