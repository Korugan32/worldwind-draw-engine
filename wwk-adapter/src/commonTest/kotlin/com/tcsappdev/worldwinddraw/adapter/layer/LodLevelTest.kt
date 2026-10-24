package com.tcsappdev.worldwinddraw.adapter.layer

import kotlin.test.Test
import kotlin.test.assertEquals

class LodLevelTest {
    @Test
    fun belowTheBaseToleranceNothingIsDropped() {
        assertEquals(LodLevel.NONE, LodLevel.forTolerance(1.0, LodLevel.NONE))
        assertEquals(LodLevel.NONE, LodLevel.forTolerance(9.0, LodLevel.NONE))
    }

    @Test
    fun theToleranceDoublesWithEveryLevel() {
        assertEquals(10.0, LodLevel.toleranceMeters(0))
        assertEquals(80.0, LodLevel.toleranceMeters(3))
        assertEquals(10_240.0, LodLevel.toleranceMeters(10))
    }

    @Test
    fun aGrowingToleranceMovesUpOneLevelPerDoubling() {
        assertEquals(LodLevel.NONE, LodLevel.forTolerance(10.0, LodLevel.NONE)) // at the base but inside the margin
        assertEquals(0, LodLevel.forTolerance(13.0, LodLevel.NONE))
        assertEquals(0, LodLevel.forTolerance(21.0, 0)) // over the edge at 20 m but inside the margin
        assertEquals(1, LodLevel.forTolerance(26.0, 0))
        assertEquals(5, LodLevel.forTolerance(330.0, LodLevel.NONE)) // 10 m * 2^5 = 320
    }

    @Test
    fun theLevelDoesNotFlapWhileTheCameraHoversAtAnEdge() {
        // Level 3 covers 80 m up to 160 m; the edge to level 4 is at 160 m. Just across it (by less than a quarter of a level) stays 3.
        assertEquals(3, LodLevel.forTolerance(160.0 * 1.1, 3))
        assertEquals(3, LodLevel.forTolerance(79.0, 3)) // and just under 80 m too
        // Clearly past the edge: it moves.
        assertEquals(4, LodLevel.forTolerance(160.0 * 1.3, 3))
        assertEquals(2, LodLevel.forTolerance(80.0 * 0.8, 3))
    }

    @Test
    fun comingBackDownToTheBaseFallsToNone() {
        assertEquals(LodLevel.NONE, LodLevel.forTolerance(5.0, 0))
        assertEquals(0, LodLevel.forTolerance(9.0, 0)) // not yet: within the quarter-level margin
    }
}
