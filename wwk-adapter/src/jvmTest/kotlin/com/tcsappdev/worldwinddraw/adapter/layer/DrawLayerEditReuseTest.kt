package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.render.Renderable
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// jvmTest: the handles are Placemarks, which Android's plain host tests can't build (see StyleAttributesMapperTest).
class DrawLayerEditReuseTest {
    private val a = EntityId("A")
    private val b = EntityId("B")

    private fun polygon(id: EntityId, vertexCount: Int, lat: Double = 0.0) = DrawnPolygon(
        id = id,
        style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
        vertices = List(vertexCount) { i ->
            val angle = 2 * Math.PI * i / vertexCount
            Vertex(VertexId("${id.id}$i"), GeoPosition(lat + Math.sin(angle), Math.cos(angle)))
        },
    )

    private fun setup(scope: CoroutineScope, vertexCount: Int = 6): Triple<DrawLayer, DrawStateMachine, EntityRepository> {
        val repository = EntityRepository()
        repository.add(polygon(a, vertexCount))
        repository.add(polygon(b, vertexCount, lat = 10.0))
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        return Triple(DrawLayer(repository, stateMachine, scope), stateMachine, repository)
    }

    private fun DrawLayer.shapeOf(id: EntityId) = single { it is Polygon && it.getUserProperty<EntityId>(DrawLayer.ENTITY_ID_KEY) == id }
    private fun DrawLayer.handles(): List<Renderable> = filterIsInstance<Placemark>()

