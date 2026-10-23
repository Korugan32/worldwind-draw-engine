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
}
