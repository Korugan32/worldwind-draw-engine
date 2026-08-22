package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class GeoPositionMapperTest {
    @Test
    fun toWwkPositionCarriesAllThreeComponents() {
        val wwkPosition = GeoPosition(latitude = 39.5, longitude = 35.1, altitude = 1200.0).toWwkPosition()

        assertEquals(39.5, wwkPosition.latitude.inDegrees)
        assertEquals(35.1, wwkPosition.longitude.inDegrees)
        assertEquals(1200.0, wwkPosition.altitude)
    }

    @Test
    fun toGeoPositionCarriesAllThreeComponents() {
        val wwkPosition = GeoPosition(latitude = -12.0, longitude = -48.25, altitude = 300.0).toWwkPosition()

        val geoPosition = wwkPosition.toGeoPosition()

        assertEquals(-12.0, geoPosition.latitude)
        assertEquals(-48.25, geoPosition.longitude)
        assertEquals(300.0, geoPosition.altitude)
    }

    @Test
    fun roundTripsThroughBothConversions() {
        val original = GeoPosition(latitude = 41.0082, longitude = 28.9784, altitude = 42.0)

        val roundTripped = original.toWwkPosition().toGeoPosition()

        assertEquals(original, roundTripped)
    }
}
