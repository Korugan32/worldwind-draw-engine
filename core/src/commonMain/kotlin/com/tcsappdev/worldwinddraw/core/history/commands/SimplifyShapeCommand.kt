package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.Visvalingam
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlin.math.roundToInt

/**
 * Cuts a polygon or polyline down to about [keepFraction] of its vertices (see [Visvalingam]); undo brings every vertex back, in
 * its place. The vertices that are kept are the original ones, ids and all, and a polygon never goes below 3 or a polyline below 2.
 *
 * The shape's vertices are remembered when [execute] first runs, so the pure reducer can build the command without reading the
 * repository, and running it again (redo) gives the same result from the same starting vertices.
 */
class SimplifyShapeCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val keepFraction: Float,
) : Command {
    init {
        require(keepFraction > 0f && keepFraction <= 1f) { "keepFraction must be in (0, 1], was $keepFraction" }
    }

    private var original: List<Vertex>? = null

    override fun execute() {
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        val (current, isClosed) = when (entity) {
            is DrawnPolygon -> entity.vertices to true
            is DrawnPolyline -> entity.vertices to false
            is DrawnMarker -> return
        }
        val source = original ?: current.also { original = it }
        val kept = Visvalingam.simplify(source.map { it.position }, isClosed, (source.size * keepFraction).roundToInt())
        replaceVertices(entity, kept.map { source[it] })
    }

    override fun undo() {
        val source = original ?: return
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        replaceVertices(entity, source)
    }

    private fun replaceVertices(entity: com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity, vertices: List<Vertex>) {
        repository.update(
            when (entity) {
                is DrawnPolygon -> entity.copy(vertices = vertices)
                is DrawnPolyline -> entity.copy(vertices = vertices)
                is DrawnMarker -> entity
            }
        )
    }
}
