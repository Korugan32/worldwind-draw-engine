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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
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

    private fun DrawLayer.shapeOf(id: EntityId) = single { it.getUserProperty<EntityId>(DrawLayer.ENTITY_ID_KEY) == id }

    /**
     * The point of updating incrementally: selecting one shape must not build the others again (a rebuilt WWK
     * shape costs its tessellation and buffers once more; with hundreds of big polygons that ran the heap out).
     */
    @Test
    fun selectingOneShapeKeepsEveryShapesRenderableAndOnlyAddsTheHighlight() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val ids = listOf("A", "B", "C").map(::EntityId)
        ids.forEach { repository.add(triangle(it)) }
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        val before = ids.associateWith { layer.shapeOf(it) }

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(ids[1])))
        delay(50)

        ids.forEach { assertSame(before.getValue(it), layer.shapeOf(it), "${it.id} must not be built again") }
        assertEquals(4, layer.count, "three shapes plus one highlight")

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(ids[2])))
        delay(50)
        ids.forEach { assertSame(before.getValue(it), layer.shapeOf(it)) }
        assertEquals(4, layer.count, "the highlight moved to C")
    }

    @Test
    fun deletingOneShapeKeepsTheOthersRenderables() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val (a, b) = listOf("A", "B").map(::EntityId)
        repository.add(triangle(a))
        repository.add(triangle(b))
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        val keptB = layer.shapeOf(b)

        repository.remove(a)
        delay(50)

        assertEquals(1, layer.count)
        assertSame(keptB, layer.shapeOf(b))
    }

    @Test
    fun changingOneShapeBuildsOnlyThatShapeAgain() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val (a, b) = listOf("A", "B").map(::EntityId)
        repository.add(triangle(a))
        repository.add(triangle(b))
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        val oldA = layer.shapeOf(a)
        val keptB = layer.shapeOf(b)

        val moved = triangle(a)
        repository.update(moved.copy(vertices = moved.vertices.mapIndexed { i, v -> if (i == 0) v.copy(position = GeoPosition(5.0, 5.0)) else v }))
        delay(50)

        assertNotSame(oldA, layer.shapeOf(a), "the changed shape is built again")
        assertSame(keptB, layer.shapeOf(b), "the other one is not")
    }

    /** A polygon whose 200 vertices all lie (to within a few metres) on a 40-degree circle: simplifying has plenty to drop. */
    private fun roundPolygon(id: EntityId, centerLat: Double = 0.0, centerLon: Double = 0.0, radius: Double = 20.0, vertexCount: Int = 200) = DrawnPolygon(
        id = id,
        style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
        vertices = List(vertexCount) { i ->
            val angle = 2 * kotlin.math.PI * i / vertexCount
            Vertex(VertexId("${id.id}$i"), GeoPosition(centerLat + radius * kotlin.math.sin(angle), centerLon + radius * kotlin.math.cos(angle)))
        },
    )

    private fun around(lat: Double, lon: Double) = GeoBounds(lat - 30, lat + 30, lon - 30, lon + 30)

    private fun DrawLayer.vertexCountOf(id: EntityId) = (shapeOf(id) as Polygon).getBoundary(0).size

    @Test
    fun aCoarseLevelDrawsAShapeWithFewerVerticesAndNoneDrawsItWhole() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("R")
        repository.add(roundPolygon(id))
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        assertEquals(200, layer.vertexCountOf(id))

        layer.applyLod(LodLevel.forTolerance(300_000.0, LodLevel.NONE)) // a camera far away: a pixel is 300 km
        // (The count is WWK's boundary, which also holds the points on the long edges that are left: about one every 2.8 degrees of this 125 degree outline.)
        assertTrue(layer.vertexCountOf(id) in 3..100, "simplified, got ${layer.vertexCountOf(id)}")

        layer.applyLod(LodLevel.NONE) // back to close up
        assertEquals(200, layer.vertexCountOf(id))
    }

    @Test
    fun theSameLevelDoesNotBuildAnythingAgain() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("R")
        repository.add(roundPolygon(id))
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        layer.applyLod(5)
        val coarse = layer.shapeOf(id)

        layer.applyLod(5)

        assertSame(coarse, layer.shapeOf(id))
    }

    @Test
    fun aShapeThatTheLevelCannotSimplifyIsNotBuiltAgainWhenTheLevelChanges() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("A")
        repository.add(triangle(id)) // 3 vertices: always drawn whole
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        val before = layer.shapeOf(id)

        layer.applyLod(8)

        assertSame(before, layer.shapeOf(id))
    }

    @Test
    fun theSimplifiedShapeIsKeptWhenAnotherOneIsSelected() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val (r, a) = listOf("R", "A").map(::EntityId)
        repository.add(roundPolygon(r))
        repository.add(triangle(a))
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        layer.applyLod(5)
        val coarse = layer.shapeOf(r)

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(a)))
        delay(50)

        assertSame(coarse, layer.shapeOf(r))
    }

    @Test
    fun onlyShapesInViewAreBroughtToTheNewLevelAndTheOthersAreLeftAlone() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val (europe, america) = listOf("EU", "AM").map(::EntityId)
        repository.add(roundPolygon(europe, centerLat = 50.0, centerLon = 10.0, radius = 10.0))
        repository.add(roundPolygon(america, centerLat = 40.0, centerLon = -100.0, radius = 10.0))
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        val americaBefore = layer.shapeOf(america)

        layer.applyLod(6, view = around(50.0, 10.0)) // the camera is over Europe

        assertTrue(layer.vertexCountOf(europe) < 200, "what is seen is simplified")
        assertSame(americaBefore, layer.shapeOf(america), "what is not seen is not built again")
        assertEquals(200, layer.vertexCountOf(america))

        layer.applyLod(6, view = around(40.0, -100.0)) // the camera moves over America

        assertTrue(layer.vertexCountOf(america) < 200, "it is brought to the level once it is seen")
    }

    @Test
    fun aLevelChangeThatIsTooBigForOneFrameIsFinishedOnTheNextCall() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val ids = listOf("A", "B", "C").map(::EntityId)
        ids.forEachIndexed { i, id -> repository.add(roundPolygon(id, centerLat = 0.0, centerLon = i * 40.0 - 40.0, radius = 10.0, vertexCount = 10_000)) }
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(100)

        layer.applyLod(6)
        assertTrue(layer.hasPendingLod, "30,000 vertices are more than one call builds")
        val doneAfterFirst = ids.count { layer.vertexCountOf(it) < 10_000 }
        assertTrue(doneAfterFirst in 1..2, "some, not all: $doneAfterFirst")

        layer.applyLod(6)
        assertFalse(layer.hasPendingLod)
        assertTrue(ids.all { layer.vertexCountOf(it) < 10_000 })
    }

    @Test
    fun aLayerThatBuildsOnTheFirstFrameBuildsNothingBeforeItAndThenBuildsAtTheLevelOfThatFrame() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val id = EntityId("R")
        repository.add(roundPolygon(id))
        val layer = DrawLayer(repository, stateMachine, backgroundScope, buildOnFirstFrame = true)
        delay(50)
        assertEquals(0, layer.count, "before the first frame the camera is not known: nothing is built")

        layer.onFrame(level = 15, handleLevel = LodLevel.NONE, view = null) // 330 km: a round shape of 70 km steps is coarsened

        assertEquals(1, layer.count)
        assertTrue(layer.vertexCountOf(id) < 200, "built straight away at the frame's level, not whole first")
    }

    @Test
    fun aShapeAddedAfterALevelWasChosenIsBuiltAtThatLevelAtOnce() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        layer.applyLod(15)

        val id = EntityId("NEW")
        repository.add(roundPolygon(id))
        delay(50)

        assertTrue(layer.vertexCountOf(id) < 200)
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
