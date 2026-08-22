package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPlacemark
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import earth.worldwind.layer.RenderableLayer
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
 */
class DrawLayer(
    entityRepository: EntityRepository,
    scope: CoroutineScope,
) : RenderableLayer("Draw Layer") {
    init {
        entityRepository.entities
            .onEach { entities ->
                clearRenderables()
                entities.filterIsInstance<DrawnPolygon>().forEach { addRenderable(it.toWwkPolygon()) }
                entities.filterIsInstance<DrawnPolyline>().forEach { addRenderable(it.toWwkPolyline()) }
                entities.filterIsInstance<DrawnMarker>().forEach { addRenderable(it.toWwkPlacemark()) }
            }
            .launchIn(scope)
    }
}
