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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickInputBridgeTest {
    private fun bridge() = PickInputBridge(DrawStateMachine(HistoryManager(), EntityRepository()))

    @Test
    fun canPickObjectsIsAlwaysTrue() {
        // Required for onObjectPicked to ever fire at all — see SelectDragCallback's KDoc.
        assertTrue(bridge().canPickObjects(userObject = "anything"))
    }

    @Test
    fun onObjectPickedResolvesTheEntityIdTaggedByDrawLayer() {
        val stateMachine = DrawStateMachine(HistoryManager(), EntityRepository())
        val bridge = PickInputBridge(stateMachine)
        val taggedRenderable = Polygon().apply { putUserProperty(DrawLayer.ENTITY_ID_KEY, EntityId("demo-polygon")) }

        bridge.onObjectPicked(taggedRenderable, Position.fromDegrees(1.0, 2.0, 0.0))

        // Idle + a non-null hitEntityId transitions to Select per DrawEngineReducer — this
        // only happens if the EntityId round-tripped through putUserProperty/getUserProperty
        // correctly.
        assertEquals(DrawMode.Select(EntityId("demo-polygon")), stateMachine.state.value.mode)
    }

    @Test
    fun onObjectPickedWithAnUntaggedRenderableTreatsItAsNoHit() {
        val stateMachine = DrawStateMachine(HistoryManager(), EntityRepository())
        val bridge = PickInputBridge(stateMachine)

        bridge.onObjectPicked(Polygon(), Position.fromDegrees(1.0, 2.0, 0.0))

        // No EntityId tag means hitEntityId is null, so Idle stays Idle (Select requires a hit).
        assertEquals(DrawMode.Idle, stateMachine.state.value.mode)
    }

    @Test
    fun onTerrainPickedDispatchesAClickWithNoHitEntity() {
        val stateMachine = DrawStateMachine(HistoryManager(), EntityRepository())
        val bridge = PickInputBridge(stateMachine)

        bridge.onTerrainPicked(Position.fromDegrees(1.0, 2.0, 0.0))

        assertEquals(DrawMode.Idle, stateMachine.state.value.mode)
    }

    @Test
    fun canMoveObjectsIsTrueForVertexAndMidpointHandlesOnly() {
        val bridge = bridge()
        // A real handle is a Placemark, but any tagged Renderable exercises the same
        // getUserProperty lookup — Placemark's default PlacemarkAttributes() construction
        // crashes under Android's plain host unit tests (see StyleAttributesMapperTest's
        // comment), so a Polygon stands in here instead.
        val vertexHandleStandIn = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, EntityId("A"))
            putUserProperty(DrawLayer.VERTEX_ID_KEY, VertexId("v1"))
        }
        val midpointHandleStandIn = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, EntityId("A"))
            putUserProperty(DrawLayer.EDGE_INDEX_KEY, 0)
        }
        val wholeShapeStandIn = Polygon().apply { putUserProperty(DrawLayer.ENTITY_ID_KEY, EntityId("A")) }

        assertTrue(bridge.canMoveObjects(vertexHandleStandIn))
        assertTrue(bridge.canMoveObjects(midpointHandleStandIn))
        assertFalse(bridge.canMoveObjects(wholeShapeStandIn))
        assertFalse(bridge.canMoveObjects("untagged"))
    }

    @Test
    fun draggingAVertexHandleMovesItLiveAndRecordsOneUndoableCommandOnRelease() {
        val entityId = EntityId("A")
        val vertexId = VertexId("v1")
        val repository = EntityRepository().apply {
            add(
                DrawnPolygon(
                    id = entityId,
                    style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                    vertices = listOf(
                        Vertex(vertexId, GeoPosition(0.0, 0.0)),
                        Vertex(VertexId("v2"), GeoPosition(1.0, 0.0)),
                        Vertex(VertexId("v3"), GeoPosition(1.0, 1.0)),
                    ),
                )
            )
        }
        val historyManager = HistoryManager()
        val stateMachine = DrawStateMachine(historyManager, repository)
        // Idle -> Select(A) -> Edit(A): the same entity clicked twice, per DrawEngineReducer.
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        assertEquals(DrawMode.Edit(entityId), stateMachine.state.value.mode)

        val bridge = PickInputBridge(stateMachine)
        val vertexHandle = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.VERTEX_ID_KEY, vertexId)
        }

        fun positionOf(id: VertexId) =
            (repository.entities.value.first { it.id == entityId } as DrawnPolygon).vertices.first { it.id == id }.position

        bridge.onObjectMoved(vertexHandle, Position.fromDegrees(0.0, 0.0, 0.0), Position.fromDegrees(5.0, 5.0, 0.0))
        assertEquals(GeoPosition(5.0, 5.0), positionOf(vertexId))
        assertFalse(historyManager.canUndo.value) // live preview only, not recorded yet

        bridge.onObjectMovingFinished(vertexHandle, Position.fromDegrees(9.0, 9.0, 0.0))
        assertEquals(GeoPosition(9.0, 9.0), positionOf(vertexId))
        assertTrue(historyManager.canUndo.value)

        historyManager.undo()
        assertEquals(GeoPosition(0.0, 0.0), positionOf(vertexId))
    }

    @Test
    fun draggingAMidpointHandleInsertsExactlyOneVertexAtTheFinalPositionOnRelease() {
        val entityId = EntityId("A")
        val repository = EntityRepository().apply {
            add(
                DrawnPolygon(
                    id = entityId,
                    style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                    vertices = listOf(
                        Vertex(VertexId("v1"), GeoPosition(0.0, 0.0)),
                        Vertex(VertexId("v2"), GeoPosition(1.0, 0.0)),
                        Vertex(VertexId("v3"), GeoPosition(1.0, 1.0)),
                    ),
                )
            )
        }
        val historyManager = HistoryManager()
        val stateMachine = DrawStateMachine(historyManager, repository)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        assertEquals(DrawMode.Edit(entityId), stateMachine.state.value.mode)

        val bridge = PickInputBridge(stateMachine)
        val midpointHandle = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.EDGE_INDEX_KEY, 0) // v1 -> v2 edge
        }

        fun currentVertices() = (repository.entities.value.first { it.id == entityId } as DrawnPolygon).vertices

        bridge.onObjectMoved(midpointHandle, Position.fromDegrees(0.5, 0.0, 0.0), Position.fromDegrees(0.5, 0.2, 0.0))
        // Nothing committed yet during the live drag — the vertex doesn't exist until release.
        assertEquals(3, currentVertices().size)
        assertFalse(historyManager.canUndo.value)

        bridge.onObjectMovingFinished(midpointHandle, Position.fromDegrees(0.5, 0.3, 0.0))

        val afterInsert = currentVertices()
        assertEquals(4, afterInsert.size)
        assertEquals(GeoPosition(0.5, 0.3), afterInsert[1].position) // spliced right after v1
        assertTrue(historyManager.canUndo.value)

        historyManager.undo()
        val afterUndo = currentVertices()
        assertEquals(3, afterUndo.size)
        assertEquals(listOf(VertexId("v1"), VertexId("v2"), VertexId("v3")), afterUndo.map { it.id })
    }
}
