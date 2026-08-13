package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.*
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Removes [removedVertex] from [entityId]; undo re-inserts it at [removedAtIndex] (its
 * original position, not just "somewhere") so the vertex order is exactly restored.
 *
 * Note: if this would drop a [DrawnPolygon]/[DrawnPolyline] below its minimum vertex
 * count, [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon]'s own `init` check
 * throws rather than silently producing an invalid shape. Callers (eventually
 * `VertexEditController` in a later phase) are expected to prevent that case in the UI
 * — e.g. disable the remove action — rather than this command swallowing the error.
 */
class RemoveVertexCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val removedVertex: Vertex,
    private val removedAtIndex: Int,
) : Command {
    override fun execute() {
        val entity = repository.entities.value.first { it.id == entityId }
        val updatedEntity = when (entity) {
            is DrawnPolygon -> entity.copy(vertices = entity.vertices.filter { it.id != removedVertex.id })
            is DrawnPolyline -> entity.copy(vertices = entity.vertices.filter { it.id != removedVertex.id })
            is DrawnMarker -> entity
        }
        repository.update(updatedEntity)
    }

    override fun undo() {
        val entity = repository.entities.value.first { it.id == entityId }
        val updatedEntity = when (entity) {
            is DrawnPolygon -> {
                val newList = entity.vertices.toMutableList().apply { add(removedAtIndex, removedVertex) }
                entity.copy(vertices = newList)
            }
            is DrawnPolyline -> {
                val newList = entity.vertices.toMutableList().apply { add(removedAtIndex, removedVertex) }
                entity.copy(vertices = newList)
            }
            is DrawnMarker -> entity
        }
        repository.update(updatedEntity)
    }
}
