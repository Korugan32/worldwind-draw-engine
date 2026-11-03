package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.withProperties
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Gives the shape [entityId] the properties [newProperties] (all of them: what it had is replaced; an empty map takes them away);
 * undoing puts back the ones it had when this ran, read at that moment so the pure reducer can build the command without reading the
 * repository. Works for every kind of shape. Does nothing if the entity no longer exists.
 */
internal class SetPropertiesCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val newProperties: Map<String, String>,
) : Command {
    private var oldProperties: Map<String, String> = emptyMap()
    private var applied = false

    override fun execute() {
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        oldProperties = entity.properties
        applied = true
        repository.update(entity.withProperties(newProperties))
    }

    override fun undo() {
        if (!applied) return
        val entity = repository.entities.value.firstOrNull { it.id == entityId } ?: return
        repository.update(entity.withProperties(oldProperties))
    }
}
