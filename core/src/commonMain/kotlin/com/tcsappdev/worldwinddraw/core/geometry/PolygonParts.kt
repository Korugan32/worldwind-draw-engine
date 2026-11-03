package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.InternalWorldWindDrawApi

/**
 * Tells the separate parts of a shape of several rings (see [DrawnPolygon]) from each other: which rings are a part's outline and which
 * are holes in it. There is nothing in the rings that says so, it follows from where they lie under the even-odd rule: a ring inside an
 * odd number of other rings is a hole, inside an even number (none, or a hole) an outline, and a hole belongs to the innermost ring
 * around it.
 *
 * A ring is judged by one of its vertices, so rings are expected not to cross each other or share points, as the model asks for.
 */
@InternalWorldWindDrawApi
public object PolygonParts {
    /**
     * The parts of the shape the [rings] enclose, in the order of their outlines: each part is a list with its outline first and its holes
     * after it. An island in a lake in a country is a part of its own. Rings of fewer than 3 points are left out.
     */
    public fun split(rings: List<List<GeoPosition>>): List<List<List<GeoPosition>>> {
        val usable = rings.filter { it.size >= 3 }
        // A ring can only lie around a point inside its own bounding box, which is what is asked first: most pairs of rings (the islands
        // of an archipelago) are told apart by that alone, so the ring-by-ring test runs for few of them.
        val boxes = usable.map { Box.of(it) }
        // For every ring, the indices of the other rings that lie around it.
        val around = usable.mapIndexed { i, ring ->
            val point = ring.first()
            usable.indices.filter { j -> j != i && boxes[j].holds(point) && contains(usable[j], point) }
        }
        val parts = ArrayList<MutableList<List<GeoPosition>>>()
        val partOf = HashMap<Int, Int>() // index of an outline -> its place in parts
        for (i in usable.indices) {
            if (around[i].size % 2 == 0) {
                partOf[i] = parts.size
                parts += mutableListOf(usable[i])
            }
        }
        for (i in usable.indices) {
            if (around[i].size % 2 == 0) continue
            // The innermost ring around this hole is the one with the most rings around it, and it is an outline.
            val parent = around[i].maxByOrNull { around[it].size } ?: continue
            partOf[parent]?.let { parts[it] += usable[i] }
        }
        return parts
    }

    private class Box(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
        fun holds(point: GeoPosition) = point.latitude in minLat..maxLat && point.longitude in minLon..maxLon

        companion object {
            fun of(ring: List<GeoPosition>) = Box(ring.minOf { it.latitude }, ring.maxOf { it.latitude }, ring.minOf { it.longitude }, ring.maxOf { it.longitude })
        }
    }

    /** Whether [point] is inside [ring] (the ordinary crossing-number test on latitude and longitude). */
    private fun contains(ring: List<GeoPosition>, point: GeoPosition): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.latitude > point.latitude) != (b.latitude > point.latitude) &&
                point.longitude < (b.longitude - a.longitude) * (point.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
            ) inside = !inside
            j = i
        }
        return inside
    }
}
