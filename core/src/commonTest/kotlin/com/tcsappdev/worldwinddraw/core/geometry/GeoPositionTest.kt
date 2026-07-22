package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeoPositionTest {
    @Test
    fun acceptsValidCoordinates() {
        val position = GeoPosition(32.0, 90.0)
        assertEquals(32.0, position.latitude)
        assertEquals(90.0, position.longitude)
    }

    @Test
    fun rejectsLatitudeOutOfRange() {
        // 91.0 is just 0.1 past the valid -90.0..90.0 bound, catches off-by-one mistakes in
        // the range check (e.g. accidentally using `..90.0` vs `<90.0`).
        assertFailsWith<IllegalArgumentException> {
            GeoPosition(91.0, 90.0)
        }
    }

    @Test
    fun rejectsLongitudeOutOfRange() {
        // Same boundary idea as above, just past the -180.0..180.0 bound.
        assertFailsWith<IllegalArgumentException> {
            GeoPosition(32.0, 181.0)
        }
    }
}