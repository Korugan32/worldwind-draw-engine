package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Takes a shape down to a chosen NUMBER of points by repeatedly dropping the point that matters least to its outline: the one
 * whose triangle with its two neighbours has the smallest area (Visvalingam-Whyatt). Unlike a tolerance in metres
 * ([PathSimplifier]) the result has exactly the size asked for, which is what a "keep 30%" control needs, and it rounds corners off
 * instead of leaving the sharp spikes Douglas-Peucker makes at heavy simplification (the reason mapshaper uses it by default).
 *
 * It works on a flat map of the shape (longitude shrunk by the cosine of the middle latitude), which is accurate for a shape of
 * ordinary size; one across the antimeridian or around a pole is only approximated. It does NOT check that the result stays free
 * of self-intersections: a very heavy simplification of a shape with fine detail can fold it over itself, so callers should not
 * offer to go all the way down.
 *
 * Like [PathSimplifier] it returns indices of the points to keep, so every kept point is one of the originals.
 */
internal object Visvalingam {
    /**
     * Indices (increasing) of the [keepCount] points to keep of [points]. For an open path the first and last point are always
     * kept (so at least 2); for a closed ring (the last point not repeated) at least 3 are kept. A [keepCount] above the number of
     * points, or at or below the minimum, gives all of them / the minimum respectively.
     */
    fun simplify(points: List<GeoPosition>, isClosed: Boolean, keepCount: Int): List<Int> {
        val n = points.size
        val minimum = if (isClosed) 3 else 2
        val target = keepCount.coerceAtLeast(minimum)
        if (n <= target) return points.indices.toList()

        val lonScale = cos(points.sumOf { it.latitude } / n * PI / 180.0).coerceAtLeast(0.01)
        val xs = DoubleArray(n) { points[it].longitude * lonScale }
        val ys = DoubleArray(n) { points[it].latitude }

        val previous = IntArray(n) { (it - 1 + n) % n }
        val next = IntArray(n) { (it + 1) % n }
        val removed = BooleanArray(n)
        val version = IntArray(n)
        val heap = AreaHeap()

        // The two ends of an open path are never candidates.
        fun isCandidate(i: Int) = isClosed || (i != 0 && i != n - 1)
        fun area(i: Int): Double {
            val a = previous[i]
            val b = next[i]
            return abs((xs[a] - xs[i]) * (ys[b] - ys[i]) - (xs[b] - xs[i]) * (ys[a] - ys[i])) / 2
        }
        for (i in 0 until n) if (isCandidate(i)) heap.add(area(i), i, 0)

        var alive = n
        var lastRemovedArea = 0.0
        while (alive > target) {
            val entry = heap.poll() ?: break
            val i = entry.index
            if (removed[i] || entry.version != version[i]) continue // a stale entry: that point's area changed since
            // A point never counts for less than one dropped before it, so a long smooth stretch is thinned evenly
            // rather than from one end.
            lastRemovedArea = maxOf(lastRemovedArea, entry.area)
            removed[i] = true
            alive--
            val a = previous[i]
            val b = next[i]
            next[a] = b
            previous[b] = a
            for (neighbour in intArrayOf(a, b)) {
                if (!isCandidate(neighbour) || removed[neighbour]) continue
                version[neighbour]++
                heap.add(maxOf(area(neighbour), lastRemovedArea), neighbour, version[neighbour])
            }
        }
        return (0 until n).filter { !removed[it] }
    }

    private class Entry(val area: Double, val index: Int, val version: Int)

    /** A min-heap by area; ties go to the lower index so the result does not depend on the order things were added in. */
    private class AreaHeap {
        private val items = ArrayList<Entry>()

        private fun less(a: Entry, b: Entry) = a.area < b.area || (a.area == b.area && a.index < b.index)

        fun add(area: Double, index: Int, version: Int) {
            items += Entry(area, index, version)
            var i = items.size - 1
            while (i > 0) {
                val parent = (i - 1) / 2
                if (!less(items[i], items[parent])) break
                items[i] = items[parent].also { items[parent] = items[i] }
                i = parent
            }
        }

        fun poll(): Entry? {
            if (items.isEmpty()) return null
            val top = items[0]
            val last = items.removeAt(items.size - 1)
            if (items.isNotEmpty()) {
                items[0] = last
                var i = 0
                while (true) {
                    val left = 2 * i + 1
                    val right = left + 1
                    var smallest = i
                    if (left < items.size && less(items[left], items[smallest])) smallest = left
                    if (right < items.size && less(items[right], items[smallest])) smallest = right
                    if (smallest == i) break
                    items[i] = items[smallest].also { items[smallest] = items[i] }
                    i = smallest
                }
            }
            return top
        }
    }
}
