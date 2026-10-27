package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Removes the whole ring of the polygon [entityId] that holds the vertex [vertexId]: a part, or a hole. Undo puts the ring back
 * where it was among the others, with all its vertices. The ring and its place are looked up when [execute] runs (so the pure
 * reducer can build the command without reading the repository). Does nothing if the entity isn't a polygon, the vertex isn't in
 * it, or the ring is its only one: a polygon cannot go without one.
 */
class RemoveRingCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val vertexId: VertexId,
) : Command {
    private var removed: List<Vertex>? = null
    private var removedAt = -1

    override fun execute() {
        val polygon = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnPolygon ?: return
        val index = polygon.ringIndexOf(vertexId)
        if (index < 0 || polygon.rings.size < 2) return
        removed = polygon.rings[index]
        removedAt = index
        repository.update(polygon.withoutRing(index))
    }

    override fun undo() {
        val ring = removed ?: return
        val polygon = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnPolygon ?: return
        repository.update(polygon.withRingInserted(removedAt, ring))
        removed = null
    }
}
