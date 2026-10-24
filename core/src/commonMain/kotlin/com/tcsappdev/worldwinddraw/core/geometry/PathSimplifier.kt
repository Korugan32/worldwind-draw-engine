package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drops the points of a path or a ring that don't change its shape by more than a tolerance
 * (Douglas-Peucker), so a shape with thousands of points can be drawn with far fewer when it is
 * seen from far away. It works on the sphere: the distance of a point to the line a shape would
 * be drawn with is its distance to the great-circle arc between the neighbours that are kept,
 * because that is how WWK draws an edge, and plain latitude/longitude distances are wrong at
 * high latitudes and over long edges.
 *
 * The result is a list of *indices* into the given points (in increasing order), not new points:
 * every point that is kept is one of the originals, so a caller can map it back to the vertex it
 * came from.
 */
object PathSimplifier {
    /** Mean Earth radius, meters: the sphere the tolerance is measured on. */
    const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * Indices of the points to keep of an open path, always including the first and the last. Every
     * dropped point is within [toleranceMeters] of the arc between its kept neighbours. A tolerance
     * of 0 (or less) keeps everything.
     */
    fun simplifyPath(points: List<GeoPosition>, toleranceMeters: Double): List<Int> {
        val n = points.size
        if (n <= 2 || toleranceMeters <= 0.0) return points.indices.toList()
        val vectors = UnitVectors(points)
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        refine(vectors, keep, 0, n - 1, n, toleranceMeters / EARTH_RADIUS_METERS)
        return keep.indices.filter { keep[it] }
    }

    /**
     * Indices of the points to keep of a closed ring (the last point is *not* repeated at the end:
     * the edge from the last point back to the first is implied), at least 3 of them whenever the ring
     * has that many. Like [simplifyPath], dropped points stay within [toleranceMeters] of the
     * outline that is kept. A tolerance of 0 (or less) keeps everything.
     */
    fun simplifyRing(points: List<GeoPosition>, toleranceMeters: Double): List<Int> {
        val n = points.size
        if (n <= 3 || toleranceMeters <= 0.0) return points.indices.toList()
        val vectors = UnitVectors(points)
        val keep = BooleanArray(n)

        // A ring has no natural ends to hold on to, so it is cut in two at point 0 and at the point
        // farthest from it, and each half is simplified as a path between them.
        var far = 1
        var farAngle = -1.0
        for (i in 1 until n) {
            val angle = vectors.angleBetween(0, i)
            if (angle > farAngle) { farAngle = angle; far = i }
        }
        keep[0] = true
        keep[far] = true
        val tolerance = toleranceMeters / EARTH_RADIUS_METERS
        refine(vectors, keep, 0, far, n, tolerance)
        refine(vectors, keep, far, n, n, tolerance) // the second half ends back at point 0 (index n)

        // A ring needs 3 points to enclose anything: if only the two anchors survived, add the point
        // farthest from the arc joining them.
        if (keep.count { it } < 3) {
            var best = -1
            var bestAngle = -1.0
            for (i in 1 until n) {
                if (keep[i]) continue
                val angle = vectors.angleToArc(i, 0, far)
                if (angle > bestAngle) { bestAngle = angle; best = i }
            }
            keep[best] = true
        }
        return keep.indices.filter { keep[it] }
    }

    /**
     * Douglas-Peucker between the kept points [from] and [to] (indices; an index of [n] stands for point 0, the
     * end of a ring): finds the point between them farthest from the arc joining them, keeps it if it is farther than
     * [tolerance] (radians) and then does the same on both sides of it. An explicit stack, not recursion: a path of a
     * few thousand points can be as deep as it is long.
     */
    private fun refine(vectors: UnitVectors, keep: BooleanArray, from: Int, to: Int, n: Int, tolerance: Double) {
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(from, to))
        while (stack.isNotEmpty()) {
            val (lo, hi) = stack.removeLast()
            if (hi - lo < 2) continue
            var farthest = -1
            var farthestAngle = -1.0
            for (i in lo + 1 until hi) {
                val angle = vectors.angleToArc(i % n, lo % n, hi % n)
                if (angle > farthestAngle) { farthestAngle = angle; farthest = i }
            }
            if (farthestAngle > tolerance) {
                keep[farthest % n] = true
                stack.addLast(intArrayOf(lo, farthest))
                stack.addLast(intArrayOf(farthest, hi))
            }
        }
    }

    /** The points as unit vectors on the sphere (x, y, z), so angles are cheap and exact. */
    private class UnitVectors(points: List<GeoPosition>) {
        private val x = DoubleArray(points.size)
        private val y = DoubleArray(points.size)
        private val z = DoubleArray(points.size)

        init {
            points.forEachIndexed { i, p ->
                val lat = p.latitude * PI / 180.0
                val lon = p.longitude * PI / 180.0
                x[i] = cos(lat) * cos(lon)
                y[i] = cos(lat) * sin(lon)
                z[i] = sin(lat)
            }
        }

        /** The angle, in radians, between points [i] and [j] as seen from the centre of the Earth. */
        fun angleBetween(i: Int, j: Int): Double {
            val cx = y[i] * z[j] - z[i] * y[j]
            val cy = z[i] * x[j] - x[i] * z[j]
            val cz = x[i] * y[j] - y[i] * x[j]
            return atan2(sqrt(cx * cx + cy * cy + cz * cz), x[i] * x[j] + y[i] * y[j] + z[i] * z[j])
        }

        /**
         * The angle, in radians, from point [p] to the great-circle arc from point [a] to point [b] (the shortest
         * of: straight to the circle if that lands within the arc, otherwise to the nearer end).
         */
        fun angleToArc(p: Int, a: Int, b: Int): Double {
            // The normal of the plane of the great circle through a and b.
            var nx = y[a] * z[b] - z[a] * y[b]
            var ny = z[a] * x[b] - x[a] * z[b]
            var nz = x[a] * y[b] - y[a] * x[b]
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            val nearestEnd = min(angleBetween(p, a), angleBetween(p, b))
            if (length < 1e-12) return nearestEnd // a and b coincide (or are opposite): there is no arc, only the ends
            nx /= length; ny /= length; nz /= length

            val side = x[p] * nx + y[p] * ny + z[p] * nz // sine of the angle to the circle
            // p moved onto the plane of the circle: is it between a and b?
            val qx = x[p] - side * nx
            val qy = y[p] - side * ny
            val qz = z[p] - side * nz
            val afterA = (y[a] * qz - z[a] * qy) * nx + (z[a] * qx - x[a] * qz) * ny + (x[a] * qy - y[a] * qx) * nz
            val beforeB = (qy * z[b] - qz * y[b]) * nx + (qz * x[b] - qx * z[b]) * ny + (qx * y[b] - qy * x[b]) * nz
            return if (afterA >= 0.0 && beforeB >= 0.0) asin(abs(side).coerceAtMost(1.0)) else nearestEnd
        }
    }
}
