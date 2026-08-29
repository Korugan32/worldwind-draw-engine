package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DrawLayerTest {
    /**
     * Regression test for a real bug found via visual verification (2026-08-29): [DrawLayer]
     * updated its renderable list correctly on every entity/state change, but WWK's Android
     * surface only redraws on demand — a repository change landing outside an active WWK
     * gesture (e.g. Undo) left a stale frame on screen until an unrelated touch forced a
     * redraw. [DrawLayer.onContentChanged] is the fix; this locks in that it actually fires
     * on every emission, not just the first.
     *
     * Uses a bare [DrawnPolygon] with no [com.tcsappdev.worldwinddraw.core.state.DrawMode.Edit]
     * involved, so no vertex-handle [earth.worldwind.shape.Placemark] gets constructed — see
     * `StyleAttributesMapperTest`'s comment on why `PlacemarkAttributes()` isn't safe here
     * (Android's plain host unit tests can't satisfy its `Resources.getSystem()` call).
     */
    @Test
    fun notifiesOnContentChangedOnEveryEntityChange() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        var notifications = 0

        // backgroundScope (not `this`): DrawLayer's collector runs forever by design (it
        // stays subscribed for the layer's whole lifetime), so runTest would otherwise fail
        // with UncompletedCoroutinesError waiting for it to finish. backgroundScope's
        // coroutines are cancelled automatically when the test body returns.
        DrawLayer(repository, stateMachine, backgroundScope, onContentChanged = { notifications++ })
        delay(50) // let the initial (empty) emission process

        val afterSubscribe = notifications
        assertTrue(afterSubscribe >= 1)

        repository.add(
            DrawnPolygon(
                id = EntityId("A"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = listOf(
                    Vertex(VertexId("v1"), GeoPosition(0.0, 0.0)),
                    Vertex(VertexId("v2"), GeoPosition(1.0, 0.0)),
                    Vertex(VertexId("v3"), GeoPosition(1.0, 1.0)),
                ),
            )
        )
        delay(50)

        assertTrue(notifications > afterSubscribe)
    }
}
