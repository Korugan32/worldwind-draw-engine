package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.booleanops.JtsOverlayEngine
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.geometry.Wgs84
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

class SetbackTest {
    private val engine = JtsOverlayEngine()
    private var next = 0
    private fun newId() = VertexId("s-${next++}")

    private fun metres(lat: Double): Pair<Double, Double> {
        val phi = lat * PI / 180
        val w = sqrt(1 - Wgs84.E2 * sin(phi) * sin(phi))
        return (180 / PI / (Wgs84.A / w * cos(phi))) to (180 / PI / (Wgs84.A * (1 - Wgs84.E2) / (w * w * w)))
    }

    private fun plot(lat: Double, lon: Double, vararg ring: Pair<Double, Double>, holes: List<List<Pair<Double, Double>>> = emptyList()): DrawnPolygon {
        val (perEast, perNorth) = metres(lat)
        var n = 0
        fun v(p: Pair<Double, Double>) = Vertex(VertexId("p${n++}"), GeoPosition(lat + p.second * perNorth, lon + p.first * perEast))
        return DrawnPolygon(EntityId("plot"), ShapeStyle.SolidColor(ColorRgba(0, 128, 0)), ring.map { v(it) }, extraRings = holes.map { h -> h.map { v(it) } })
    }

    private fun near(expected: Double, actual: Double, relative: Double, message: String = "") =
        assertTrue(abs(expected - actual) <= abs(expected) * relative, "$message expected $expected, was $actual (allowed ${relative * 100}%)")

    @Test
    fun aSquareLosesTheSetbackOnEverySide() {
        val square = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        val result = Setback.buildable(square, 10.0, engine, ::newId)
        near(80.0 * 80.0, result.area, 2e-4)
        assertEquals(1, result.rings.size)
        assertEquals(4, result.rings.single().size)
    }

    @Test
    fun noSetbackLeavesTheWholePlot() {
        val square = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        near(Measure.area(square), Setback.buildable(square, 0.0, engine, ::newId).area, 1e-6)
    }

    @Test
    fun theSetbackIsTheSameDistanceInEveryDirectionAtAnyLatitude() {
        // A longitude degree is a different length at 60 degrees than at the equator: the distance must still be metres.
        for (lat in listOf(0.0, 36.0, 42.0, 60.0, -40.0)) {
            val rectangle = plot(lat, 20.0, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 80.0, 0.0 to 80.0)
            near((200.0 - 2 * 5.0) * (80.0 - 2 * 5.0), Setback.buildable(rectangle, 5.0, engine, ::newId).area, 3e-4, "latitude $lat")
        }
    }

    /** What a rectilinear plot is left with: area less perimeter times the distance plus 4 d squared (a mitred corner adds back d squared). */
    private fun rectilinear(area: Double, perimeter: Double, d: Double) = area - perimeter * d + 4 * d * d

    @Test
    fun anLShapedPlotIsLeftWithWhatTheFormulaSays() {
        // 300 by 200 with a 150 by 100 corner out: area 45,000, perimeter 1,000.
        val l = plot(39.0, 35.0, 0.0 to 0.0, 300.0 to 0.0, 300.0 to 100.0, 150.0 to 100.0, 150.0 to 200.0, 0.0 to 200.0)
        near(rectilinear(45_000.0, 1_000.0, 10.0), Setback.buildable(l, 10.0, engine, ::newId).area, 3e-4)
    }

    @Test
    fun aHoleGrowsByTheSetback() {
        val outside = listOf(0.0 to 0.0, 200.0 to 0.0, 200.0 to 200.0, 0.0 to 200.0)
        val withHole = plot(39.0, 35.0, *outside.toTypedArray(), holes = listOf(listOf(50.0 to 50.0, 150.0 to 50.0, 150.0 to 150.0, 50.0 to 150.0)))
        // the outline is 180 by 180, the hole 120 by 120
        val result = Setback.buildable(withHole, 10.0, engine, ::newId)
        near(180.0 * 180.0 - 120.0 * 120.0, result.area, 3e-4)
        assertEquals(2, result.rings.size)
    }

    @Test
    fun aPlotNarrowerThanTwiceTheSetbackLeavesNothing() {
        val narrow = plot(39.0, 35.0, 0.0 to 0.0, 15.0 to 0.0, 15.0 to 100.0, 0.0 to 100.0)
        val result = Setback.buildable(narrow, 10.0, engine, ::newId)
        assertEquals(emptyList(), result.rings)
        assertEquals(0.0, result.area)
    }

    @Test
    fun aPlotThatIsNotARectangleKeepsTheDistanceFromEveryEdge() {
        // a triangle: its inset is the triangle with the same corners pulled in, and smaller by the area d * perimeter less the corners
        val triangle = plot(39.0, 35.0, 0.0 to 0.0, 200.0 to 0.0, 0.0 to 100.0)
        val result = Setback.buildable(triangle, 5.0, engine, ::newId)
        val a = 0.5 * 200 * 100
        val perimeter = 200 + 100 + sqrt(200.0 * 200 + 100.0 * 100)
        // the inscribed radius is a / s with s the half perimeter: the inset triangle is the same shape scaled by (r - d) / r
        val r = a / (perimeter / 2)
        near(a * ((r - 5.0) / r) * ((r - 5.0) / r), result.area, 5e-4)
    }

    @Test
    fun everyCornerOfTheResultIsNewAndNoIdIsUsedTwice() {
        val square = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        val ids = Setback.buildable(square, 10.0, engine, ::newId).rings.flatten().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { id -> square.vertices.any { it.id == id } })
        assertTrue(ids.all { it.id.startsWith("s-") })
    }

    @Test
    fun theCornersLieInsideThePlotAtTheRightDistance() {
        val square = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        val origin = square.vertices.first().position
        for (corner in Setback.buildable(square, 10.0, engine, ::newId).rings.single()) {
            val east = Measure.distance(origin, GeoPosition(origin.latitude, corner.position.longitude))
            val north = Measure.distance(origin, GeoPosition(corner.position.latitude, origin.longitude))
            assertTrue(abs(east - 10.0) < 0.05 || abs(east - 90.0) < 0.05, "east $east")
            assertTrue(abs(north - 10.0) < 0.05 || abs(north - 90.0) < 0.05, "north $north")
        }
    }

    @Test
    fun badInputIsRefused() {
        val square = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        assertFailsWith<IllegalArgumentException> { Setback.buildable(square, -1.0, engine, ::newId) }
        assertFailsWith<IllegalArgumentException> { Setback.buildable(square, Double.NaN, engine, ::newId) }
        assertFailsWith<IllegalArgumentException> { Setback.buildable(square, Double.POSITIVE_INFINITY, engine, ::newId) }
        val bowtie = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 100.0, 100.0 to 0.0, 0.0 to 100.0)
        assertFailsWith<InvalidShapeException> { Setback.buildable(bowtie, 5.0, engine, ::newId) }
        val across = DrawnPolygon(
            EntityId("x"), ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
            listOf(GeoPosition(10.0, 179.999), GeoPosition(10.0, -179.999), GeoPosition(10.001, -179.999), GeoPosition(10.001, 179.999))
                .mapIndexed { i, p -> Vertex(VertexId("a$i"), p) },
        )
        assertFailsWith<IllegalArgumentException> { Setback.buildable(across, 5.0, engine, ::newId) }
    }

    @Test
    fun thePlotItselfIsNotChanged() {
        val square = plot(39.0, 35.0, 0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 0.0 to 100.0)
        val before = square.copy()
        Setback.buildable(square, 10.0, engine, ::newId)
        assertEquals(before, square)
    }
}
