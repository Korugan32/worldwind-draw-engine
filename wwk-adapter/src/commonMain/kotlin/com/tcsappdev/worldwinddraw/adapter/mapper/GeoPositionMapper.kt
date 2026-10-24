package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.GreatCircle
import earth.worldwind.geom.Position
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** Converts to the WWK position type every renderable/camera API in `wwk-adapter` expects. */
fun GeoPosition.toWwkPosition(): Position = Position.fromDegrees(latitude, longitude, altitude)

/** Converts a WWK [Position] back to our platform-independent [GeoPosition]. */
fun Position.toGeoPosition(): GeoPosition = GeoPosition(latitude.inDegrees, longitude.inDegrees, altitude)

/**
 * The longest stretch of an edge that is drawn as one straight piece, in degrees of arc: what WWK itself uses (its default is 64 pieces
 * for an edge halfway round the globe, 180 / 64 degrees each).
 */
private const val MAX_EDGE_STEP_DEGREES = 180.0 / 64.0

/**
 * The positions to give a WWK polygon or path for an outline through [positions]: the points themselves plus, on every edge longer than
 * [MAX_EDGE_STEP_DEGREES], points along the great circle between them, so a long edge still bends the way a great-circle edge does.
 * With these in place the shape's own subdivision is switched off (`maximumNumEdgeIntervals = 0`, see [disableEdgeSubdivision]).
 *
 * Leaving WWK to subdivide is what cost the memory: it sets aside room for its maximum of 63 extra points on EVERY edge, used or not,
 * about 6 KB per vertex. A shape of 30,000 vertices then asked for a single 25 MB array and ran the heap out, as did a map with a
 * few hundred countries. Adding only the points a long edge really needs costs next to nothing.
 *
 * [isClosed] adds the edge from the last point back to the first (a polygon); a path has one edge fewer than it has points.
 */
internal fun densifiedWwkPositions(positions: List<GeoPosition>, isClosed: Boolean): List<Position> {
    val result = ArrayList<Position>(positions.size)
    val edgeCount = if (isClosed) positions.size else positions.size - 1
    for (i in positions.indices) {
        val from = positions[i]
        result += from.toWwkPosition()
        if (i < edgeCount) {
            val to = positions[(i + 1) % positions.size]
            // Most edges are short: telling so costs a few trig calls, building the intermediate points would cost allocations.
            if (arcDegrees(from, to) > MAX_EDGE_STEP_DEGREES) {
                GreatCircle.intermediatePoints(from, to, MAX_EDGE_STEP_DEGREES).forEach { result += it.toWwkPosition() }
            }
        }
    }
    return result
}

/** The great-circle angle between two positions, in degrees. */
private fun arcDegrees(a: GeoPosition, b: GeoPosition): Double {
    val lat1 = a.latitude * PI / 180.0
    val lat2 = b.latitude * PI / 180.0
    val dLon = (b.longitude - a.longitude) * PI / 180.0
    val cosine = sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(dLon)
    return acos(cosine.coerceIn(-1.0, 1.0)) * 180.0 / PI
}

/** Turns off WWK's own edge subdivision for [shape], whose edges [densifiedWwkPositions] has already divided. */
internal fun disableEdgeSubdivision(shape: earth.worldwind.shape.AbstractShape) {
    shape.maximumNumEdgeIntervals = 0
}
