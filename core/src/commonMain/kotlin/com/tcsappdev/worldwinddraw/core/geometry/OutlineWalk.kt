package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.InternalWorldWindDrawApi

import kotlin.math.PI

/**
 * Walking once round the outline of a shape, from a starting corner, the way a pen draws it: [pathTo] is what the pen has drawn when it is
 * a given part of the way round, and [pointAt] is where the pen is.
 *
 * The distance is the length along the edges, as WWK draws them (great-circle arcs, see [GreatCircle]), so the pen moves at the same speed
 * on a long edge as on a short one, and a cut in the middle of an edge lies on the drawn edge. An edge's length is the angle at the centre of
 * the globe, only meaningful against the other edges of the same walk, which is what [fraction][pathTo] needs.
 *
 * The walk is closed (the outline of a shape) and ends back at the start, unless [closed] is false: then it is a route, from the first point
 * to the last, which the pen does not come back from. Fewer than 2 points have nothing to walk.
 *
 * @param startIndex the corner the pen starts at (an index into [ring]); a route always starts at its first point, so it must be 0.
 * @param reversed whether it goes the other way round, against the order of [ring].
 */
@InternalWorldWindDrawApi
public class OutlineWalk(ring: List<GeoPosition>, startIndex: Int = 0, reversed: Boolean = false, closed: Boolean = true) {
    private val corners: List<GeoPosition>
    private val along: DoubleArray

    /** The whole way round, as the sum of the angles of the edges (radians). */
    public val length: Double

    init {
        require(ring.size >= 2) { "an outline needs at least 2 points to be walked" }
        require(startIndex in ring.indices) { "startIndex $startIndex is not in a ring of ${ring.size} points" }
        val ordered = if (reversed) ring.reversed() else ring
        if (closed) {
            // Reversing moves the corner that was at startIndex to this place.
            val start = if (reversed) ring.size - 1 - startIndex else startIndex
            corners = ordered.drop(start) + ordered.take(start) + ordered[start]
        } else {
            require(startIndex == 0) { "a route starts at its first point" }
            corners = ordered
        }
        along = DoubleArray(corners.size)
        for (i in 1 until corners.size) along[i] = along[i - 1] + GreatCircle.angleBetween(corners[i - 1], corners[i])
        length = along.last()
    }

    /** How far round the walk the corner at [index] (an index into the points it was made from, for a route) is: 0 for the first, 1 for the last. */
    public fun fractionAtCorner(index: Int): Double {
        require(index in 0 until corners.size) { "corner $index is not in a walk of ${corners.size} points" }
        return if (length <= 0.0) 0.0 else along[index] / length
    }

    /** Where the pen is when it is [fraction] (0 at the start, 1 back at the start; outside that it stops at the ends) of the way round. */
    public fun pointAt(fraction: Double): GeoPosition {
        val at = locate(fraction)
        return cut(at)
    }

    /**
     * The direction the pen is going at [fraction] of the way, in radians clockwise from north (see [GreatCircle.bearing]): along the arc of
     * the edge it is on, so on a long edge the direction changes as it goes. At the very end it is the direction it arrived in.
     */
    public fun bearingAt(fraction: Double): Double {
        val at = locate(fraction)
        val end = corners[at.edge + 1]
        if (at.fraction >= 1.0) {
            // Arrived: the way it came in, which is the way back from the end turned round.
            val back = GreatCircle.bearing(end, corners[at.edge])
            return (back + PI) % (2 * PI)
        }
        return GreatCircle.bearing(cut(at), end)
    }

    /**
     * What the pen has drawn at [fraction] of the way round: the corners it has passed, then the point it is at. At 0 that is only the start
     * (too little for a line: the caller shows nothing yet), at 1 the whole outline, ending on the start again. Between two corners the
     * last point is on the arc WWK draws between them, so the line it makes with the corner before is the drawn edge cut short.
     */
    public fun pathTo(fraction: Double): List<GeoPosition> {
        val at = locate(fraction)
        val path = ArrayList<GeoPosition>(at.edge + 2)
        for (i in 0..at.edge) path += corners[i]
        if (at.fraction > 0.0) path += cut(at)
        return path
    }

    /**
     * The stretch the pen drew between [from] and [to] (both fractions of the way round, like [pathTo]): from where it was at [from], by the
     * corners in between, to where it was at [to]. It begins and ends exactly where [pathTo] had the pen, so stretches that follow each
     * other join up. When [to] is not after [from] there is no stretch, only the one point.
     */
    public fun pathBetween(from: Double, to: Double): List<GeoPosition> {
        if (to <= from) return listOf(pointAt(from))
        val a = locate(from)
        val b = locate(to)
        val path = ArrayList<GeoPosition>()
        path += cut(a)
        for (i in a.edge + 1..b.edge) path += corners[i]
        if (b.fraction > 0.0) path += cut(b)
        return path
    }

    /** The point on the edge where the pen is; at either end of the edge, exactly the corner (no arc arithmetic to leave a rounding error). */
    private fun cut(at: Place): GeoPosition = when {
        at.fraction <= 0.0 -> corners[at.edge]
        at.fraction >= 1.0 -> corners[at.edge + 1]
        else -> GreatCircle.pointAt(corners[at.edge], corners[at.edge + 1], at.fraction)
    }

    /** The edge the pen is on and how far along that edge it is (0 until 1; at the very end it is the last edge, 1 along it). */
    private class Place(val edge: Int, val fraction: Double)

    private fun locate(fraction: Double): Place {
        require(!fraction.isNaN()) { "fraction is not a number" }
        val lastEdge = corners.size - 2
        if (length <= 0.0 || fraction <= 0.0) return Place(0, 0.0)
        if (fraction >= 1.0) return Place(lastEdge, 1.0)
        val target = fraction * length
        // The last corner that is not further along than the target: the pen is on the edge that starts there.
        var edge = 0
        var low = 0
        var high = lastEdge
        while (low <= high) {
            val middle = (low + high) / 2
            if (along[middle] <= target) { edge = middle; low = middle + 1 } else high = middle - 1
        }
        val span = along[edge + 1] - along[edge]
        return Place(edge, if (span <= 0.0) 0.0 else (target - along[edge]) / span)
    }
}
