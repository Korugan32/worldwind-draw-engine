package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.SpanMover
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Which anchors a dragged vertex has on either side, and the shape's vertices as they were when the drag first changed
 * anything. A drag is a stream of commands, each one saying "the vertex is now *here*"; every one of them has to start
 * from the same original vertices (not from what the last one left), or the hidden vertices would be moved again and
 * again. The commands therefore share this object, and the first one to run records the vertices in it. It is a holder,
 * not a value: two spans are never equal unless they are the same object.
 */
class VertexSpan(val prevId: VertexId?, val nextId: VertexId?) {
    /** The shape's vertices before the drag changed anything; `null` until the first command runs. */
    var originalVertices: List<Vertex>? = null
        internal set
}

/**
 * Moves the vertex [vertexId] of [entityId] to [newPosition] and, in proportion, the vertices hidden between it and
 * its neighbouring anchors in [span] (see [SpanMover]). Like [MoveVertexCommand], running it again with another position
 * gives the same result as running it once: it always starts from the vertices [span] remembers, so a drag's live
 * previews and its final, recorded command don't add up, and undo puts every vertex back.
 */
class MoveVertexWithSpanCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val vertexId: VertexId,
    private val newPosition: GeoPosition,
    private val span: VertexSpan,
) : Command {
    override fun execute() {
        val entity = repository.entities.value.first { it.id == entityId }
        val (current, isClosed) = verticesOf(entity) ?: return
        val original = span.originalVertices ?: current.also { span.originalVertices = it }
        val moved = SpanMover.move(original, vertexId, span.prevId, span.nextId, newPosition, isClosed)
        repository.update(withVertices(entity, moved))
    }

    override fun undo() {
        val original = span.originalVertices ?: return
        val entity = repository.entities.value.first { it.id == entityId }
        repository.update(withVertices(entity, original))
    }

    private fun verticesOf(entity: com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity): Pair<List<Vertex>, Boolean>? = when (entity) {
        is DrawnPolygon -> entity.vertices to true
        is DrawnPolyline -> entity.vertices to false
        is DrawnMarker -> null
    }

    private fun withVertices(entity: com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity, vertices: List<Vertex>) = when (entity) {
        is DrawnPolygon -> entity.copy(vertices = vertices)
        is DrawnPolyline -> entity.copy(vertices = vertices)
        is DrawnMarker -> entity
    }
}
