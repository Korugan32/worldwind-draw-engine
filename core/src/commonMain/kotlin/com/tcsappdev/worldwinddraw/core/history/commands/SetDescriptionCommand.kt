package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.withDescription
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Gives the shape [entityId] the description [newDescription] (`null` takes it away); undoing puts back the one it had when this
 * ran, read at that moment so the pure reducer can build the command without reading the repository. Works for every kind of shape.
 * Does nothing if the entity no longer exists.
 */
class SetDescriptionCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val newDescription: String?,
) : Command {
    private var oldDescription: String? = null
    private var applied = false

    override fun execute() {
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        oldDescription = entity.description
        applied = true
        repository.update(entity.withDescription(newDescription))
    }

    override fun undo() {
        if (!applied) return
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        repository.update(entity.withDescription(oldDescription))
    }
}
