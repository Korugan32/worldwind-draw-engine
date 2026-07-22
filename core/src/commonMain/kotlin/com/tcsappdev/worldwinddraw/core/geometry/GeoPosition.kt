package com.tcsappdev.worldwinddraw.core.geometry

/**
 * An immutable location on the globe, expressed in degrees of latitude/longitude and
 * meters of altitude above the reference ellipsoid.
 *
 * This is the base geographic type every other type in this library builds on (a
 * [Vertex]'s position, a marker's location, a polygon's boundary points, etc.).
 *
 * @property latitude degrees north of the equator, must be within `-90.0..90.0`.
 * @property longitude degrees east of the prime meridian, must be within `-180.0..180.0`.
 * @property altitude meters above the reference ellipsoid; defaults to sea level (`0.0`).
 * @throws IllegalArgumentException if [latitude] or [longitude] is out of range.
 */
data class GeoPosition(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
    }
}