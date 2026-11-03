package com.tcsappdev.worldwinddraw.core.repository

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The live, in-memory source of truth for which shapes currently exist. Deliberately
 * separate from [com.tcsappdev.worldwinddraw.core.state.DrawEngineState], which only
 * tracks interaction mode — [com.tcsappdev.worldwinddraw.core.history.Command]s (via
 * `HistoryManager`) are what mutate this.
 */
public class EntityRepository {
    private val _entities = MutableStateFlow<List<DrawableEntity>>(emptyList())
    /** The shapes that exist now, in drawing order (the last is drawn on top of the ones before it, unless a [DrawableEntity.zIndex] says otherwise). Collect it to follow every change. */
    public val entities : StateFlow<List<DrawableEntity>> = _entities.asStateFlow()

    /** Puts [entity] after the shapes there are. Not a history step (it cannot be undone). */
    public fun add(entity: DrawableEntity) {
        _entities.value += entity
    }

    /** Takes away the shape with the id [entityId]; nothing happens if there is none. Not a history step. */
    public fun remove(entityId: EntityId) {
        _entities.value = _entities.value.filter { it.id != entityId }
    }

    /** Replaces the shape that has the id of [entity] by [entity], in the same place in the order; nothing happens if there is none. Not a history step. */
    public fun update(entity: DrawableEntity) {
        _entities.value = _entities.value.map { if (it.id == entity.id) entity else it }
    }

    /**
     * Makes [newEntities], in that order, the shapes that exist, whatever there was before, as ONE change: whoever watches [entities]
     * sees the old list and then the new one, never the many in-between lists that removing and adding one by one would show (and
     * that would each make the map redraw). What a loaded file or a restored snapshot needs. An empty list clears the repository.
     * Not a history step: it is a command's job to make a change that can be undone, and replacing everything is meant for the host
     * to set the scene (clear the history with it, see `HistoryManager.clear`).
     *
     * @throws IllegalArgumentException if two of [newEntities] have the same id; nothing changes then.
     */
    public fun replaceAll(newEntities: List<DrawableEntity>) {
        require(newEntities.map { it.id }.toSet().size == newEntities.size) { "two shapes have the same id" }
        _entities.value = newEntities.toList()
    }
}