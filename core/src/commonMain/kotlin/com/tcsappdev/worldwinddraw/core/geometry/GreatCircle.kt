package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.abs
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
public object GreatCircle {
    /**
     * The points strictly between [from] and [to] along the great circle joining them, spaced at
     * most [maxStepDegrees] apart (and none at all if the edge is already shorter than that).
     * Altitude goes straight from [from]'s to [to]'s. Two points that are identical or exactly opposite have no unique
     * great circle; they yield no intermediate points.
     */
    public fun intermediatePoints(from: GeoPosition, to: GeoPosition, maxStepDegrees: Double): List<GeoPosition> {
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
                .copy(altitude = from.altitude + (to.altitude - from.altitude) * t)
        }
    }

    /**
     * The angle at the centre of the globe between [a] and [b], in radians (0 for the same point, PI for opposite points): the length of the
     * great-circle arc on a sphere of radius 1. Only lengths measured this way can be compared with each other, wherever the edges are.
     */
    public fun angleBetween(a: GeoPosition, b: GeoPosition): Double {
        val u = unitVector(a)
        val v = unitVector(b)
        // atan2 of the length of the cross product and the dot product: acos of the dot product alone loses nearly all its digits for
        // points that are close (two identical points would come out about 1e-8 apart).
        val cx = u[1] * v[2] - u[2] * v[1]
        val cy = u[2] * v[0] - u[0] * v[2]
        val cz = u[0] * v[1] - u[1] * v[0]
        return atan2(sqrt(cx * cx + cy * cy + cz * cz), u[0] * v[0] + u[1] * v[1] + u[2] * v[2])
    }

    /**
     * The point [fraction] (0 = [from], 1 = [to]) of the way along the great-circle arc from [from] to [to]; altitude goes straight from one
     * to the other. Two points that are identical or exactly opposite have no unique arc, the answer is then [from].
     */
    public fun pointAt(from: GeoPosition, to: GeoPosition, fraction: Double): GeoPosition {
        val a = unitVector(from)
        val b = unitVector(to)
        val angle = angleBetween(from, to)
        val sinAngle = sin(angle)
        if (sinAngle < 1e-12) return from
        val wa = sin((1 - fraction) * angle) / sinAngle
        val wb = sin(fraction * angle) / sinAngle
        val onSphere = toGeoPosition(wa * a[0] + wb * b[0], wa * a[1] + wb * b[1], wa * a[2] + wb * b[2])
        return onSphere.copy(altitude = from.altitude + (to.altitude - from.altitude) * fraction)
    }

    /**
     * The direction to take at [from] to go to [to] along the great circle, in radians clockwise from north (0 north, PI/2 east), between 0
     * and 2 PI. For two identical (or exactly opposite) points there is no direction and the answer is 0.
     */
    public fun bearing(from: GeoPosition, to: GeoPosition): Double {
        val lat1 = from.latitude * PI / 180.0
        val lat2 = to.latitude * PI / 180.0
        val dLon = (to.longitude - from.longitude) * PI / 180.0
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        if (abs(x) < 1e-15 && abs(y) < 1e-15) return 0.0
        val bearing = atan2(y, x)
        return if (bearing < 0.0) bearing + 2 * PI else bearing
    }

    /**
     * The point [angle] radians (an arc on a sphere of radius 1, see [angleBetween]) away from [from], going [bearing] radians clockwise from
     * north along a great circle. Altitude is [from]'s.
     */
    public fun destination(from: GeoPosition, bearing: Double, angle: Double): GeoPosition {
        val lat1 = from.latitude * PI / 180.0
        val lon1 = from.longitude * PI / 180.0
        val sinLat2 = (sin(lat1) * cos(angle) + cos(lat1) * sin(angle) * cos(bearing)).coerceIn(-1.0, 1.0)
        val lat2 = asin(sinLat2)
        val lon2 = lon1 + atan2(sin(bearing) * sin(angle) * cos(lat1), cos(angle) - sin(lat1) * sinLat2)
        var lon = toDegrees(lon2)
        while (lon > 180.0) lon -= 360.0
        while (lon < -180.0) lon += 360.0
        return GeoPosition(toDegrees(lat2).coerceIn(-90.0, 90.0), lon.coerceIn(-180.0, 180.0), from.altitude)
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
