package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/** Adds [entity] to [repository]; undoing removes it again by [DrawableEntity.id]. */
internal class AddEntityCommand(
    private val repository: EntityRepository,
    private val entity: DrawableEntity,
) : Command {
    override fun execute() {
        repository.add(entity)
    }

    override fun undo() {
        repository.remove(entity.id)
    }
}