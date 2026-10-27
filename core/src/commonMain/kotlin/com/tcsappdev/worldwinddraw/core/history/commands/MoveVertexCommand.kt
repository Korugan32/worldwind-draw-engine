package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.*
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Moves a single vertex ([vertexId]) of [entityId] between [oldPosition] and
 * [newPosition]. Only [DrawnPolygon] and [DrawnPolyline] have vertices — [DrawnMarker] is
 * left untouched, since [DrawableEntity] doesn't expose a shared "vertices" contract for
 * a `when` to dispatch on generically.
 */
class MoveVertexCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val vertexId: VertexId,
    private val oldPosition: GeoPosition,
    private val newPosition: GeoPosition,
) : Command {
    override fun execute() = moveVertexTo(newPosition)
    override fun undo() = moveVertexTo(oldPosition)

    private fun moveVertexTo(position: GeoPosition) {
        val entity = repository.entities.value.first { it.id == entityId }
        val updatedEntity = when (entity) {
            is DrawnPolygon -> entity.mapRings { replaceVertexPosition(it, position) }
            is DrawnPolyline -> entity.copy(vertices = replaceVertexPosition(entity.vertices, position))
            is DrawnMarker -> entity
        }
        repository.update(updatedEntity)
    }

    private fun replaceVertexPosition(vertices: List<Vertex>, position: GeoPosition): List<Vertex> {
        return vertices.map { vertex -> if (vertex.id == vertexId) vertex.copy(position = position) else vertex }
    }
}