package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Removes an entity from [repository]; undoing adds it back. Holds the full entity, not just
 * its id — undo needs to recreate it, and just an id wouldn't be enough for that.
 *
 * Built from an entity directly, or from just an [EntityId]: then the entity is looked up and
 * remembered at the moment [execute] runs. That is what lets the (pure) reducer build one without
 * reading the repository, and it captures the entity as it is *at that moment* — including any
 * edits made since it was drawn. If no such entity exists at that moment, executing (and undoing)
 * does nothing.
 */
internal class DeleteEntityCommand private constructor(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private var entity: DrawableEntity?,
) : Command {
    constructor(repository: EntityRepository, entity: DrawableEntity) : this(repository, entity.id, entity)

    constructor(repository: EntityRepository, entityId: EntityId) : this(repository, entityId, null)

    override fun execute() {
        val target = entity ?: repository.entities.value.firstOrNull { it.id == entityId } ?: return
        entity = target
        repository.remove(entityId)
    }

    override fun undo() {
        entity?.let { repository.add(it) }
    }
}
