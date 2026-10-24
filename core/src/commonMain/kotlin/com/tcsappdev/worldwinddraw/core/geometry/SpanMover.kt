package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moves one vertex of a shape together with the vertices hidden between it and its neighbouring "anchors", so a
 * stretch of a border can be reshaped by dragging one of a few handles instead of every one of its points.
 *
 * Dragging an anchor moves it fully; the vertices between the previous anchor and it follow in proportion to how
 * far along that stretch they are (0 at the previous anchor, 1 at the dragged vertex), and likewise those between it
 * and the next anchor (1 at the dragged vertex, 0 at the next anchor). The anchors themselves, and every vertex beyond
 * them, stay where they are. The stretch bends smoothly instead of leaving the hidden points behind as a zig-zag.
 *
 * The displacement is a difference in latitude and longitude degrees, which is exact enough for the size of an
 * editing gesture; "how far along" is measured on the sphere, by great-circle distance along the stretch.
 */
object SpanMover {
    /**
     * A copy of [vertices] with [movedId] at [newPosition] and the vertices between [prevId] and it, and between it and
     * [nextId], moved in proportion. Either neighbour may be `null` (an end of an open path has no neighbour on one
     * side): that side then has no stretch. [isClosed] says the vertices form a ring, so a stretch may run past the
     * end of the list back to its start. Returns [vertices] unchanged if [movedId] isn't among them.
     */
    fun move(vertices: List<Vertex>, movedId: VertexId, prevId: VertexId?, nextId: VertexId?, newPosition: GeoPosition, isClosed: Boolean): List<Vertex> {
        val n = vertices.size
        val moved = vertices.indexOfFirst { it.id == movedId }
        if (moved < 0) return vertices

        val from = vertices[moved].position
        val dLat = newPosition.latitude - from.latitude
        val dLon = wrapDegrees(newPosition.longitude - from.longitude)

        val result = vertices.toMutableList()
        result[moved] = vertices[moved].copy(position = newPosition)

        // The stretch from the previous anchor up to the moved vertex: 0 at the anchor, 1 at the moved vertex.
        prevId?.let { id ->
            val anchor = vertices.indexOfFirst { it.id == id }
            stretch(vertices, anchor, moved, isClosed)?.let { indices ->
                shift(vertices, result, indices, dLat, dLon) { distanceFromStart, total -> distanceFromStart / total }
            }
        }
        // The stretch from the moved vertex up to the next anchor: 1 at the moved vertex, 0 at the anchor.
        nextId?.let { id ->
            val anchor = vertices.indexOfFirst { it.id == id }
            stretch(vertices, moved, anchor, isClosed)?.let { indices ->
                shift(vertices, result, indices, dLat, dLon) { distanceFromStart, total -> 1.0 - distanceFromStart / total }
            }
        }
        return result
    }

    /**
     * The indices from [start] to [end] inclusive, going forward through the list (and, for a ring, past its end back to
     * its start), or `null` if there is no such stretch: an anchor that isn't there, or, in an open path, one that is on
     * the wrong side.
     */
    private fun stretch(vertices: List<Vertex>, start: Int, end: Int, isClosed: Boolean): List<Int>? {
        val n = vertices.size
        if (start < 0 || end < 0 || start == end) return null
        if (!isClosed && start > end) return null
        val indices = ArrayList<Int>()
        var i = start
        while (true) {
            indices += i
            if (i == end) break
            i = (i + 1) % n
        }
        return indices
    }

    /**
     * Shifts the vertices strictly inside [indices] (the first and last are the two ends of the stretch and stay) by
     * [dLat]/[dLon] times [weight], which gets how far the vertex is along the stretch and the stretch's whole length.
     */
    private inline fun shift(
        original: List<Vertex>,
        result: MutableList<Vertex>,
        indices: List<Int>,
        dLat: Double,
        dLon: Double,
        weight: (distanceFromStart: Double, total: Double) -> Double,
    ) {
        if (indices.size < 3) return // no vertex between the two ends
        val cumulative = DoubleArray(indices.size)
        for (k in 1 until indices.size) {
            cumulative[k] = cumulative[k - 1] + angleBetween(original[indices[k - 1]].position, original[indices[k]].position)
        }
        val total = cumulative.last()
        for (k in 1 until indices.size - 1) {
            // A stretch whose points all coincide has no length to go by: fall back to even steps by index.
            val w = if (total > 0.0) weight(cumulative[k], total) else weight(k.toDouble(), (indices.size - 1).toDouble())
            val index = indices[k]
            val p = original[index].position
            result[index] = original[index].copy(
                position = GeoPosition(
                    latitude = (p.latitude + dLat * w).coerceIn(-90.0, 90.0),
                    longitude = wrapDegrees(p.longitude + dLon * w),
                    altitude = p.altitude,
                )
            )
        }
    }

    /** Wraps [degrees] into -180..180. */
    private fun wrapDegrees(degrees: Double): Double {
        var d = (degrees + 180.0) % 360.0
        if (d < 0) d += 360.0
        return d - 180.0
    }

    /** The great-circle angle, in radians, between two positions (haversine). */
    private fun angleBetween(a: GeoPosition, b: GeoPosition): Double {
        val lat1 = a.latitude * PI / 180.0
        val lat2 = b.latitude * PI / 180.0
        val dLat = lat2 - lat1
        val dLon = (b.longitude - a.longitude) * PI / 180.0
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
