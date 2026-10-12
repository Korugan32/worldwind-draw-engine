package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * Selecting an entity adds a separate highlight renderable, and that renderable must be
     * invisible to picking — untagged and `isPickEnabled = false` — so a tap on the
     * highlighted border still resolves to the real, tagged polygon (verified visually too:
     * tapping a highlighted polyline's halo still advanced Select -> Edit).
     */
    @Test
    fun selectingAnEntityAddsANonPickableUntaggedHighlight() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("A")
        repository.add(
            DrawnPolygon(
                id = id,
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = listOf(
                    Vertex(VertexId("v1"), GeoPosition(0.0, 0.0)),
                    Vertex(VertexId("v2"), GeoPosition(1.0, 0.0)),
                    Vertex(VertexId("v3"), GeoPosition(1.0, 1.0)),
                ),
            )
        )
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        assertEquals(1, layer.count, "unselected: just the polygon itself")

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(id)))
        delay(50)

        assertEquals(2, layer.count, "selected: the polygon plus its highlight")
        val tagged = layer.filter { it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) }
        val untagged = layer.filterNot { it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) }
        assertEquals(1, tagged.size)
        assertTrue(tagged.single().isPickEnabled)
        assertEquals(1, untagged.size)
        assertFalse(untagged.single().isPickEnabled)

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        delay(50)
        assertEquals(1, layer.count, "deselected: the highlight is gone")
    }

    private fun triangle(id: EntityId) = DrawnPolygon(
        id = id,
        style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
        vertices = listOf(
            Vertex(VertexId("v1"), GeoPosition(0.0, 0.0)),
            Vertex(VertexId("v2"), GeoPosition(1.0, 0.0)),
            Vertex(VertexId("v3"), GeoPosition(1.0, 1.0)),
        ),
    )

    private fun highlightAlpha(layer: DrawLayer): Float =
        (layer.single { !it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) } as Polygon).attributes.outlineColor.alpha

    @Test
    fun ticksPulseTheHighlightAlphaAndRequestARedrawEachTime() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("A")
        repository.add(triangle(id))
        var redraws = 0
        val layer = DrawLayer(repository, stateMachine, backgroundScope, onContentChanged = { redraws++ })
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(id)))
        delay(50)
        assertEquals(1f, highlightAlpha(layer), "starts fully opaque")

        val before = redraws
        layer.onTick(350) // half of the 700ms Reverse cycle: partway to the minimum alpha
        val halfway = highlightAlpha(layer)
        assertTrue(halfway < 1f && halfway > 0.2f, "partway faded, was $halfway")
        assertEquals(before + 1, redraws)

        layer.onTick(350) // a full cycle in: at the minimum
        assertTrue(highlightAlpha(layer) < halfway)
    }

    @Test
    fun ticksDoNothingAndRequestNoRedrawWhileNothingIsHighlighted() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        repository.add(triangle(EntityId("A")))
        var redraws = 0
        val layer = DrawLayer(repository, stateMachine, backgroundScope, onContentChanged = { redraws++ })
        delay(50)

        val before = redraws
        repeat(10) { layer.onTick(16) }

        // WWK renders on demand: an idle map must not be redrawn 60 times a second.
        assertEquals(before, redraws)
    }

    @Test
    fun highlightColorFollowsHighlightStyleAndCanBeChangedLive() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("A")
        repository.add(triangle(id))
        val layer = DrawLayer(repository, stateMachine, backgroundScope, highlightStyle = HighlightStyle(selectedColor = ColorRgba(255, 0, 0)))
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(id)))
        delay(50)
        fun outline() = (layer.single { !it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) } as Polygon).attributes.outlineColor
        assertEquals(1f, outline().red)
        assertEquals(0f, outline().blue)

        layer.highlightStyle = HighlightStyle(selectedColor = ColorRgba(0, 0, 255))
        delay(50)

        assertEquals(0f, outline().red)
        assertEquals(1f, outline().blue)
    }

    @Test
    fun aTranslucentHighlightColorStaysTranslucentWhilePulsing() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("A")
        repository.add(triangle(id))
        val layer = DrawLayer(repository, stateMachine, backgroundScope, highlightStyle = HighlightStyle(selectedColor = ColorRgba(255, 0, 0, alpha = 128)))
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(id)))
        delay(50)

        assertEquals(128 / 255f, highlightAlpha(layer), "starts at the colour's own opacity, not forced to 1")
        layer.onTick(350)
        assertTrue(highlightAlpha(layer) < 128 / 255f, "the pulse fades it from there")
    }
}
