package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.withZIndex
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Moves the shape [entityId] to the place [newZIndex] in the drawing order (see [com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity.zIndex]);
 * undoing puts back the place it had when this ran, read at that moment so the pure reducer can build the command without reading the
 * repository. Works for every kind of shape. Does nothing if the entity no longer exists.
 */
class SetZIndexCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val newZIndex: Int,
) : Command {
    private var oldZIndex = 0
    private var applied = false

    override fun execute() {
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        oldZIndex = entity.zIndex
        applied = true
        repository.update(entity.withZIndex(newZIndex))
    }

    override fun undo() {
        if (!applied) return
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        repository.update(entity.withZIndex(oldZIndex))
    }
}
