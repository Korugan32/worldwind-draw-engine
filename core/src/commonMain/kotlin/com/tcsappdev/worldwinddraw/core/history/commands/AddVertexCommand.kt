package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.*
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Inserts [newVertex] into [entityId] right after [afterEdgeIndex]: edge `i` runs from the
 * vertex at index `i` to the next one, and the new vertex goes between the two.
 * For a polygon with several rings, [ringIndex] says which of them (`0`, the first, by default).
 * Undo removes it again by [newVertex]'s id. Only applies to [DrawnPolygon]/
 * [DrawnPolyline]; [DrawnMarker] has no vertices to insert into.
 */
class AddVertexCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val afterEdgeIndex: Int,
    private val newVertex: Vertex,
    private val ringIndex: Int = 0,
) : Command {
    override fun execute() {
        val entity = repository.entities.value.first { it.id == entityId }
        val updatedEntity = when (entity) {
            is DrawnPolygon -> {
                val newList = entity.rings[ringIndex].toMutableList().apply { add(afterEdgeIndex + 1, newVertex) }
                entity.withRing(ringIndex, newList)
            }
            is DrawnPolyline -> {
                val newList = entity.vertices.toMutableList().apply { add(afterEdgeIndex + 1, newVertex) }
                entity.copy(vertices = newList)
            }
            is DrawnMarker -> entity
        }
        repository.update(updatedEntity)
    }

    override fun undo() {
        val entity = repository.entities.value.first { it.id == entityId }
        val updatedEntity = when (entity) {
            is DrawnPolygon -> entity.mapRings { ring -> ring.filter { it.id != newVertex.id } }
            is DrawnPolyline -> entity.copy(vertices = entity.vertices.filter { it.id != newVertex.id })
            is DrawnMarker -> entity
        }
        repository.update(updatedEntity)
    }
}