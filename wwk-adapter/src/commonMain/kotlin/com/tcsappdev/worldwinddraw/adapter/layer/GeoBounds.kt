package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.Vertex

/**
 * A box in latitude/longitude degrees, used to tell which shapes are anywhere near what the camera
 * shows. It is deliberately rough: a shape that might be on screen must never be reported as off screen,
 * while the odd shape reported on screen that isn't only costs a little work.
 *
 * Longitudes are not wrapped around the antimeridian. A box that is wider than [WIDE_DEGREES] might be
 * wrapping (or covers most of the globe anyway), so it is treated as spanning every longitude.
 */
internal class GeoBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
    val minLat = minLat
    val maxLat = maxLat
    val minLon: Double
    val maxLon: Double

    init {
        val wide = maxLon - minLon > WIDE_DEGREES
        this.minLon = if (wide) -180.0 else minLon
        this.maxLon = if (wide) 180.0 else maxLon
    }

    /** True when the two boxes overlap or touch. */
    fun intersects(other: GeoBounds): Boolean =
        minLat <= other.maxLat && maxLat >= other.minLat && minLon <= other.maxLon && maxLon >= other.minLon

    /** This box grown by [fraction] of its own size on every side (so a shape just outside it is still counted). */
    fun grownBy(fraction: Double): GeoBounds {
        val dLat = (maxLat - minLat) * fraction
        val dLon = (maxLon - minLon) * fraction
        return GeoBounds((minLat - dLat).coerceAtLeast(-90.0), (maxLat + dLat).coerceAtMost(90.0), minLon - dLon, maxLon + dLon)
    }

    companion object {
        const val WIDE_DEGREES = 180.0

        /** The box around [vertices]' positions. */
        fun of(vertices: List<Vertex>): GeoBounds {
            var minLat = 90.0
            var maxLat = -90.0
            var minLon = 180.0
            var maxLon = -180.0
            for (v in vertices) {
                val p = v.position
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            return GeoBounds(minLat, maxLat, minLon, maxLon)
        }
    }
}
