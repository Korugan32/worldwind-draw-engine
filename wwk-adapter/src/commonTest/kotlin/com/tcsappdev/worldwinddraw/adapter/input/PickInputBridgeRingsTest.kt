package com.tcsappdev.worldwinddraw.adapter.input

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
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
import earth.worldwind.geom.Position
import earth.worldwind.shape.Polygon
import kotlin.test.Test
import kotlin.test.assertEquals

class PickInputBridgeRingsTest {
    private val id = EntityId("P")

    private fun ring(prefix: String, lat: Double, lon: Double, size: Double) = listOf(
        Vertex(VertexId("${prefix}0"), GeoPosition(lat, lon)),
        Vertex(VertexId("${prefix}1"), GeoPosition(lat, lon + size)),
        Vertex(VertexId("${prefix}2"), GeoPosition(lat + size, lon + size)),
        Vertex(VertexId("${prefix}3"), GeoPosition(lat + size, lon)),
    )

    private val repository = EntityRepository().apply {
        add(DrawnPolygon(id, ShapeStyle.SolidColor(ColorRgba(0, 0, 0)), ring("o", 0.0, 0.0, 10.0), extraRings = listOf(ring("h", 3.0, 3.0, 2.0))))
    }
    private val stateMachine = DrawStateMachine(HistoryManager(), repository).also {
        it.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), id))
        it.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), id))
    }
    private val bridge = PickInputBridge(stateMachine)

    private fun polygon() = repository.entities.value.single() as DrawnPolygon

    // A real handle is a Placemark; any tagged Renderable does for the lookup (see PickInputBridgeTest).
    private fun midpointHandle(edge: Int, ring: Int?) = Polygon().apply {
        putUserProperty(DrawLayer.ENTITY_ID_KEY, id)
        putUserProperty(DrawLayer.EDGE_INDEX_KEY, edge)
        if (ring != null) putUserProperty(DrawLayer.RING_INDEX_KEY, ring)
    }

    @Test
    fun draggingTheMidpointHandleOfAHoleEdgeAddsTheVertexToTheHole() {
        val handle = midpointHandle(edge = 0, ring = 1)

        bridge.onObjectMoved(handle, Position.fromDegrees(3.0, 4.0, 0.0), Position.fromDegrees(3.0, 4.0, 0.0))
        val drag = (stateMachine.state.value.mode as DrawMode.Edit).activeDrag as DrawMode.Edit.ActiveDrag.NewVertex
        assertEquals(1, drag.ringIndex)
        assertEquals(0, drag.edgeIndex)

        bridge.onObjectMovingFinished(handle, Position.fromDegrees(2.5, 4.0, 0.0))
        assertEquals(4, polygon().rings[0].size, "the outer ring is as it was")
        assertEquals(5, polygon().rings[1].size)
        assertEquals(2.5, polygon().rings[1][1].position.latitude, 1e-9)
    }

    @Test
    fun aMidpointHandleWithoutARingTagBelongsToTheFirstRing() {
        val handle = midpointHandle(edge = 1, ring = null)

        bridge.onObjectMoved(handle, Position.fromDegrees(5.0, 10.0, 0.0), Position.fromDegrees(5.0, 10.0, 0.0))
        bridge.onObjectMovingFinished(handle, Position.fromDegrees(5.0, 11.0, 0.0))

        assertEquals(5, polygon().rings[0].size)
        assertEquals(4, polygon().rings[1].size)
    }
}
