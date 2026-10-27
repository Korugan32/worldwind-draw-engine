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
 * its place. The vertices that are kept are the original ones, ids and all, and a polygon ring never goes below 3 or a polyline below 2.
 * Every ring of a polygon is cut on its own, so a hole or a small part keeps its share of points instead of losing them to a big one.
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

    /** The shape's rings before it was cut: one list for a polyline. */
    private var original: List<List<Vertex>>? = null

    override fun execute() {
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        val (current, isClosed) = when (entity) {
            is DrawnPolygon -> entity.rings to true
            is DrawnPolyline -> listOf(entity.vertices) to false
            is DrawnMarker -> return
        }
        val source = original ?: current.also { original = it }
        replaceRings(entity, source.map { ring ->
            val kept = Visvalingam.simplify(ring.map { it.position }, isClosed, (ring.size * keepFraction).roundToInt())
            kept.map { ring[it] }
        })
    }

    override fun undo() {
        val source = original ?: return
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        replaceRings(entity, source)
    }

    private fun replaceRings(entity: com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity, rings: List<List<Vertex>>) {
        repository.update(
            when (entity) {
                is DrawnPolygon -> entity.copy(vertices = rings.first(), extraRings = rings.drop(1))
                is DrawnPolyline -> entity.copy(vertices = rings.first())
                is DrawnMarker -> entity
            }
        )
    }
}
