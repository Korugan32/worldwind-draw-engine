package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import kotlin.math.abs
import kotlin.math.hypot

/**
 * How an outline becomes another outline that has some corners in common with it but not all (a merge keeps the corners of the shape that
 * takes the other in and adds the other's; a cut takes some away; adding or deleting a point is the smallest case). The corners both have
 * (the same [com.tcsappdev.worldwinddraw.core.geometry.VertexId]) stay where they are, or move straight to their new place. Between two of
 * them, the stretch of the old outline and the stretch of the new one are laid over each other by how far along they are, so the shape
 * starts out as exactly the old outline and ends as exactly the new one, and the part that is new grows out of the old edge (or the part
 * that goes shrinks back into the new one) instead of one shape fading out under another.
 *
 * Returns `null` when that cannot be done: fewer than three corners in common, or the common ones are not in the same order round
 * both (then the two are not the same shape changed, and the caller crossfades).
 */
internal fun alignOutlines(a: List<Vertex>, b: List<Vertex>): List<Slot>? {
    if (a.size != a.map { it.id }.toSet().size || b.size != b.map { it.id }.toSet().size) return null
    val bIds = b.map { it.id }.toSet()
    val common = a.filter { it.id in bIds }.map { it.id }
    if (common.size < 3) return null

    // The other outline may go round the other way (the result of an overlay is not always oriented like its inputs).
    val commonSet = common.toSet()
    val ordered = listOf(b, b.reversed()).firstOrNull { ring -> rotatedTo(ring, common.first()).filter { it.id in commonSet }.map { it.id } == common }
        ?: return null

    // Both outlines go round starting at the same common corner.
    val ringA = rotatedTo(a, common.first())
    val ringB = rotatedTo(ordered, common.first())
    val slots = ArrayList<Slot>(ringA.size + ringB.size)
    var ia = 0
    var ib = 0
    while (ia < ringA.size) {
        val startA = ringA[ia]
        val startB = ringB[ib]
        slots += Slot(startB, startA.position, startB.position, startA.metadata)
        ia++
        ib++
        val chainA = ArrayList<Vertex>()
        while (ia < ringA.size && ringA[ia].id !in commonSet) chainA += ringA[ia++]
        val chainB = ArrayList<Vertex>()
        while (ib < ringB.size && ringB[ib].id !in commonSet) chainB += ringB[ib++]
        val endA = ringA[ia % ringA.size]
        val endB = ringB[ib % ringB.size]
        if (chainA.isEmpty() && chainB.isEmpty()) continue
        slots += between(startA.position, chainA, endA.position, startB.position, chainB, endB.position)
    }
    return slots
}

/**
 * A corner of the outline while it changes: [vertex] is the corner it is (its id, and its tags when it is in the second outline), and it
 * goes from [from] to [to]. A corner that is in both outlines has its tags change at the half way point ([fromMetadata] to [vertex]'s).
 */
internal class Slot(val vertex: Vertex, val from: GeoPosition, val to: GeoPosition, val fromMetadata: Map<String, String> = vertex.metadata) {
    fun at(t: Float) = Vertex(vertex.id, lerpPosition(from, to, t), step(fromMetadata, vertex.metadata, t))
}

/**
 * The corners between two common corners. The old stretch (A) and the new stretch (B) are each a line from the first common corner to the
 * second; every corner of either one, at its share of the line's length, is a place where the two are laid over each other: there the
 * point of A goes to the point of B. That gives A's corners a place on B's line and the other way round.
 */
private fun between(
    startA: GeoPosition, chainA: List<Vertex>, endA: GeoPosition,
    startB: GeoPosition, chainB: List<Vertex>, endB: GeoPosition,
): List<Slot> {
    val lineA = listOf(startA) + chainA.map { it.position } + endA
    val lineB = listOf(startB) + chainB.map { it.position } + endB
    val shareA = shares(lineA)
    val shareB = shares(lineB)

    // (where along the stretch, the corner that is there)
    val marks = ArrayList<Pair<Double, Vertex>>(chainA.size + chainB.size)
    for (i in chainA.indices) marks += shareA[i + 1] to chainA[i]
    for (i in chainB.indices) marks += shareB[i + 1] to chainB[i]
    marks.sortBy { it.first }

    return marks.map { (share, vertex) ->
        Slot(vertex, pointAt(lineA, shareA, share), pointAt(lineB, shareB, share))
    }
}

/** The corners of [line] as fractions (0..1) of its length; evenly spaced if it has no length at all. */
private fun shares(line: List<GeoPosition>): List<Double> {
    val along = DoubleArray(line.size)
    for (i in 1 until line.size) along[i] = along[i - 1] + distance(line[i - 1], line[i])
    val total = along.last()
    return List(line.size) { if (total <= 1e-12) it.toDouble() / (line.size - 1) else along[it] / total }
}

/** The point at [share] (0..1) of the way along [line], whose corners are at [shares]. */
private fun pointAt(line: List<GeoPosition>, shares: List<Double>, share: Double): GeoPosition {
    var i = 1
    while (i < line.size - 1 && shares[i] < share) i++
    val span = shares[i] - shares[i - 1]
    val within = if (span <= 1e-12) 0.0 else ((share - shares[i - 1]) / span).coerceIn(0.0, 1.0)
    return pointBetween(line[i - 1], line[i], within)
}

/** The point [fraction] of the way from [a] to [b] (longitude the short way), in double precision: it is where a corner starts, not a frame. */
private fun pointBetween(a: GeoPosition, b: GeoPosition, fraction: Double): GeoPosition {
    var dLon = b.longitude - a.longitude
    if (dLon > 180.0) dLon -= 360.0 else if (dLon < -180.0) dLon += 360.0
    var longitude = a.longitude + dLon * fraction
    if (longitude > 180.0) longitude -= 360.0 else if (longitude < -180.0) longitude += 360.0
    return GeoPosition(
        a.latitude + (b.latitude - a.latitude) * fraction,
        longitude.coerceIn(-180.0, 180.0),
        a.altitude + (b.altitude - a.altitude) * fraction,
    )
}

/** A length in degrees (the long way round the antimeridian does not count), only used to tell how long one stretch is against another. */
private fun distance(a: GeoPosition, b: GeoPosition): Double {
    var dLon = abs(a.longitude - b.longitude)
    if (dLon > 180.0) dLon = 360.0 - dLon
    return hypot(a.latitude - b.latitude, dLon)
}

private fun rotatedTo(ring: List<Vertex>, first: VertexId): List<Vertex> {
    val at = ring.indexOfFirst { it.id == first }
    return ring.drop(at) + ring.take(at)
}