    @Test
    fun goingFromSelectToEditKeepsTheShapesAndAddsOnlyTheHandles() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(a)))
        delay(50)
        val shapeA = layer.shapeOf(a)
        val shapeB = layer.shapeOf(b)
        assertTrue(layer.handles().isEmpty())

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)

        assertSame(shapeA, layer.shapeOf(a))
        assertSame(shapeB, layer.shapeOf(b))
        // 6 vertices and 6 edges, two renderables each (the visible dot and the invisible target).
        assertEquals(24, layer.handles().size)
    }

    @Test
    fun movingOneVertexBuildsAgainOnlyThatShapeAndTheHandlesAroundTheVertex() = runTest {
        val (layer, stateMachine, repository) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        val shapeB = layer.shapeOf(b)
        val handlesBefore = layer.handles()
        assertEquals(24, handlesBefore.size)

        val original = repository.entities.value.first { it.id == a } as DrawnPolygon
        repository.update(original.copy(vertices = original.vertices.mapIndexed { i, v -> if (i == 2) v.copy(position = GeoPosition(3.0, 3.0)) else v }))
        delay(50)

        assertSame(shapeB, layer.shapeOf(b), "another shape is untouched")
        val handlesAfter = layer.handles()
        assertEquals(24, handlesAfter.size)
        // Vertex 2's own handle (2 renderables) and the midpoints of its two edges (2 x 2) are new; the other 18 are the very same objects.
        assertEquals(18, handlesAfter.count { after -> handlesBefore.any { it === after } })
    }

    @Test
    fun leavingEditModeRemovesTheHandlesAndKeepsTheShapes() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        val shapeA = layer.shapeOf(a)

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        delay(50)

        assertTrue(layer.handles().isEmpty())
        assertSame(shapeA, layer.shapeOf(a))
    }

    @Test
    fun theShapeBeingEditedIsDrawnWholeAtACoarseLevelWhileOthersStaySimplified() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope, vertexCount = 200)
        delay(50)
        layer.applyLod(8)
        assertTrue((layer.shapeOf(a) as Polygon).getBoundary(0).size < 200)
        assertTrue((layer.shapeOf(b) as Polygon).getBoundary(0).size < 200)

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)

        assertEquals(200, (layer.shapeOf(a) as Polygon).getBoundary(0).size, "an edit must see every vertex")
        assertTrue((layer.shapeOf(b) as Polygon).getBoundary(0).size < 200)
    }

    // -- Edit handles for a shape with many points

    /** The handles on the vertices that are handled (the "+" on a stretch's middle vertex is not one of them). */
    private fun DrawLayer.vertexHandles() = filterIsInstance<Placemark>().filter {
        it.hasUserProperty(DrawLayer.VERTEX_ID_KEY) && !it.hasUserProperty(DrawLayer.STRETCH_MIDDLE_KEY)
    }

    /** The "+" handles that sit on a hidden vertex in the middle of a stretch between two handled vertices. */
    private fun DrawLayer.middleHandles() = filterIsInstance<Placemark>().filter { it.hasUserProperty(DrawLayer.STRETCH_MIDDLE_KEY) }
    private fun DrawLayer.midpointHandles() = filterIsInstance<Placemark>().filter { it.hasUserProperty(DrawLayer.EDGE_INDEX_KEY) }

    @Test
    fun aShapeWithManyPointsGetsOnlyTheHandlesThatMatterAtACoarseHandleLevelAndAllOfThemUpClose() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope, vertexCount = 200)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        assertEquals(200, layer.vertexHandles().size, "close up: a handle on every vertex")

        layer.applyHandleLevel(12) // far away
        val few = layer.vertexHandles().size
        assertTrue(few in 3..60, "far away: just a few handles, got $few")
        assertEquals(few, layer.middleHandles().size + layer.midpointHandles().size, "one \"+\" per coarse edge of the ring")

        layer.applyHandleLevel(LodLevel.NONE)
        assertEquals(200, layer.vertexHandles().size)
    }

    @Test
    fun aHandleWithHiddenPointsNextToItNamesTheHandledOnesOnEitherSideAndOthersCarryNoSpan() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope, vertexCount = 200)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        layer.applyHandleLevel(12)

        val handles = layer.vertexHandles()
        assertTrue(handles.size >= 3)
        handles.forEachIndexed { k, handle ->
            val before = handles[(k + handles.size - 1) % handles.size].getUserProperty<VertexId>(DrawLayer.VERTEX_ID_KEY)
            val after = handles[(k + 1) % handles.size].getUserProperty<VertexId>(DrawLayer.VERTEX_ID_KEY)
            assertEquals(before, handle.getUserProperty<VertexId>(DrawLayer.SPAN_PREV_KEY))
            assertEquals(after, handle.getUserProperty<VertexId>(DrawLayer.SPAN_NEXT_KEY))
        }
    }

    @Test
    fun theHandleOnAStretchWithHiddenPointsSitsOnItsMiddleVertexAndMovesItInsteadOfAddingOne() = runTest {
        val (layer, stateMachine, repository) = setup(backgroundScope, vertexCount = 200)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        layer.applyHandleLevel(12)

        val vertices = (repository.entities.value.first { it.id == a } as DrawnPolygon).vertices
        fun indexOf(id: VertexId?) = vertices.indexOfFirst { it.id == id }
        val anchors = layer.vertexHandles().map { indexOf(it.getUserProperty<VertexId>(DrawLayer.VERTEX_ID_KEY)) }
        val middles = layer.middleHandles()
        assertEquals(anchors.size, middles.size)
        assertTrue(layer.midpointHandles().isEmpty(), "every stretch of this outline has hidden vertices: no edge handle that adds one")
        middles.forEach { handle ->
            val from = indexOf(handle.getUserProperty<VertexId>(DrawLayer.SPAN_PREV_KEY))
            val to = indexOf(handle.getUserProperty<VertexId>(DrawLayer.SPAN_NEXT_KEY))
            val middle = indexOf(handle.getUserProperty<VertexId>(DrawLayer.VERTEX_ID_KEY))
            val stretch = (to - from + 200) % 200
            val offset = (middle - from + 200) % 200
            assertTrue(from in anchors && to in anchors, "its neighbours are handled vertices")
            assertTrue(offset in 1 until stretch, "vertex $middle is hidden, strictly between $from and $to")
            assertEquals(null, handle.getUserProperty<Int>(DrawLayer.EDGE_INDEX_KEY), "dragging it adds no vertex")
        }
    }

    @Test
    fun aShapeWithFewPointsAlwaysGetsAHandleOnEveryPoint() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope, vertexCount = 6)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        layer.applyHandleLevel(14)
        assertEquals(6, layer.vertexHandles().size)
        assertTrue(layer.vertexHandles().none { it.hasUserProperty(DrawLayer.SPAN_PREV_KEY) || it.hasUserProperty(DrawLayer.SPAN_NEXT_KEY) })
        assertEquals(6, layer.midpointHandles().size, "next to each other: an ordinary edge, whose \"+\" adds a vertex")
        assertTrue(layer.middleHandles().isEmpty())
    }

    @Test
    fun theHandleLevelIsNotChangedUnderAFingerThatIsDraggingAHandle() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope, vertexCount = 200)
        layer.applyHandleLevel(12)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(a)))
        delay(50)
        val handled = layer.vertexHandles().size
        val grabbed = layer.vertexHandles().first().getUserProperty<VertexId>(DrawLayer.VERTEX_ID_KEY)!!
        stateMachine.onEvent(DrawEvent.DragStart(GeoPosition(0.0, 0.0), grabbed, a))
        delay(50)

        layer.applyHandleLevel(LodLevel.NONE) // the camera moved far enough to change it...

        assertEquals(handled, layer.vertexHandles().size, "...but the handles stay as they are until the drag ends")
    }
}
