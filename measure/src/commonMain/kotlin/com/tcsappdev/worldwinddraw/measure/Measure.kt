package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Wgs84
import com.tcsappdev.worldwinddraw.core.geometry.GreatCircle
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Lengths and areas of what is drawn, in metres and square metres, on the WGS84 ellipsoid.
 *
 * - **A distance** is the length of the shortest line on the ellipsoid between two points (a geodesic), found with Vincenty's inverse
 *   formula, which is good to a fraction of a millimetre. It fails to settle for two points that are almost opposite on the globe; the
 *   distance is then taken on a ball of the mean radius, which is no worse than a few kilometres out at that range and no use to anyone
 *   measuring land.
 * - **An area** is the area the shape has on the ellipsoid, found by measuring it on the ball that has the same surface (using the authalic
 *   latitudes, see [Wgs84]). The edges of a shape are great-circle arcs, as WorldWind draws them. For a plot of land (up to some tens of
 *   kilometres across) the result is the area a surveyor gets, to better than a millionth of it.
 *
 * Altitude is ignored: everything is measured at sea level.
 */
public object Measure {
    private const val TO_RADIANS = PI / 180.0
    private const val ITERATIONS = 200
    private const val CONVERGED = 1e-12

    /** The distance between [a] and [b] along the surface, in metres (0 for the same point). */
    public fun distance(a: GeoPosition, b: GeoPosition): Double = vincenty(a, b) ?: (GreatCircle.angleBetween(a, b) * MEAN_RADIUS)

    /** The length of the line through [points], in metres: the distances between each point and the next; 0 for fewer than 2 points. */
    public fun length(points: List<GeoPosition>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        return total
    }

    /** The lengths of the edges of the ring [ring], in metres, one for each corner (the edge that starts at it), the last edge closing the ring. */
    public fun edgeLengths(ring: List<GeoPosition>): List<Double> =
        if (ring.size < 2) emptyList() else List(ring.size) { i -> distance(ring[i], ring[(i + 1) % ring.size]) }

    /** The length of the outline of [ring], in metres: its edges, the last one back to the first corner. */
    public fun perimeter(ring: List<GeoPosition>): Double = edgeLengths(ring).sum()

    /**
     * The area enclosed by [ring], in square metres: the same whichever way round it goes. A ring of fewer than 3 points encloses nothing
     * (0). For a ring that crosses itself the result is the signed sum, which is not what anyone means by its area: a drawing has to be a
     * simple ring.
     */
    public fun area(ring: List<GeoPosition>): Double {
        if (ring.size < 3) return 0.0
        val vectors = ring.map { unitVector(it) }
        // A fan of spherical triangles from the first corner. Each one is a signed area (the sign says which way round it goes), so the sum is
        // right for any simple ring, convex or not.
        var excess = 0.0
        val a = vectors[0]
        for (i in 1 until vectors.size - 1) excess += triangle(a, vectors[i], vectors[i + 1])
        return abs(excess) * Wgs84.AUTHALIC_RADIUS * Wgs84.AUTHALIC_RADIUS
    }

    /**
     * The area of [polygon], in square metres, with the holes taken out and the separate parts added, as the shape is filled (see
     * [DrawnPolygon]).
     */
    public fun area(polygon: DrawnPolygon): Double {
        val parts = PolygonParts.split(polygon.rings.map { ring -> ring.map { it.position } })
        var total = 0.0
        for (part in parts) {
            total += area(part.first())
            for (hole in part.drop(1)) total -= area(hole)
        }
        return total
    }

    /** The mean radius of the Earth, in metres: what [distance] falls back to for two points that are nearly opposite. */
    private const val MEAN_RADIUS: Double = 6_371_008.8

    /** The point as a vector on the unit ball, with its latitude turned into the authalic one (see [Wgs84.authalicSine]). */
    private fun unitVector(p: GeoPosition): DoubleArray {
        val sinBeta = Wgs84.authalicSine(p.latitude).coerceIn(-1.0, 1.0)
        val cosBeta = sqrt(1.0 - sinBeta * sinBeta)
        val lon = p.longitude * TO_RADIANS
        return doubleArrayOf(cosBeta * cos(lon), cosBeta * sin(lon), sinBeta)
    }

    /**
     * The signed area (on the unit ball) of the spherical triangle [a], [b], [c]: Van Oosterom and Strackee's formula, which stays exact for
     * a triangle as small as a plot of land, where the usual "sum of the angles minus pi" would lose every digit.
     */
    private fun triangle(a: DoubleArray, b: DoubleArray, c: DoubleArray): Double {
        // a . (b x c) is also a . ((b - a) x (c - a)), and the second form multiplies small numbers (the corners of a plot are a hair apart on the
        // ball) instead of taking the small difference of big products, which would lose most of the digits for a plot of a few metres.
        val bx = b[0] - a[0]; val by = b[1] - a[1]; val bz = b[2] - a[2]
        val cx = c[0] - a[0]; val cy = c[1] - a[1]; val cz = c[2] - a[2]
        val triple = a[0] * (by * cz - bz * cy) - a[1] * (bx * cz - bz * cx) + a[2] * (bx * cy - by * cx)
        val ab = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val bc = b[0] * c[0] + b[1] * c[1] + b[2] * c[2]
        val ca = c[0] * a[0] + c[1] * a[1] + c[2] * a[2]
        return 2.0 * atan2(triple, 1.0 + ab + bc + ca)
    }

    /** Vincenty's inverse formula: the geodesic distance in metres, or null if it does not settle (the points are almost opposite). */
    private fun vincenty(p: GeoPosition, q: GeoPosition): Double? {
        if (p.latitude == q.latitude && p.longitude == q.longitude) return 0.0
        val f = Wgs84.F
        val u1 = atan((1.0 - f) * tan(p.latitude * TO_RADIANS))
        val u2 = atan((1.0 - f) * tan(q.latitude * TO_RADIANS))
        var difference = (q.longitude - p.longitude) * TO_RADIANS
        // the short way round
        if (difference > PI) difference -= 2 * PI else if (difference < -PI) difference += 2 * PI
        val sinU1 = sin(u1); val cosU1 = cos(u1)
        val sinU2 = sin(u2); val cosU2 = cos(u2)

        var lambda = difference
        var sinSigma = 0.0; var cosSigma = 0.0; var sigma = 0.0
        var cosSqAlpha = 0.0; var cos2SigmaM = 0.0
        var settled = false
        for (n in 0 until ITERATIONS) {
            val sinLambda = sin(lambda); val cosLambda = cos(lambda)
            val x = cosU2 * sinLambda
            val y = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda
            sinSigma = sqrt(x * x + y * y)
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha
            // along the equator cosSqAlpha is 0 and the term is left out
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha))
            val previous = lambda
            lambda = difference + (1.0 - c) * f * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - previous) < CONVERGED) { settled = true; break }
        }
        if (!settled) return null

        val uSq = cosSqAlpha * (Wgs84.A * Wgs84.A - Wgs84.B * Wgs84.B) / (Wgs84.B * Wgs84.B)
        val bigA = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val bigB = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))
        val deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4.0 * (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
            bigB / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)))
        return Wgs84.B * bigA * (sigma - deltaSigma)
    }
}
