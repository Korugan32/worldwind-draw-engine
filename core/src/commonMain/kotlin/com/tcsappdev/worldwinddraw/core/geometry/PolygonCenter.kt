package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The visual centre of a polygon: the point inside it that is farthest from its edges, which is where a label or a picture
 * looks centred ("pole of inaccessibility"). Unlike the area centroid or the middle of the bounding box it is always inside
 * the shape, and it sits in the fattest part of it: for a horseshoe or a country with a long arm the centroid can lie outside
 * or in a thin stretch, while this lands in the body. It also gives the radius of the biggest circle that fits inside the
 * shape around that point.
 *
 * It is the polylabel algorithm (Mapbox): the bounding box is cut into cells, and only those that could still hold a point
 * better than the best found so far are cut again.
 *
 * The work is done on a flat map of the shape, with longitude shrunk by the cosine of the shape's middle latitude, so a
 * degree of longitude and a degree of latitude are about the same length there. That is close for any shape of ordinary
 * size; for one that crosses the antimeridian (its longitudes would have to be unwrapped first) or reaches a pole it is
 * only an approximation.
 */
object PolygonCenter {
    /** A [position] and the [radiusMeters] of the biggest circle around it that stays inside the polygon. */
    data class VisualCenter(val position: GeoPosition, val radiusMeters: Double)

    /** Meters per degree of latitude (and, after the shrinking, of longitude). */
    internal const val METERS_PER_DEGREE = 111_194.9

    /** A hard limit on the number of cells looked at, so a pathological shape can't take forever. */
    private const val MAX_CELLS = 200_000

    /**
     * The visual centre of the polygon with outline [ring] (the last point is not repeated). It is found to within
     * [relativePrecision] of the polygon's size (the default, 0.1%, is far finer than a label or picture can show).
     * A ring of fewer than 3 points has no inside: the first point, radius 0.
     */
    fun visualCenter(ring: List<GeoPosition>, relativePrecision: Double = 0.001): VisualCenter {
        if (ring.isEmpty()) throw IllegalArgumentException("A ring needs at least one point")
        if (ring.size < 3) return VisualCenter(ring.first(), 0.0)

        val minLat = ring.minOf { it.latitude }
        val maxLat = ring.maxOf { it.latitude }
        val minLon = ring.minOf { it.longitude }
        val maxLon = ring.maxOf { it.longitude }
        val lonScale = cos((minLat + maxLat) / 2 * PI / 180.0).coerceAtLeast(0.01)

        // The flat map: x is longitude shrunk, y is latitude.
        val xs = DoubleArray(ring.size) { ring[it].longitude * lonScale }
        val ys = DoubleArray(ring.size) { ring[it].latitude }
        val left = minLon * lonScale
        val right = maxLon * lonScale
        val width = right - left
        val height = maxLat - minLat
        val size = min(width, height)
        if (size <= 0.0) return VisualCenter(ring.first(), 0.0)
        val precision = max(width, height) * relativePrecision

        val queue = CellQueue()
        var half = size / 2
        var x = left
        while (x < right) {
            var y = minLat
            while (y < maxLat) {
                queue.add(Cell(x + half, y + half, half, xs, ys))
                y += size
            }
            x += size
        }

        // Start from the area centroid and the middle of the box, both common good guesses.
        var best = centroidCell(xs, ys) ?: queue.peek()!!
        val boxCell = Cell(left + width / 2, minLat + height / 2, 0.0, xs, ys)
        if (boxCell.distance > best.distance) best = boxCell

        var cells = 0
        while (queue.isNotEmpty() && cells++ < MAX_CELLS) {
            val cell = queue.poll()!!
            if (cell.distance > best.distance) best = cell
            // Nothing in this cell can beat the best by more than the precision: leave it.
            if (cell.maxDistance - best.distance <= precision) continue
            half = cell.half / 2
            queue.add(Cell(cell.x - half, cell.y - half, half, xs, ys))
            queue.add(Cell(cell.x + half, cell.y - half, half, xs, ys))
            queue.add(Cell(cell.x - half, cell.y + half, half, xs, ys))
            queue.add(Cell(cell.x + half, cell.y + half, half, xs, ys))
        }

        return VisualCenter(
            position = GeoPosition(best.y.coerceIn(-90.0, 90.0), (best.x / lonScale).coerceIn(-180.0, 180.0)),
            radiusMeters = max(0.0, best.distance) * METERS_PER_DEGREE,
        )
    }

