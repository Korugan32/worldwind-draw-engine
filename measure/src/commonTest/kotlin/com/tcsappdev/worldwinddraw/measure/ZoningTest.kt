package com.tcsappdev.worldwinddraw.measure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoningTest {
    private val limits = ZoningLimits(taks = 0.30, kaks = 0.60)

    @Test
    fun taksAndEmsalAreTheAreasOverTheAreaOfThePlot() {
        assertEquals(0.25, Zoning.taks(footprint = 250.0, parcelArea = 1_000.0), 1e-12)
        assertEquals(0.9, Zoning.kaks(totalArea = 900.0, parcelArea = 1_000.0), 1e-12)
    }

    @Test
    fun aPlotOfADonumWithTaks30AndEmsal60AllowsAThirdOfItCoveredAndTwoFloors() {
        val allowance = Zoning.allowance(parcelArea = 1_000.0, limits = limits)
        assertEquals(300.0, allowance.maxFootprint, 1e-9)
        assertEquals(600.0, allowance.maxTotalArea, 1e-9)
        assertEquals(2.0, allowance.floors, 1e-9)
        assertEquals(2, allowance.wholeFloors)
    }

    @Test
    fun theSetbacksCanLeaveLessRoomThanTheTaksAllows() {
        // The taks would allow 300 m2 of footprint, but the setbacks leave 200 m2 to build on.
        val allowance = Zoning.allowance(1_000.0, limits, buildableArea = 200.0)
        assertEquals(200.0, allowance.maxFootprint, 1e-9)
        assertEquals(600.0, allowance.maxTotalArea, 1e-9)
        assertEquals(3.0, allowance.floors, 1e-9) // the same total on a smaller footprint: more floors
    }

    @Test
    fun theSetbacksDoNotRaiseTheFootprintAboveTheTaks() {
        assertEquals(300.0, Zoning.allowance(1_000.0, limits, buildableArea = 900.0).maxFootprint, 1e-9)
    }

    @Test
    fun aFractionOfAFloorIsNotAWholeFloor() {
        val allowance = Zoning.allowance(1_000.0, ZoningLimits(0.30, 0.75))
        assertEquals(2.5, allowance.floors, 1e-9)
        assertEquals(2, allowance.wholeFloors)
    }

    @Test
    fun nothingCanBeBuiltWhereNothingIsLeft() {
        val allowance = Zoning.allowance(1_000.0, limits, buildableArea = 0.0)
        assertEquals(0.0, allowance.maxFootprint)
        assertEquals(0.0, allowance.floors)
        assertEquals(0, allowance.wholeFloors)
    }

    @Test
    fun aBuildingWithinTheLimitsIsAllowedAndOneOverThemIsNot() {
        val ok = Zoning.check(1_000.0, footprint = 280.0, totalArea = 560.0, limits = limits)
        assertTrue(ok.allowed)
        assertEquals(0.28, ok.taks, 1e-12)
        assertEquals(0.56, ok.kaks, 1e-12)

        val tooWide = Zoning.check(1_000.0, footprint = 320.0, totalArea = 560.0, limits = limits)
        assertFalse(tooWide.footprintAllowed); assertTrue(tooWide.totalAllowed); assertFalse(tooWide.allowed)

        val tooMuch = Zoning.check(1_000.0, footprint = 280.0, totalArea = 650.0, limits = limits)
        assertTrue(tooMuch.footprintAllowed); assertFalse(tooMuch.totalAllowed); assertFalse(tooMuch.allowed)
    }

    @Test
    fun aBuildingExactlyAtTheLimitIsAllowed() {
        assertTrue(Zoning.check(1_000.0, 300.0, 600.0, limits).allowed)
    }

    @Test
    fun aFootprintOutsideTheBuildableAreaIsNotAllowedEvenIfTheTaksIs() {
        val check = Zoning.check(1_000.0, footprint = 250.0, totalArea = 500.0, limits = limits, buildableArea = 200.0)
        assertFalse(check.footprintAllowed)
    }

    @Test
    fun badLimitsAndAreasAreRefused() {
        assertFailsWith<IllegalArgumentException> { ZoningLimits(0.0, 0.6) }
        assertFailsWith<IllegalArgumentException> { ZoningLimits(1.2, 0.6) }
        assertFailsWith<IllegalArgumentException> { ZoningLimits(0.3, 0.0) }
        assertFailsWith<IllegalArgumentException> { ZoningLimits(Double.NaN, 0.6) }
        assertFailsWith<IllegalArgumentException> { Zoning.taks(100.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Zoning.kaks(-1.0, 100.0) }
        assertFailsWith<IllegalArgumentException> { Zoning.allowance(0.0, limits) }
        assertFailsWith<IllegalArgumentException> { Zoning.allowance(1_000.0, limits, buildableArea = -1.0) }
    }

    @Test
    fun aTaksOfOneIsTheMostThatMakesSense() {
        assertEquals(1.0, ZoningLimits(1.0, 2.0).taks)
        assertEquals(1_000.0, Zoning.allowance(1_000.0, ZoningLimits(1.0, 2.0)).maxFootprint, 1e-9)
    }
}
