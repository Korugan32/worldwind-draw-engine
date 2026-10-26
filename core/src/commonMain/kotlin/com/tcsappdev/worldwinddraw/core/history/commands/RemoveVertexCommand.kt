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
class RemoveVertexCommand private constructor(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val vertexId: VertexId,
    private var removedVertex: Vertex?,
    private var removedAtIndex: Int,
) : Command {
    constructor(repository: EntityRepository, entityId: EntityId, removedVertex: Vertex, removedAtIndex: Int) :
        this(repository, entityId, removedVertex.id, removedVertex, removedAtIndex)

    /**
     * Removes the vertex with this [vertexId]: the vertex and its position in the list are looked up and remembered when
     * [execute] runs (so the pure reducer can build one without reading the repository), and undo puts it back exactly
     * there. If the entity or the vertex isn't there at that moment, executing and undoing do nothing.
     */
    constructor(repository: EntityRepository, entityId: EntityId, vertexId: VertexId) :
        this(repository, entityId, vertexId, null, -1)

    override fun execute() {
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        if (removedVertex == null) {
            // Built from an id: find the real vertex and where it sits.
            val vertices = when (entity) {
                is DrawnPolygon -> entity.vertices
                is DrawnPolyline -> entity.vertices
                is DrawnMarker -> return
            }
            val index = vertices.indexOfFirst { it.id == vertexId }
            if (index < 0) return
            removedVertex = vertices[index]
            removedAtIndex = index
        }
        val removedVertex = removedVertex ?: return
        val updatedEntity = when (entity) {
            is DrawnPolygon -> entity.copy(vertices = entity.vertices.filter { it.id != removedVertex.id })
            is DrawnPolyline -> entity.copy(vertices = entity.vertices.filter { it.id != removedVertex.id })
            is DrawnMarker -> entity
        }
        repository.update(updatedEntity)
    }

    override fun undo() {
        val removedVertex = removedVertex ?: return // execute never found the vertex: nothing was removed
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
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