    /** The cell at the polygon's area centroid, or `null` if the polygon has no area. */
    private fun centroidCell(xs: DoubleArray, ys: DoubleArray): Cell? {
        var area = 0.0
        var cx = 0.0
        var cy = 0.0
        var j = xs.size - 1
        for (i in xs.indices) {
            val cross = xs[i] * ys[j] - xs[j] * ys[i]
            cx += (xs[i] + xs[j]) * cross
            cy += (ys[i] + ys[j]) * cross
            area += cross * 3
            j = i
        }
        if (area == 0.0) return null
        return Cell(cx / area, cy / area, 0.0, xs, ys)
    }

    /** A square cell of the search: its centre, half its side, and how far inside the polygon its centre is (negative: outside). */
    private class Cell(val x: Double, val y: Double, val half: Double, xs: DoubleArray, ys: DoubleArray) {
        val distance = signedDistance(x, y, xs, ys)

        /** The most any point of this cell could be inside by: its centre's distance plus the cell's half diagonal. */
        val maxDistance = distance + half * SQRT2
    }

    /** A max-heap of cells by their [Cell.maxDistance] (the most promising first). */
    private class CellQueue {
        private val items = ArrayList<Cell>()

        fun isNotEmpty() = items.isNotEmpty()
        fun peek(): Cell? = items.firstOrNull()

        fun add(cell: Cell) {
            items += cell
            var i = items.size - 1
            while (i > 0) {
                val parent = (i - 1) / 2
                if (items[parent].maxDistance >= items[i].maxDistance) break
                swap(i, parent)
                i = parent
            }
        }

        fun poll(): Cell? {
            if (items.isEmpty()) return null
            val top = items[0]
            val last = items.removeAt(items.size - 1)
            if (items.isNotEmpty()) {
                items[0] = last
                var i = 0
                while (true) {
                    val left = 2 * i + 1
                    val right = left + 1
                    var largest = i
                    if (left < items.size && items[left].maxDistance > items[largest].maxDistance) largest = left
                    if (right < items.size && items[right].maxDistance > items[largest].maxDistance) largest = right
                    if (largest == i) break
                    swap(i, largest)
                    i = largest
                }
            }
            return top
        }

        private fun swap(a: Int, b: Int) {
            val t = items[a]; items[a] = items[b]; items[b] = t
        }
    }

    private val SQRT2 = sqrt(2.0)

    /** Distance from (px, py) to the nearest edge of the polygon: positive inside it, negative outside. */
    private fun signedDistance(px: Double, py: Double, xs: DoubleArray, ys: DoubleArray): Double {
        var inside = false
        var minDistanceSquared = Double.MAX_VALUE
        var j = xs.size - 1
        for (i in xs.indices) {
            val ax = xs[i]; val ay = ys[i]
            val bx = xs[j]; val by = ys[j]
            if ((ay > py) != (by > py) && px < (bx - ax) * (py - ay) / (by - ay) + ax) inside = !inside
            minDistanceSquared = min(minDistanceSquared, segmentDistanceSquared(px, py, ax, ay, bx, by))
            j = i
        }
        val distance = sqrt(minDistanceSquared)
        return if (inside) distance else -distance
    }

    private fun segmentDistanceSquared(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        var x = ax
        var y = ay
        var dx = bx - x
        var dy = by - y
        if (dx != 0.0 || dy != 0.0) {
            val t = ((px - x) * dx + (py - y) * dy) / (dx * dx + dy * dy)
            if (t > 1) {
                x = bx; y = by
            } else if (t > 0) {
                x += dx * t; y += dy * t
            }
        }
        dx = px - x
        dy = py - y
        return dx * dx + dy * dy
    }
}
