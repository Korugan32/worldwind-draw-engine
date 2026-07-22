package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.sqrt

/**
 * The result of [GeometryOps.nearestEdge]: which edge of a ring is closest to a query
 * point, the closest point on that edge, and the (planar) distance to it.
 */
data class EdgeHit(
    val edgeIndex: Int,
    val closestPoint: GeoPosition,
    val distance: Double,
)

/**
 * Pure geometry math over [GeoPosition] rings. Uses a planar (lat/lon-as-Cartesian)
 * approximation rather than geodesic math — accurate enough for hit-testing and editing
 * at the scale of a single on-screen shape, and keeps this module free of any
 * projection/CRS dependency.
 */
object GeometryOps {
    /** Returns a copy of [ring] with [position] inserted right after [afterEdgeIndex]. */
    fun insertVertex(
        ring: List<GeoPosition>,
        afterEdgeIndex: Int,
        position: GeoPosition
    ): List<GeoPosition> {
        val ringCopy = ring.toMutableList()
        ringCopy.add(afterEdgeIndex + 1, position)
        return ringCopy
    }

    /**
     * Whether [point] lies inside the closed polygon described by [ring], using the
     * ray-casting (even-odd) rule: a ray is cast outward from [point], and the number of
     * ring edges it crosses is counted — an odd count means the point is inside.
     */
    fun isPointInPolygon(point: GeoPosition, ring: List<GeoPosition>): Boolean {
        val lat = point.latitude
        val lon = point.longitude
        var inside = false
        var j = ring.lastIndex
        var i = 0
        while (i < ring.size) {
            val currentLat = ring[i].latitude
            val currentLon = ring[i].longitude
            val previousLat = ring[j].latitude
            val previousLon = ring[j].longitude
            val edgeSpansPoint = currentLon > lon != previousLon > lon
            val crossingIsBeyondPoint =
                lat < (previousLat - currentLat) * (lon - currentLon) / (previousLon - currentLon) + currentLat
            val intersect = edgeSpansPoint && crossingIsBeyondPoint
            if (intersect) {
                inside = !inside
            }
            j = i++
        }
        return inside
    }

    /**
     * Finds the edge of [ring] closest to [point]: which edge it is, the closest point on
     * it, and the distance to it. Set [closed] to `false` for an open polyline — this
     * skips the edge that would otherwise close the ring from its last point back to its
     * first.
     */
    fun nearestEdge(point: GeoPosition, ring: List<GeoPosition>, closed: Boolean = true): EdgeHit {
        val segmentCount = if (closed) ring.size else ring.size - 1
        var best: EdgeHit? = null
        for (i in 0 until segmentCount) {
            val start = ring[i]
            val end = ring[(i + 1) % ring.size]
            val closestPoint = projectOntoSegment(point, start, end)
            val distance = distanceBetween(point, closestPoint)
            if (best == null || distance < best.distance) {
                best = EdgeHit(edgeIndex = i, closestPoint = closestPoint, distance = distance)
            }
        }
        return best ?: error("unreachable: a ring of size >= 2 always has at least one segment")
    }

    /** The closest point to [point] on the segment from [start] to [end] (clamped to the segment, not the infinite line through it). */
    private fun projectOntoSegment(point: GeoPosition, start: GeoPosition, end: GeoPosition): GeoPosition {
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return start
        val t = ((point.longitude - start.longitude) * dx + (point.latitude - start.latitude) * dy) / lengthSquared
        val clampedT = t.coerceIn(0.0, 1.0)
        return GeoPosition(
            latitude = start.latitude + clampedT * dy,
            longitude = start.longitude + clampedT * dx,
        )
    }

    /** Straight-line (planar) distance between two positions. */
    private fun distanceBetween(a: GeoPosition, b: GeoPosition): Double {
        val dLat = a.latitude - b.latitude
        val dLon = a.longitude - b.longitude
        return sqrt(dLat * dLat + dLon * dLon)
    }
}