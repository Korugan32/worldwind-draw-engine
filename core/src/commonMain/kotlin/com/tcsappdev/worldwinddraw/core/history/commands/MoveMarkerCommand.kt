package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Moves the [DrawnMarker] [entityId] between [oldPosition] and [newPosition]. The counterpart of
 * [MoveVertexCommand] for a marker, which has one position instead of vertices. Does nothing if
 * [entityId] isn't a marker (or no longer exists).
 */
class MoveMarkerCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val oldPosition: GeoPosition,
    private val newPosition: GeoPosition,
) : Command {
    override fun execute() = moveTo(newPosition)
    override fun undo() = moveTo(oldPosition)

    private fun moveTo(position: GeoPosition) {
        val marker = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnMarker ?: return
        repository.update(marker.copy(position = position))
    }
}
