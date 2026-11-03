package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RouteMotionTest {
    private val id = EntityId("r")
    private val plane = ShapeStyle.SolidColor(ColorRgba(255, 255, 255))

    // East along the equator for 40 degrees, then north for 40 degrees: two legs of the same length.
    private val route = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0), GeoPosition(40.0, 40.0))

    // A plain trail and a vehicle: what the rest is added to.
    private val options = RouteOptions(glow = 0f, trailShare = 0f, vehicle = plane, height = 0f, arc = 0f, lift = 0f)

    private fun List<DrawableEntity>.vehicle() = filterIsInstance<DrawnMarker>().singleOrNull { it.id.id == "r#vehicle" }
    private fun List<DrawableEntity>.lines() = filterIsInstance<DrawnPolyline>()
    private fun near(a: GeoPosition, b: GeoPosition, tolerance: Double = 1e-6) = abs(a.latitude - b.latitude) < tolerance && abs(a.longitude - b.longitude) < tolerance

    @Test
    fun atTheStartTheVehicleIsAtTheFirstPointWithNoLineYet() {
        val shapes = RouteMotion.at(id, route, 0f, options)
        assertTrue(near(route.first(), shapes.vehicle()!!.position))
        assertEquals(0, shapes.lines().size)
    }

    @Test
    fun theVehicleIsHalfWayAlongTheRouteAtHalfTheTimeWhichHereIsTheMiddlePoint() {
        val shapes = RouteMotion.at(id, route, 0.5f, options)
        assertTrue(near(route[1], shapes.vehicle()!!.position, 1e-4), "was ${shapes.vehicle()!!.position}")
    }

    @Test
    fun theLineEndsWhereTheVehicleIs() {
        val shapes = RouteMotion.at(id, route, 0.3f, options)
        assertTrue(near(shapes.lines().single().vertices.last().position, shapes.vehicle()!!.position))
        assertEquals(route.first(), shapes.lines().single().vertices.first().position)
    }

    @Test
    fun theVehicleHeadsInTheDirectionItIsGoing() {
        assertEquals(90f, RouteMotion.at(id, route, 0.2f, options).vehicle()!!.rotation, 1e-3f) // east along the equator
        assertEquals(0f, RouteMotion.at(id, route, 0.8f, options).vehicle()!!.rotation.let { if (it > 180f) it - 360f else it }, 1e-3f) // then north
    }

    @Test
    fun aPictureThatDoesNotPointNorthIsTurnedByTheOffset() {
        val shapes = RouteMotion.at(id, route, 0.2f, options.copy(vehicleTurn = -90f))
        assertEquals(0f, shapes.vehicle()!!.rotation.let { if (it > 180f) it - 360f else it }, 1e-3f)
        assertEquals(270f, RouteMotion.at(id, route, 0.2f, options.copy(vehicleTurn = 180f)).vehicle()!!.rotation, 1e-3f)
        assertTrue(RouteMotion.at(id, route, 0.2f, options.copy(vehicleTurn = 720f)).vehicle()!!.rotation in 0f..360f)
    }

    @Test
    fun theSpeedIsTheSameOnALongLegAsOnAShortOne() {
        val uneven = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 10.0), GeoPosition(0.0, 90.0))
        // 10 of 90 degrees is the first leg: at a ninth of the time the vehicle is at the corner
        assertTrue(near(uneven[1], RouteMotion.at(id, uneven, 1f / 9, options).vehicle()!!.position, 1e-3))
    }

    @Test
    fun theTripIsOverAtOneWithTheLineKeptAndNoVehicleByDefault() {
        val shapes = RouteMotion.at(id, route, 1f, options)
        assertEquals(null, shapes.vehicle())
        assertEquals(route, shapes.lines().single().vertices.map { it.position })
    }

    @Test
    fun whatIsKeptWhenTheTripIsOverIsUpToTheOptions() {
        val both = RouteMotion.at(id, route, 1f, options.copy(keepVehicle = true))
        assertTrue(near(route.last(), both.vehicle()!!.position))
        assertEquals(0, RouteMotion.at(id, route, 1f, options.copy(keepTrail = false)).size)
        assertEquals(0, RouteMotion.at(id, route, 1f, options.copy(keepTrail = false)).lines().size)
    }

    @Test
    fun noVehicleWhenThereIsNone() {
        assertEquals(null, RouteMotion.at(id, route, 0.4f, options.copy(vehicle = null)).vehicle())
        assertEquals(1, RouteMotion.at(id, route, 0.4f, options.copy(vehicle = null)).lines().size)
    }

    @Test
    fun theLineHasTheColourAndWidthAsked() {
        val line = RouteMotion.at(id, route, 0.4f, options.copy(lineColor = ColorRgba(1, 2, 3), lineWidth = 7f)).lines().single()
        assertEquals(ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), line.style)
        assertEquals(7f, line.lineWidth)
    }

    @Test
    fun theTrailCoolsFromLightAtTheVehicleToTheLineColourBehindIt() {
        val red = ColorRgba(200, 0, 0)
        val shapes = RouteMotion.at(id, route, 0.8f, options.copy(lineColor = red, headColor = ColorRgba(255, 220, 220), trailShare = 0.3f, trailBands = 4))
        val bands = shapes.lines().filter { "." in it.id.id }
        assertEquals(4, bands.size)
        val greens = bands.map { (it.style as ShapeStyle.SolidColor).color.green }
        assertEquals(greens.sorted(), greens)
        assertTrue(greens.last() > greens.first())
        assertEquals(ShapeStyle.SolidColor(red), shapes.lines().single { it.id.id == "r#trail" }.style)
        bands.zipWithNext { a, b -> assertTrue(near(a.vertices.last().position, b.vertices.first().position)) }
    }

    @Test
    fun aGlowAddsLightUnderTheLineAndAHotCoreOnTopAndTheTripEndsWithoutCooling() {
        val neon = options.copy(glow = 1.5f)
        val going = RouteMotion.at(id, route, 0.5f, neon).associateBy { it.id.id }
        assertTrue("r#trail~h0" in going && "r#trail" in going && "r#trail~c" in going)
        assertTrue(going.getValue("r#trail~h0").zIndex < going.getValue("r#trail").zIndex)
        assertTrue(going.getValue("r#trail").zIndex < going.getValue("r#trail~c").zIndex)
        assertTrue(going.getValue("r#trail~c").zIndex < going.getValue("r#vehicle").zIndex)
        val over = RouteMotion.at(id, route, 1f, neon.copy(trailShare = 0.3f))
        assertTrue(over.none { "." in it.id.id }, "no bands: the line has cooled")
    }

    @Test
    fun theIdsAreMadeFromTheRoutesAndAreUnique() {
        for (t in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) {
            val ids = RouteMotion.at(id, route, t, RouteOptions(vehicle = plane, keepVehicle = true)).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "t $t")
            assertTrue(ids.all { it.id.startsWith("r#") })
        }
    }

    @Test
    fun theLineOnlyGrows() {
        var before = 0
        for (i in 1..40) {
            val line = RouteMotion.at(id, route, i / 40f, options).lines().single()
            assertTrue(line.vertices.size >= before)
            before = line.vertices.size
        }
    }

    @Test
    fun theVehicleIsOnTopOfTheLineAndOfTheOptionsZIndex() {
        val shapes = RouteMotion.at(id, route, 0.4f, options.copy(zIndex = 500))
        assertTrue(shapes.vehicle()!!.zIndex > shapes.lines().maxOf { it.zIndex })
        assertTrue(shapes.all { it.zIndex >= 500 })
    }

    @Test
    fun aRouteOfTwoPointsIsAStraightTrip() {
        val shapes = RouteMotion.at(id, listOf(GeoPosition(10.0, 10.0), GeoPosition(10.0, 20.0)), 0.5f, options)
        assertEquals(2, shapes.lines().single().vertices.size)
        assertTrue(shapes.vehicle()!!.position.longitude in 14.0..16.0)
    }

    @Test
    fun badInputIsRefused() {
        assertFailsWith<IllegalArgumentException> { RouteMotion.at(id, listOf(GeoPosition(0.0, 0.0)), 0.5f) }
        assertFailsWith<IllegalArgumentException> { RouteMotion.at(id, route, Float.NaN) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(lineWidth = 0f) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(trailShare = 2f) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(trailBands = 0) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(glow = -1f) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(vehicleSize = 0f) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(vehicleTurn = Float.NaN) }
    }

    @Test
    fun theRoutePointsAreNotChanged() {
        val before = route.toList()
        RouteMotion.at(id, route, 0.7f, RouteOptions(vehicle = plane))
        assertEquals(before, route)
    }

    // --- the bulge, the lift and the shadow ---

    @Test
    fun aBulgingLegLeavesAndArrivesAtItsPointsAndBulgesToTheRightInTheMiddle() {
        val east = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0))
        val line = RouteMotion.at(id, east, 1f, options.copy(arc = 0.25f, arcSteps = 8)).lines().single()
        assertEquals(east.first(), line.vertices.first().position)
        assertEquals(east.last(), line.vertices.last().position)
        assertEquals(9, line.vertices.size)
        val middle = line.vertices[4].position
        assertTrue(middle.latitude < -9.0 && middle.latitude > -11.0, "to the right of going east is south: ${middle.latitude}") // 0.25 of 40 degrees
        assertTrue(abs(middle.longitude - 20.0) < 0.5)
    }

    @Test
    fun aNegativeArcBulgesTheOtherWayAndNoArcIsAStraightLeg() {
        val east = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0))
        assertTrue(RouteMotion.at(id, east, 1f, options.copy(arc = -0.25f, arcSteps = 8)).lines().single().vertices[4].position.latitude > 9.0)
        assertEquals(2, RouteMotion.at(id, east, 1f, options).lines().single().vertices.size)
    }

    @Test
    fun theVehicleHeadingChangesAllAlongABulgingLeg() {
        val east = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0))
        val bulge = options.copy(arc = 0.3f)
        val start = RouteMotion.at(id, east, 0.02f, bulge).vehicle()!!.rotation
        val middle = RouteMotion.at(id, east, 0.5f, bulge).vehicle()!!.rotation
        val end = RouteMotion.at(id, east, 0.98f, bulge).vehicle()!!.rotation
        assertTrue(start > middle && middle > end, "$start $middle $end") // it leaves to the south of east (more than 90) and arrives to the north of it
        assertTrue(abs(middle - 90f) < 5f)
    }

    @Test
    fun theVehicleIsBiggestInTheMiddleOfALegAndItsNormalSizeAtThePoints() {
        val lifted = options.copy(lift = 0.5f, vehicleSize = 40f)
        assertEquals(40f, RouteMotion.at(id, route, 0f, lifted).vehicle()!!.size, 1e-3f)
        assertEquals(60f, RouteMotion.at(id, route, 0.25f, lifted).vehicle()!!.size, 0.5f) // the middle of the first leg
        assertEquals(60f, RouteMotion.at(id, route, 0.75f, lifted).vehicle()!!.size, 0.5f) // and of the second
        assertEquals(40f, RouteMotion.at(id, route, 0.5f, lifted).vehicle()!!.size, 0.5f) // on the ground at the corner between them
    }

    @Test
    fun theShadowStaysOnTheGroundUnderTheVehicleAtThePointsAndMovesAwayAsItRises() {
        val shadow = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 90))
        val lifted = options.copy(lift = 0.5f, height = 0.1f, shadow = shadow)
        val onTheGround = RouteMotion.at(id, route, 0f, lifted)
        assertTrue(near(onTheGround.vehicle()!!.position, onTheGround.filterIsInstance<DrawnMarker>().single { it.id.id == "r#shadow" }.position))
        val up = RouteMotion.at(id, route, 0.25f, lifted)
        val shadowUp = up.filterIsInstance<DrawnMarker>().single { it.id.id == "r#shadow" }
        assertTrue(!near(up.vehicle()!!.position, shadowUp.position, 1e-3))
        assertTrue(shadowUp.zIndex < up.vehicle()!!.zIndex)
        assertEquals(shadow, shadowUp.style)
        assertEquals(up.vehicle()!!.rotation, shadowUp.rotation)
    }

    @Test
    fun noShadowUnlessGivenAndNoneOnceTheTripIsOver() {
        assertEquals(1, RouteMotion.at(id, route, 0.4f, options).filterIsInstance<DrawnMarker>().size)
        val withShadow = options.copy(shadow = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 90)), keepVehicle = false)
        assertEquals(0, RouteMotion.at(id, route, 1f, withShadow).filterIsInstance<DrawnMarker>().size)
    }

    @Test
    fun theIdsOfABulgingTripAreStillUnique() {
        val full = RouteOptions(vehicle = plane, shadow = plane, keepVehicle = true, arc = 0.2f, lift = 0.3f)
        for (t in listOf(0f, 0.3f, 0.6f, 1f)) {
            val ids = RouteMotion.at(id, route, t, full).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "t $t")
        }
    }

    @Test
    fun badBulgeOptionsAreRefused() {
        assertFailsWith<IllegalArgumentException> { RouteOptions(arc = 2f) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(arc = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(arcSteps = 1) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(lift = -0.1f) }
    }

    // --- the arch into the air ---

    private val east = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0))
    private val metersPerDegree = 6_371_000.0 * kotlin.math.PI / 180.0

    @Test
    fun theLineArchesUpIntoTheAirAndComesDownAtTheEnd() {
        val line = RouteMotion.at(id, east, 1f, options.copy(height = 0.1f, arcSteps = 8)).lines().single()
        val altitudes = line.vertices.map { it.position.altitude }
        assertEquals(0.0, altitudes.first())
        assertEquals(0.0, altitudes.last())
        // the top is a tenth of the leg's length (40 degrees) up
        assertTrue(abs(altitudes.max() - 0.1 * 40 * metersPerDegree) < 1.0, "was ${altitudes.max()}")
        assertEquals(altitudes.max(), altitudes[4])
        assertTrue(altitudes.take(5) == altitudes.take(5).sorted() && altitudes.drop(4) == altitudes.drop(4).sortedDescending())
        assertEquals(east.map { it.latitude }, listOf(line.vertices.first().position.latitude, line.vertices.last().position.latitude))
    }

    @Test
    fun theVehicleFliesAtTheAltitudeOfTheArchAndIsOnTheGroundAtThePoints() {
        val flying = options.copy(height = 0.1f)
        assertEquals(0.0, RouteMotion.at(id, east, 0f, flying).vehicle()!!.position.altitude, 1e-6)
        val top = RouteMotion.at(id, east, 0.5f, flying).vehicle()!!.position
        assertTrue(abs(top.altitude - 0.1 * 40 * metersPerDegree) < 1000.0, "was ${top.altitude}")
        assertTrue(abs(top.longitude - 20.0) < 0.2)
        val atTheCorner = RouteMotion.at(id, route, 0.5f, flying).vehicle()!!.position
        assertTrue(atTheCorner.altitude < 5_000.0, "on the ground at the corner: ${atTheCorner.altitude}")
    }

    @Test
    fun aLongerLegArchesHigher() {
        val short = RouteMotion.at(id, listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 10.0)), 0.5f, options.copy(height = 0.1f)).vehicle()!!.position.altitude
        val long = RouteMotion.at(id, listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0)), 0.5f, options.copy(height = 0.1f)).vehicle()!!.position.altitude
        assertTrue(abs(long / short - 4.0) < 0.1)
    }

    @Test
    fun noHeightIsATripOnTheGround() {
        val line = RouteMotion.at(id, east, 1f, options.copy(arcSteps = 8)).lines().single()
        assertEquals(2, line.vertices.size)
        assertTrue(line.vertices.all { it.position.altitude == 0.0 })
    }

    @Test
    fun theShadowIsOnTheGroundAndMovesOffAsTheVehicleRises() {
        val shadow = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 90))
        val flying = options.copy(height = 0.1f, shadow = shadow)
        val atTheTop = RouteMotion.at(id, east, 0.5f, flying)
        val onTheGround = atTheTop.filterIsInstance<DrawnMarker>().single { it.id.id == "r#shadow" }
        assertEquals(0.0, onTheGround.position.altitude)
        assertTrue(!near(onTheGround.position, atTheTop.vehicle()!!.position, 1e-3))
        val atTheStart = RouteMotion.at(id, east, 0f, flying)
        assertTrue(near(atTheStart.filterIsInstance<DrawnMarker>().single { it.id.id == "r#shadow" }.position, atTheStart.vehicle()!!.position))
    }

    @Test
    fun theHeightCanBeCombinedWithABulgeToTheSide() {
        val line = RouteMotion.at(id, east, 1f, options.copy(height = 0.1f, arc = 0.2f, arcSteps = 8)).lines().single()
        assertTrue(line.vertices[4].position.latitude < -6.0)
        assertTrue(line.vertices[4].position.altitude > 100_000.0)
    }

    @Test
    fun aNegativeHeightIsRefused() {
        assertFailsWith<IllegalArgumentException> { RouteOptions(height = -0.1f) }
        assertFailsWith<IllegalArgumentException> { RouteOptions(height = Float.NaN) }
    }
}
