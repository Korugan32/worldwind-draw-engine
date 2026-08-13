package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Removes [entity] from [repository]; undoing adds it back. Holds the full [entity], not
 * just its id — undo needs to recreate it, and just an id wouldn't be enough for that.
 */
class DeleteEntityCommand(
    private val repository: EntityRepository,
    private val entity: DrawableEntity,
) : Command {
    override fun execute() {
        repository.remove(entity.id)
    }

    override fun undo() {
        repository.add(entity)
    }
}