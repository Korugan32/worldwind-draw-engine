package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle (shortest path on a sphere) math. WWK draws a shape's edges as great-circle arcs,
 * which are NOT straight lines in latitude/longitude — over a long edge the two visibly differ — so
 * anything that has to line up with a drawn edge must follow the arc, not the straight line.
 */
object GreatCircle {
    /**
     * The points strictly between [from] and [to] along the great circle joining them, spaced at
     * most [maxStepDegrees] apart (and none at all if the edge is already shorter than that).
     * Altitudes are ignored. Two points that are identical or exactly opposite have no unique
     * great circle; they yield no intermediate points.
     */
    fun intermediatePoints(from: GeoPosition, to: GeoPosition, maxStepDegrees: Double): List<GeoPosition> {
        require(maxStepDegrees > 0.0) { "maxStepDegrees must be positive" }
        val a = unitVector(from)
        val b = unitVector(to)
        val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1.0, 1.0)
        val angle = acos(dot)
        val sinAngle = sin(angle)
        if (sinAngle < 1e-12) return emptyList()

        // The small tolerance keeps an edge that is an exact multiple of the step (40 degrees, step 10)
        // from getting an extra point because of floating-point noise (4.0000000001 steps).
        val steps = ceil(toDegrees(angle) / maxStepDegrees - 1e-9).toInt()
        if (steps <= 1) return emptyList()
        return (1 until steps).map { i ->
            val t = i.toDouble() / steps
            // Spherical linear interpolation: stays on the unit sphere, constant angular speed.
            val wa = sin((1 - t) * angle) / sinAngle
            val wb = sin(t * angle) / sinAngle
            toGeoPosition(wa * a[0] + wb * b[0], wa * a[1] + wb * b[1], wa * a[2] + wb * b[2])
        }
    }

    private fun toDegrees(radians: Double) = radians * 180.0 / PI

    private fun unitVector(p: GeoPosition): DoubleArray {
        val lat = p.latitude * PI / 180.0
        val lon = p.longitude * PI / 180.0
        return doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
    }

    private fun toGeoPosition(x: Double, y: Double, z: Double): GeoPosition {
        val length = sqrt(x * x + y * y + z * z)
        val lat = toDegrees(asin((z / length).coerceIn(-1.0, 1.0)))
        val lon = toDegrees(atan2(y, x))
        return GeoPosition(lat.coerceIn(-90.0, 90.0), lon.coerceIn(-180.0, 180.0))
    }
}
