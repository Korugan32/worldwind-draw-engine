package com.tcsappdev.worldwinddraw.adapter.input

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
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
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
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
    fun draggingAHandleWithHiddenNeighboursCarriesThemAlongAsOneUndoableStep() {
        val entityId = EntityId("A")
        // Five vertices in a line; v0 and v4 are the handled ones, v1..v3 are hidden between them, and v2 has a handle too.
        val repository = EntityRepository().apply {
            add(
                DrawnPolygon(
                    id = entityId,
                    style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                    vertices = (0 until 5).map { Vertex(VertexId("v$it"), GeoPosition(0.0, it.toDouble())) },
                )
            )
        }
        val historyManager = HistoryManager()
        val stateMachine = DrawStateMachine(historyManager, repository)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))

        val bridge = PickInputBridge(stateMachine)
        val handle = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.VERTEX_ID_KEY, VertexId("v2"))
            putUserProperty(DrawLayer.SPAN_PREV_KEY, VertexId("v0"))
            putUserProperty(DrawLayer.SPAN_NEXT_KEY, VertexId("v4"))
        }
        fun latitudes() = (repository.entities.value.first { it.id == entityId } as DrawnPolygon).vertices.map { it.position.latitude }

        bridge.onObjectMoved(handle, Position.fromDegrees(0.0, 2.0, 0.0), Position.fromDegrees(2.0, 2.0, 0.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 1.0, 0.0), latitudes(), "v2 dragged 2 degrees north; the hidden v1 and v3 follow by half")

        bridge.onObjectMovingFinished(handle, Position.fromDegrees(2.0, 2.0, 0.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 1.0, 0.0), latitudes(), "finishing does not move them again")
        historyManager.undo()
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0), latitudes(), "one undo puts the whole stretch back")
    }

    @Test
    fun tappingAVertexHandleSelectsThatVertexButTappingAStretchMiddlePlusDoesNot() {
        val entityId = EntityId("A")
        val repository = EntityRepository().apply {
            add(
                DrawnPolygon(
                    id = entityId,
                    style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                    vertices = (0 until 4).map { Vertex(VertexId("v$it"), GeoPosition(it.toDouble(), 0.0)) },
                )
            )
        }
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        val bridge = PickInputBridge(stateMachine)
        fun selected() = (stateMachine.state.value.mode as DrawMode.Edit).selectedVertexId

        val vertexHandle = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.VERTEX_ID_KEY, VertexId("v2"))
        }
        bridge.onObjectPicked(vertexHandle, Position.fromDegrees(2.0, 0.0, 0.0))
        assertEquals(VertexId("v2"), selected())

        // The "+" on the middle of a stretch carries a vertex id too, but it is not a handle on that vertex.
        val plus = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.VERTEX_ID_KEY, VertexId("v1"))
            putUserProperty(DrawLayer.STRETCH_MIDDLE_KEY, true)
        }
        bridge.onObjectPicked(plus, Position.fromDegrees(1.0, 0.0, 0.0))
        assertEquals(null, selected(), "tapping it clears the selection like any tap on the shape that is not a vertex")
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

        fun livePosition() =
            ((stateMachine.state.value.mode as DrawMode.Edit).activeDrag as DrawMode.Edit.ActiveDrag.NewVertex).position

        // WWK reports the handle's own position as `from` and the cursor as `to`. The cursor is
        // already 0.2 degrees away on the first move (grab offset + touch slop), but the new
        // vertex must start exactly at the handle — not leap to the finger.
        bridge.onObjectMoved(midpointHandle, Position.fromDegrees(0.5, 0.0, 0.0), Position.fromDegrees(0.5, 0.2, 0.0))
        assertEquals(GeoPosition(0.5, 0.0), livePosition())
        // Nothing committed yet during the live drag — the vertex doesn't exist until release.
        assertEquals(3, currentVertices().size)
        assertFalse(historyManager.canUndo.value)

        // From then on it follows the finger's movement, keeping the same distance from it.
        bridge.onObjectMoved(midpointHandle, Position.fromDegrees(0.5, 0.2, 0.0), Position.fromDegrees(0.5, 0.25, 0.0))
        assertEquals(0.05, livePosition().longitude, 1e-9)

        bridge.onObjectMovingFinished(midpointHandle, Position.fromDegrees(0.5, 0.3, 0.0))

        val afterInsert = currentVertices()
        assertEquals(4, afterInsert.size)
        // handle (0.0) + the finger's travel since the first move (0.3 - 0.2) = 0.1
        assertEquals(0.1, afterInsert[1].position.longitude, 1e-9) // spliced right after v1
        assertEquals(0.5, afterInsert[1].position.latitude, 1e-9)
        assertTrue(historyManager.canUndo.value)

        historyManager.undo()
        val afterUndo = currentVertices()
        assertEquals(3, afterUndo.size)
        assertEquals(listOf(VertexId("v1"), VertexId("v2"), VertexId("v3")), afterUndo.map { it.id })
    }

    @Test
    fun theGrabOffsetIsForgottenBetweenDrags() {
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
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), entityId))
        val bridge = PickInputBridge(stateMachine)
        val midpointHandle = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.EDGE_INDEX_KEY, 0)
        }
        fun vertexCount() = (repository.entities.value.first { it.id == entityId } as DrawnPolygon).vertices.size
        fun livePosition() =
            ((stateMachine.state.value.mode as DrawMode.Edit).activeDrag as DrawMode.Edit.ActiveDrag.NewVertex).position

        // First drag, with a big grab offset.
        bridge.onObjectMoved(midpointHandle, Position.fromDegrees(0.5, 0.0, 0.0), Position.fromDegrees(0.5, 0.4, 0.0))
        bridge.onObjectMovingFinished(midpointHandle, Position.fromDegrees(0.5, 0.4, 0.0))
        assertEquals(4, vertexCount())

        // Second drag on a (now shorter) edge: a different, tiny offset must not inherit the first.
        val secondHandle = Polygon().apply {
            putUserProperty(DrawLayer.ENTITY_ID_KEY, entityId)
            putUserProperty(DrawLayer.EDGE_INDEX_KEY, 1)
        }
        bridge.onObjectMoved(secondHandle, Position.fromDegrees(0.75, 0.0, 0.0), Position.fromDegrees(0.75, 0.01, 0.0))
        assertEquals(GeoPosition(0.75, 0.0), livePosition())
    }

    // -- Handles on a shape that is still being drawn (DrawMode.Draw)

    private fun drawingStateMachine(vararg points: GeoPosition): DrawStateMachine {
        val stateMachine = DrawStateMachine(HistoryManager(), EntityRepository())
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, points.toList())))
        return stateMachine
    }

    private fun DrawStateMachine.draft() = state.value.mode as DrawMode.Draw

    private fun draftPointHandle(index: Int) = Polygon().apply { putUserProperty(DrawLayer.DRAFT_POINT_INDEX_KEY, index) }
    private fun draftEdgeHandle(index: Int) = Polygon().apply { putUserProperty(DrawLayer.DRAFT_EDGE_INDEX_KEY, index) }

    @Test
    fun draftHandlesCanBeDraggedEvenThoughTheyCarryNoEntityId() {
        val bridge = PickInputBridge(drawingStateMachine())

        assertTrue(bridge.canMoveObjects(draftPointHandle(0)))
        assertTrue(bridge.canMoveObjects(draftEdgeHandle(0)))
    }

    @Test
    fun tappingADraftHandleDoesNotAddAPointUnderIt() {
        val stateMachine = drawingStateMachine(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0), GeoPosition(1.0, 1.0))
        val bridge = PickInputBridge(stateMachine)

        bridge.onObjectPicked(draftPointHandle(1), Position.fromDegrees(1.0, 0.0, 0.0))
        bridge.onObjectPicked(draftEdgeHandle(0), Position.fromDegrees(0.5, 0.0, 0.0))

        assertEquals(3, stateMachine.draft().pendingVertices.size)
        // ...while a tap on plain terrain still adds one, as before.
        bridge.onTerrainPicked(Position.fromDegrees(2.0, 2.0, 0.0))
        assertEquals(4, stateMachine.draft().pendingVertices.size)
    }

    @Test
    fun draggingADraftPointMovesItAndKeepsFollowingTheCursor() {
        val stateMachine = drawingStateMachine(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0), GeoPosition(1.0, 1.0))
        val bridge = PickInputBridge(stateMachine)
        val handle = draftPointHandle(1)

        bridge.onObjectMoved(handle, Position.fromDegrees(1.0, 0.0, 0.0), Position.fromDegrees(1.2, 0.1, 0.0))
        assertEquals(GeoPosition(1.2, 0.1), stateMachine.draft().pendingVertices[1])
        bridge.onObjectMoved(handle, Position.fromDegrees(1.2, 0.1, 0.0), Position.fromDegrees(1.5, 0.3, 0.0))
        bridge.onObjectMovingFinished(handle, Position.fromDegrees(1.6, 0.4, 0.0))

        assertEquals(listOf(GeoPosition(0.0, 0.0), GeoPosition(1.6, 0.4), GeoPosition(1.0, 1.0)), stateMachine.draft().pendingVertices)
        assertEquals(null, stateMachine.draft().activeDrag)
    }

    @Test
    fun draggingADraftMidpointInsertsAPointAndKeepsTheGrabOffset() {
        val stateMachine = drawingStateMachine(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0), GeoPosition(1.0, 1.0))
        val bridge = PickInputBridge(stateMachine)
        val handle = draftEdgeHandle(0)

        // Handle at (0.5, 0.0); the cursor is already 0.2 degrees away on the first move.
        bridge.onObjectMoved(handle, Position.fromDegrees(0.5, 0.0, 0.0), Position.fromDegrees(0.5, 0.2, 0.0))
        val live = (stateMachine.draft().activeDrag as DrawMode.Draw.ActiveDrag.InsertingPoint).position
        assertEquals(GeoPosition(0.5, 0.0), live, "starts exactly at the handle, no leap to the finger")
        assertEquals(3, stateMachine.draft().pendingVertices.size, "nothing inserted until the drop")

        bridge.onObjectMovingFinished(handle, Position.fromDegrees(0.5, 0.3, 0.0))

        val points = stateMachine.draft().pendingVertices
        assertEquals(4, points.size)
        assertEquals(0.1, points[1].longitude, 1e-9) // handle (0.0) + the finger's travel since the first move (0.3 - 0.2)
        assertEquals(0.5, points[1].latitude, 1e-9)
    }

    // -- Moving the marker being edited

    private class MarkerSetup(val bridge: PickInputBridge, val history: HistoryManager, val repository: EntityRepository)

    /** A marker "M" at (1, 1), already in edit mode. */
    private fun markerSetup(): MarkerSetup {
        val id = EntityId("M")
        val repository = EntityRepository().apply {
            add(DrawnMarker(id, ShapeStyle.SolidColor(ColorRgba(0, 0, 0)), GeoPosition(1.0, 1.0), size = 20f))
        }
        val history = HistoryManager()
        val stateMachine = DrawStateMachine(history, repository)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(1.0, 1.0), id))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(1.0, 1.0), id))
        return MarkerSetup(PickInputBridge(stateMachine), history, repository)
    }

    private fun markerMoveTarget() = Polygon().apply {
        putUserProperty(DrawLayer.ENTITY_ID_KEY, EntityId("M"))
        putUserProperty(DrawLayer.MARKER_MOVE_KEY, true)
    }

    private fun EntityRepository.markerPosition() = (entities.value.single() as DrawnMarker).position

    @Test
    fun theMarkerMoveTargetCanBeDragged() {
        assertTrue(markerSetup().bridge.canMoveObjects(markerMoveTarget()))
    }

    @Test
    fun draggingAMarkerGrabbedOffCentreDoesNotMakeItLeapToTheFingerAndIsOneUndoableStep() {
        val (bridge, history, repository) = markerSetup().let { Triple(it.bridge, it.history, it.repository) }
        val target = markerMoveTarget()

        // WWK reports the marker's own position as `from`; the cursor is already 0.2/0.3 degrees away on the first move.
        bridge.onObjectMoved(target, Position.fromDegrees(1.0, 1.0, 0.0), Position.fromDegrees(1.2, 1.3, 0.0))
        assertEquals(GeoPosition(1.0, 1.0), repository.markerPosition(), "no leap on the first move")

        bridge.onObjectMoved(target, Position.fromDegrees(1.2, 1.3, 0.0), Position.fromDegrees(1.5, 1.8, 0.0))
        assertEquals(1.3, repository.markerPosition().latitude, 1e-9) // 1.0 + (1.5 - 1.2)
        assertEquals(1.5, repository.markerPosition().longitude, 1e-9) // 1.0 + (1.8 - 1.3)

        bridge.onObjectMovingFinished(target, Position.fromDegrees(1.6, 2.0, 0.0))
        assertEquals(1.4, repository.markerPosition().latitude, 1e-9)
        assertEquals(1.7, repository.markerPosition().longitude, 1e-9)
        assertTrue(history.canUndo.value)

        history.undo()
        assertEquals(GeoPosition(1.0, 1.0), repository.markerPosition(), "one undo goes back to where it started")
        assertFalse(history.canUndo.value)
    }
}
