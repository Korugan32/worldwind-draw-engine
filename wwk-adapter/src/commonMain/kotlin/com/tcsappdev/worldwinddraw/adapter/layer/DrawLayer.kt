package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPlacemark
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.render.Renderable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps a WWK [RenderableLayer] in sync with [entityRepository]'s live entity list, so a
 * host app can add one [DrawLayer] to its `WorldWindow` and never touch WWK renderables
 * directly.
 *
 * Rebuilds the whole layer on every emission rather than diffing old vs. new state. That's
 * the simplest correct thing, and entity lists here are small (shapes a user drew, not bulk
 * data), so the cost is negligible.
 *
 * Every renderable is tagged with its originating [EntityId] via [ENTITY_ID_KEY] (see
 * [Renderable.putUserProperty]) — this is how `adapter.input.PickInputBridge` maps a WWK
 * pick result back to a [com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity].
 */
class DrawLayer(
    entityRepository: EntityRepository,
    scope: CoroutineScope,
) : RenderableLayer("Draw Layer") {
    init {
        entityRepository.entities
            .onEach { entities ->
                clearRenderables()
                entities.filterIsInstance<DrawnPolygon>().forEach { addRenderable(it.toWwkPolygon().tagged(it.id)) }
                entities.filterIsInstance<DrawnPolyline>().forEach { addRenderable(it.toWwkPolyline().tagged(it.id)) }
                entities.filterIsInstance<DrawnMarker>().forEach { addRenderable(it.toWwkPlacemark().tagged(it.id)) }
            }
            .launchIn(scope)
    }

    private fun <T : Renderable> T.tagged(entityId: EntityId): T = apply { putUserProperty(ENTITY_ID_KEY, entityId) }

    companion object {
        /** [Renderable.putUserProperty] key every renderable this layer creates is tagged with. */
        const val ENTITY_ID_KEY = "com.tcsappdev.worldwinddraw.entityId"
    }
}
