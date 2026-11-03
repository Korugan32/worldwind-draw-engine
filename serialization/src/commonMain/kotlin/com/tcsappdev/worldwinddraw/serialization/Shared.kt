package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId

/**
 * What reading a GeoJSON or KML text made: the shapes it could use, a line in [warnings] for everything it had to leave out or guess,
 * and in [leftOut] how many of each kind of thing was not imported at all (a `GroundOverlay`, a feature without a geometry, a ring of
 * two points), so a host can say "3 things were not imported" without reading the lines. Things that were imported but guessed, like
 * a colour that wasn't one, are only in [warnings].
 */
public class ImportResult(
    public val entities: List<com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity>,
    public val warnings: List<String>,
    public val leftOut: Map<String, Int> = emptyMap(),
)

/** What the readers keep while reading: the warnings, and the count of what was left out, by kind. */
internal class ReadLog {
    val warnings = ArrayList<String>()
    val leftOut = LinkedHashMap<String, Int>()

    /** A thing that was imported with a guess or a default. */
    fun warn(message: String) {
        warnings += message
    }

    /** A thing that was not imported, of the kind [kind]. */
    fun leave(kind: String, message: String) {
        warnings += message
        leftOut[kind] = (leftOut[kind] ?: 0) + 1
    }
}

// What the file formats that carry no ids and no rings of their own (GeoJSON, KML) have in common.

/** Hands out shape ids that are still free: the one asked for, or that with a number added. */
internal class UniqueIds {
    private val taken = HashSet<String>()

    fun make(base: String, suffix: Int? = null): String {
        val wanted = if (suffix == null) base else "$base-$suffix"
        var id = wanted
        var n = 2
        while (!taken.add(id)) id = "$wanted~${n++}"
        return id
    }
}

/** A ring as the files write it closes itself by repeating its first point; the model closes a ring on its own, so that point goes. */
internal fun withoutClosingPoint(positions: List<GeoPosition>): List<GeoPosition> =
    if (positions.size > 1 && positions.first().latitude == positions.last().latitude && positions.first().longitude == positions.last().longitude) {
        positions.dropLast(1)
    } else {
        positions
    }

/** Vertex ids for a shape read from a file, which has none: `<shape>-<ring>-<index>`. */
internal fun vertices(shapeId: String, ring: Int, positions: List<GeoPosition>): List<Vertex> =
    positions.mapIndexed { i, p -> Vertex(VertexId("$shapeId-$ring-$i"), p) }

/**
 * One part's rings ready to be written: the outline counter-clockwise first, the holes clockwise after it (what RFC 7946 and KML ask for),
 * whichever way they were drawn. The rings are not closed here.
 */
internal fun orientedPart(rings: List<List<GeoPosition>>): List<List<GeoPosition>> =
    rings.mapIndexed { i, ring -> if ((i == 0) == (signedArea(ring) > 0)) ring else ring.reversed() }

// Positive when the ring runs counter-clockwise (longitude as x, latitude as y).
internal fun signedArea(ring: List<GeoPosition>): Double {
    var sum = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        sum += a.longitude * b.latitude - b.longitude * a.latitude
    }
    return sum / 2
}
