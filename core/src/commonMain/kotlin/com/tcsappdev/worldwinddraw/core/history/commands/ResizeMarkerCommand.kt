package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Sets the size of the [DrawnMarker] [entityId] to [newSize]; undoing puts back the size it had when
 * this ran. The old size is read at that moment (not when the command is built), which is what
 * lets the pure reducer create one without reading the repository — the same approach as
 * [DeleteEntityCommand]. Does nothing if [entityId] isn't a marker (or no longer exists).
 */
internal class ResizeMarkerCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val newSize: Float,
) : Command {
    private var oldSize: Float? = null

    init {
        require(newSize > 0f) { "size must be positive, was $newSize" }
    }

    override fun execute() {
        val marker = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnMarker ?: return
        oldSize = marker.size
        repository.update(marker.copy(size = newSize))
    }

    override fun undo() {
        val previous = oldSize ?: return
        val marker = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnMarker ?: return
        repository.update(marker.copy(size = previous))
    }
}
