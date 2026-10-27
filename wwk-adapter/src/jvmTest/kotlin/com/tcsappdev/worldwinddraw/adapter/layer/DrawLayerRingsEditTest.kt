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
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.render.Renderable
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// jvmTest: the handles are Placemarks, which Android's plain host tests can't build (see StyleAttributesMapperTest).
class DrawLayerRingsEditTest {
    private val id = EntityId("P")

    private fun ring(prefix: String, count: Int, radius: Double, lat: Double = 0.0, lon: Double = 0.0) = List(count) { i ->
        val angle = 2 * Math.PI * i / count
        Vertex(VertexId("$prefix$i"), GeoPosition(lat + radius * Math.sin(angle), lon + radius * Math.cos(angle)))
    }

    /** An outer ring of 6, a hole of 4 and another part of 3. */
    private fun setup(scope: CoroutineScope): Triple<DrawLayer, DrawStateMachine, EntityRepository> {
        val repository = EntityRepository()
        repository.add(DrawnPolygon(id, ShapeStyle.SolidColor(ColorRgba(0, 0, 0)), ring("o", 6, 2.0), extraRings = listOf(ring("h", 4, 0.5), ring("p", 3, 0.5, lon = 10.0))))
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        return Triple(DrawLayer(repository, stateMachine, scope), stateMachine, repository)
    }

    private fun DrawLayer.handles(): List<Renderable> = filterIsInstance<Placemark>()
    private fun DrawLayer.vertexHandleIds() = handles().mapNotNull { it.getUserProperty<VertexId>(DrawLayer.VERTEX_ID_KEY) }.toSet()
    private fun DrawLayer.edgeHandles() = handles().filter { it.hasUserProperty(DrawLayer.EDGE_INDEX_KEY) }
    private fun DrawLayer.polygonBoundaries() =
        (single { it is Polygon && it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) } as Polygon).let { p -> (0 until p.boundaryCount).map { p.getBoundary(it).size } }

    @Test
    fun editingAPolygonGivesEveryRingItsVertexAndEdgeHandles() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
        delay(50)

        // 13 vertices and 13 edges in the three rings, two renderables each (the visible dot and the invisible target).
        assertEquals(52, layer.handles().size)
        val expected = (0 until 6).map { VertexId("o$it") } + (0 until 4).map { VertexId("h$it") } + (0 until 3).map { VertexId("p$it") }
        assertEquals(expected.toSet(), layer.vertexHandleIds())
    }

    @Test
    fun anEdgeHandleSaysWhichEdgeOfWhichRingItIsOn() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
        delay(50)

        val tags = layer.edgeHandles()
            .map { it.getUserProperty<Int>(DrawLayer.RING_INDEX_KEY) to it.getUserProperty<Int>(DrawLayer.EDGE_INDEX_KEY) }
            .toSet()
        val expected = (0 until 6).map { 0 to it } + (0 until 4).map { 1 to it } + (0 until 3).map { 2 to it }
        assertEquals(expected.toSet(), tags)
    }

    @Test
    fun dragAPlusHandleOfTheHoleSplicesTheLivePointIntoTheHoleOnly() = runTest {
        val (layer, stateMachine, _) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
        delay(50)
        val before = layer.polygonBoundaries()
        val edgeHandlesBefore = layer.edgeHandles().size

        stateMachine.onEvent(DrawEvent.DragStart(GeoPosition(0.0, 0.0), hitVertexId = null, hitEntityId = id, hitEdgeIndex = 1, hitRingIndex = 1))
        stateMachine.onEvent(DrawEvent.DragMove(GeoPosition(0.2, 0.2)))
        delay(50)
        val during = layer.polygonBoundaries()

        assertEquals(before[0], during[0], "the outer ring is unchanged")
        assertEquals(before[1] + 1, during[1], "the hole shows the point being dragged")
        assertEquals(before[2], during[2])
        // The "+" of the edge being dragged is the one WWK is holding: no second one for it (the same edge of another ring still has its own).
        assertEquals(edgeHandlesBefore - 1, layer.edgeHandles().size, "one of the 13 edges is being dragged")
    }

    @Test
    fun editingShowsAllRingsWholeWhateverTheLevel() = runTest {
        val repository = EntityRepository()
        repository.add(DrawnPolygon(id, ShapeStyle.SolidColor(ColorRgba(0, 0, 0)), ring("o", 200, 20.0), extraRings = listOf(ring("p", 200, 3.0, lon = 40.0))))
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        layer.applyLod(LodLevel.forTolerance(300_000.0, LodLevel.NONE))
        assertTrue(layer.polygonBoundaries().all { it < 100 }, "simplified")

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
        delay(50)
        assertEquals(listOf(200, 200), layer.polygonBoundaries(), "edited: every ring whole, as every handle sits on a real vertex")
    }
}
