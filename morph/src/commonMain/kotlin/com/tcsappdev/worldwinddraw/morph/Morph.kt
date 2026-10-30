package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId

/**
 * The drawing at a moment between two [Snapshot]s: what a transition shows while it plays.
 *
 * **A shape that is in both** and has the same vertices in both (the same [VertexId]s, in the same order, in each ring) *morphs*:
 * every vertex moves in a straight line to where it is in the second, and so do the size of a marker, the width of a line and
 * the opacity or colour of a look. What has no middle (a text, an image, a list of properties, a look that changes kind) is
 * the first one's up to the half way point and the second one's after it.
 *
 * **A polygon whose corners changed but share some** (a merge adds the other shape's corners, a cut takes some away, a point was added or
 * deleted) *grows or shrinks*: the corners it has in both stay or move straight, and the ones that are only in one slide along the outline
 * to or from where they are in the other (see [alignOutlines]), so the shape starts as exactly the first outline and ends as exactly the
 * second. That needs at least three corners in common, in the same order round both (either way round), and the same holes.
 *
 * **Any other shape crossfades.** A shape that is in only one snapshot, or whose outline cannot be grown from the first into the second,
 * cannot be moved vertex to vertex: the first fades out while the second fades in. The second of a pair like that is a copy under
 * another id (the id with `#to` added), because both are on the map at once.
 *
 * The result is in the order of the first snapshot, with what fades in after it in the order of the second. `t` is how far along it is:
 * at 0 the result is exactly the first snapshot's shapes and at 1 exactly the second's, so a transition begins and ends on the real drawing.
 */
object Morph {
    fun between(from: Snapshot, to: Snapshot, t: Float): List<DrawableEntity> {
        require(!t.isNaN()) { "t is not a number" }
        if (t <= 0f) return from.entities
        if (t >= 1f) return to.entities

        val fromById = from.entities.associateBy { it.id }
        val toById = to.entities.associateBy { it.id }
        val usedIds = (fromById.keys + toById.keys).toMutableSet()

        val result = ArrayList<DrawableEntity>(from.entities.size + to.entities.size)
        val morphed = HashSet<EntityId>()
        for (a in from.entities) {
            val b = toById[a.id]
            val between = if (b == null) null else morph(a, b, t)
            if (between != null) morphed += a.id
            result += between ?: a.faded(1f - t)
        }
        for (b in to.entities) {
            when {
                fromById[b.id] == null -> result += b.faded(t)
                b.id in morphed -> Unit
                else -> result += b.withId(freeId(b.id, usedIds)).faded(t)
            }
        }
        return result
    }

    private fun freeId(id: EntityId, usedIds: MutableSet<EntityId>): EntityId {
        var candidate = EntityId(id.id + "#to")
        while (candidate in usedIds) candidate = EntityId(candidate.id + "#to")
        usedIds += candidate
        return candidate
    }

    private fun DrawableEntity.withId(id: EntityId): DrawableEntity = when (this) {
        is DrawnPolygon -> copy(id = id)
        is DrawnPolyline -> copy(id = id)
        is DrawnMarker -> copy(id = id)
    }

    /** [a] at [t] on its way to [b], or `null` when it cannot (a different kind of shape, or an outline that is not the same shape changed). */
    private fun morph(a: DrawableEntity, b: DrawableEntity, t: Float): DrawableEntity? = when {
        a is DrawnPolygon && b is DrawnPolygon -> morphPolygon(a, b, t)
        a is DrawnPolyline && b is DrawnPolyline && ids(a.vertices) == ids(b.vertices) -> morphPolyline(a, b, t)
        a is DrawnMarker && b is DrawnMarker -> morphMarker(a, b, t)
        else -> null
    }

    private fun ids(ring: List<Vertex>): List<VertexId> = ring.map { it.id }

    private fun morphPolygon(a: DrawnPolygon, b: DrawnPolygon, t: Float): DrawnPolygon? {
        if (a.extraRings.map(::ids) != b.extraRings.map(::ids)) return null
        val vertices = if (ids(a.vertices) == ids(b.vertices)) lerpVertices(a.vertices, b.vertices, t)
        else alignOutlines(a.vertices, b.vertices)?.map { it.at(t) } ?: return null
        return a.copy(
            style = lerpStyle(a.style, b.style, t),
            vertices = vertices,
            outlineStyle = lerpOptionalStyle(a.outlineStyle, b.outlineStyle, t),
            outlineWidth = lerp(a.outlineWidth, b.outlineWidth, t),
            extraRings = a.extraRings.zip(b.extraRings) { ringA, ringB -> lerpVertices(ringA, ringB, t) },
            description = step(a.description, b.description, t),
            zIndex = step(a.zIndex, b.zIndex, t),
            properties = step(a.properties, b.properties, t),
        )
    }

    private fun morphPolyline(a: DrawnPolyline, b: DrawnPolyline, t: Float) = a.copy(
        style = lerpStyle(a.style, b.style, t),
        vertices = lerpVertices(a.vertices, b.vertices, t),
        lineWidth = lerp(a.lineWidth, b.lineWidth, t),
        description = step(a.description, b.description, t),
        zIndex = step(a.zIndex, b.zIndex, t),
        properties = step(a.properties, b.properties, t),
    )

    private fun morphMarker(a: DrawnMarker, b: DrawnMarker, t: Float) = a.copy(
        style = lerpStyle(a.style, b.style, t),
        position = lerpPosition(a.position, b.position, t),
        size = lerp(a.size, b.size, t),
        label = lerpLabel(a.label, b.label, t),
        description = step(a.description, b.description, t),
        zIndex = step(a.zIndex, b.zIndex, t),
        properties = step(a.properties, b.properties, t),
    )
}
