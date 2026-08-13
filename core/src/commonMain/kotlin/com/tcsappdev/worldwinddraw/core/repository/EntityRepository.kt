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
class EntityRepository {
    private val _entities = MutableStateFlow<List<DrawableEntity>>(emptyList())
    val entities : StateFlow<List<DrawableEntity>> = _entities.asStateFlow()

    fun add(entity: DrawableEntity) {
        _entities.value += entity
    }

    fun remove(entityId: EntityId) {
        _entities.value = _entities.value.filter { it.id != entityId }
    }

    fun update(entity: DrawableEntity) {
        _entities.value = _entities.value.map { if (it.id == entity.id) entity else it }
    }
}