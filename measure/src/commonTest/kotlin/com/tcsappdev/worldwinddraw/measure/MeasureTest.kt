package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Wgs84
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasureTest {
    private fun near(expected: Double, actual: Double, relative: Double, message: String = "") =
        assertTrue(abs(expected - actual) <= abs(expected) * relative, "$message expected $expected, was $actual (allowed ${relative * 100}%)")

    /**
     * A square of [side] metres by [side] metres with its south-west corner at [lat], [lon], worked out on the ellipsoid by the radii of
     * curvature there (M along a meridian, N across it): a way to the right area that does not use the code under test.
     */
    private fun squareOfMeters(lat: Double, lon: Double, side: Double): List<GeoPosition> {
        val phi = lat * PI / 180
        val w = sqrt(1 - Wgs84.E2 * sin(phi) * sin(phi))
        val n = Wgs84.A / w
        val m = Wgs84.A * (1 - Wgs84.E2) / (w * w * w)
        val dLat = side / m * 180 / PI
        val dLon = side / (n * cos(phi)) * 180 / PI
        return listOf(GeoPosition(lat, lon), GeoPosition(lat, lon + dLon), GeoPosition(lat + dLat, lon + dLon), GeoPosition(lat + dLat, lon))
    }

    /**
     * What the area of [squareOfMeters] really is: its meridian sides come together towards the pole, so it is a trapezoid, a little narrower
     * at the top. (The mean of the two parallels, times the height.)
     */
    private fun trapezoid(lat: Double, side: Double): Double {
        val corners = squareOfMeters(lat, 0.0, side)
        val dLon = (corners[1].longitude - corners[0].longitude) * PI / 180
        fun parallel(latDegrees: Double): Double {
            val phi = latDegrees * PI / 180
            return dLon * Wgs84.A / sqrt(1 - Wgs84.E2 * sin(phi) * sin(phi)) * cos(phi)
        }
        val bottom = corners[0].latitude
        val top = corners[2].latitude
        val mid = (bottom + top) / 2 * PI / 180
        val m = Wgs84.A * (1 - Wgs84.E2) / (1 - Wgs84.E2 * sin(mid) * sin(mid)).pow(1.5)
        return (top - bottom) * PI / 180 * m * (parallel(bottom) + parallel(top)) / 2
    }

    // --- distance ---

    @Test
    fun aDegreeOfLongitudeAlongTheEquatorIsAboutAHundredAndElevenKilometres() {
        near(111_319.49, Measure.distance(GeoPosition(0.0, 0.0), GeoPosition(0.0, 1.0)), 1e-6)
    }

    @Test
    fun aDegreeOfLatitudeAtTheEquatorIsShorterThanAtThePole() {
        near(110_574.39, Measure.distance(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0)), 1e-6)
        near(111_693.86, Measure.distance(GeoPosition(89.0, 0.0), GeoPosition(90.0, 0.0)), 1e-5)
    }

    @Test
    fun theDistanceIsTheSameBothWaysAndZeroForTheSamePoint() {
        val a = GeoPosition(41.0082, 28.9784)
        val b = GeoPosition(39.9334, 32.8597)
        assertEquals(Measure.distance(a, b), Measure.distance(b, a), 1e-6)
        assertEquals(0.0, Measure.distance(a, a))
    }

    @Test
    fun aKnownLongDistanceIsRight() {
        // Equator to the pole along a meridian is a quarter of the ellipsoid's meridian: 10,001,965.729 m.
        near(10_001_965.729, Measure.distance(GeoPosition(0.0, 10.0), GeoPosition(90.0, 10.0)), 1e-9)
    }

    @Test
    fun theDistanceTakesTheShortWayRoundTheAntimeridian() {
        near(111_319.49, Measure.distance(GeoPosition(0.0, 179.5), GeoPosition(0.0, -179.5)), 1e-6)
    }

    @Test
    fun twoAlmostOppositePointsStillGiveADistance() {
        // Vincenty does not settle here; the answer is the ball's, roughly half the way round the globe.
        val d = Measure.distance(GeoPosition(0.0, 0.0), GeoPosition(0.5, 179.7))
        assertTrue(d in 19_900_000.0..20_100_000.0, "was $d")
    }

    @Test
    fun theLengthOfALineAndThePerimeterOfARingAreTheSumsOfTheirEdges() {
        val ring = squareOfMeters(40.0, 33.0, 100.0)
        near(400.0, Measure.perimeter(ring), 1e-4)
        assertEquals(4, Measure.edgeLengths(ring).size)
        near(300.0, Measure.length(ring + ring.first()) - Measure.edgeLengths(ring).last(), 1e-4)
        assertEquals(0.0, Measure.length(listOf(ring.first())))
        assertEquals(0.0, Measure.perimeter(listOf(ring.first())))
    }

    // --- area ---

    @Test
    fun aHundredByHundredMetreSquareIsTenThousandSquareMetres() {
        for (lat in listOf(0.0, 36.0, 40.0, 42.0, 60.0, -33.0)) {
            near(10_000.0, Measure.area(squareOfMeters(lat, 33.0, 100.0)), 2e-5, "at latitude $lat")
        }
    }

    @Test
    fun aOneKilometreSquareIsAMillionSquareMetresLessTheMeridiansComingTogether() {
        // At 39 degrees the top edge of a one-kilometre "square" is 12.7 cm shorter than the bottom one: about 63 m2 of a million.
        val expected = trapezoid(39.0, 1_000.0)
        near(999_937.0, expected, 1e-5, "the trapezoid")
        near(expected, Measure.area(squareOfMeters(39.0, 35.0, 1_000.0)), 2e-5)
    }

    @Test
    fun theSizeOfAPlotDoesNotDependOnWhereOnTheGlobeItIs() {
        // What a ball of the mean radius would get wrong: about 0.4% at the equator, 0.1% in Turkey.
        near(trapezoid(0.0, 500.0), Measure.area(squareOfMeters(0.0, 10.0, 500.0)), 2e-5, "equator")
        near(trapezoid(39.0, 500.0), Measure.area(squareOfMeters(39.0, 35.0, 500.0)), 2e-5, "turkey")
        near(trapezoid(60.0, 500.0), Measure.area(squareOfMeters(60.0, 35.0, 500.0)), 2e-5, "latitude 60")
    }

    @Test
    fun theAreaIsTheSameWhicheverWayRoundTheRingGoesAndFromWhicheverCornerItStarts() {
        val ring = squareOfMeters(40.0, 33.0, 200.0)
        val area = Measure.area(ring)
        assertEquals(area, Measure.area(ring.reversed()), 1e-6)
        assertEquals(area, Measure.area(ring.drop(2) + ring.take(2)), 1e-6)
        // and a few metres across it does not lose its digits
        val tiny = squareOfMeters(40.0, 33.0, 3.0)
        near(9.0, Measure.area(tiny), 1e-5, "3 m square")
        assertEquals(Measure.area(tiny), Measure.area(tiny.reversed()), 1e-9)
    }

    @Test
    fun aRingThatEnclosesNothingHasNoArea() {
        assertEquals(0.0, Measure.area(emptyList()))
        assertEquals(0.0, Measure.area(listOf(GeoPosition(1.0, 1.0), GeoPosition(2.0, 2.0))))
        assertEquals(0.0, Measure.area(listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 1.0), GeoPosition(0.0, 2.0)))) // on one line
    }

    @Test
    fun aShapeThatIsNotConvexIsMeasuredRight() {
        // An L: a 200 m square with a 100 m square taken out of one corner = 30,000 m2.
        val phi = 40.0 * PI / 180
        val w = sqrt(1 - Wgs84.E2 * sin(phi) * sin(phi))
        val dLat = 100 / (Wgs84.A * (1 - Wgs84.E2) / (w * w * w)) * 180 / PI
        val dLon = 100 / (Wgs84.A / w * cos(phi)) * 180 / PI
        val lat = 40.0; val lon = 33.0
        val l = listOf(
            GeoPosition(lat, lon), GeoPosition(lat, lon + 2 * dLon), GeoPosition(lat + dLat, lon + 2 * dLon),
            GeoPosition(lat + dLat, lon + dLon), GeoPosition(lat + 2 * dLat, lon + dLon), GeoPosition(lat + 2 * dLat, lon),
        )
        near(30_000.0, Measure.area(l), 5e-5)
    }

    @Test
    fun aPlotAcrossTheAntimeridianIsMeasuredRight() {
        val across = listOf(GeoPosition(10.0, 179.999), GeoPosition(10.0, -179.999), GeoPosition(10.001, -179.999), GeoPosition(10.001, 179.999))
        val expected = Measure.distance(across[0], across[1]) * Measure.distance(across[1], across[2])
        near(expected, Measure.area(across), 1e-3)
    }

    // --- a polygon with holes and parts ---

    private fun polygon(vararg rings: List<GeoPosition>): DrawnPolygon {
        var n = 0
        fun ring(r: List<GeoPosition>) = r.map { Vertex(VertexId("v${n++}"), it) }
        return DrawnPolygon(
            EntityId("p"), ShapeStyle.SolidColor(ColorRgba(0, 0, 0)), ring(rings.first()),
            extraRings = rings.drop(1).map { ring(it) },
        )
    }

    @Test
    fun aHoleIsTakenOutOfTheArea() {
        val outside = squareOfMeters(40.0, 33.0, 200.0)
        val hole = squareOfMeters(40.0005, 33.0005, 50.0)
        near(40_000.0 - 2_500.0, Measure.area(polygon(outside, hole)), 1e-4)
    }

    @Test
    fun separatePartsAreAdded() {
        val one = squareOfMeters(40.0, 33.0, 100.0)
        val two = squareOfMeters(40.01, 33.01, 50.0)
        near(10_000.0 + 2_500.0, Measure.area(polygon(one, two)), 1e-4)
    }

    @Test
    fun aPolygonOfOneRingIsTheAreaOfItsRing() {
        val ring = squareOfMeters(40.0, 33.0, 100.0)
        assertEquals(Measure.area(ring), Measure.area(polygon(ring)), 1e-6)
    }

    // --- units ---

    @Test
    fun theUnitsConvertBothWays() {
        assertEquals(2.5, AreaUnit.Donum.fromSquareMeters(2_500.0), 1e-12)
        assertEquals(0.25, AreaUnit.Hectare.fromSquareMeters(2_500.0), 1e-12)
        assertEquals(1_000.0, AreaUnit.Donum.toSquareMeters(1.0), 1e-12)
        near(4_046.8564224, AreaUnit.Acre.toSquareMeters(1.0), 1e-12)
        for (unit in AreaUnit.entries) assertEquals(123.0, unit.fromSquareMeters(unit.toSquareMeters(123.0)), 1e-9)
    }
}
