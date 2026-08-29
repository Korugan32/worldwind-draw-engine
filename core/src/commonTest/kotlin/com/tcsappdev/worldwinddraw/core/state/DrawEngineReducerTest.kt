package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawEngineReducerTest {
    private fun reduce(state: DrawEngineState, event: DrawEvent, repository: EntityRepository = EntityRepository()) =
        DrawEngineReducer.reduce(state, event, repository)

    private fun polygonAt(id: EntityId, vararg vertices: Pair<VertexId, GeoPosition>) =
        DrawnPolygon(
            id = id,
            style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
            vertices = vertices.map { (vertexId, position) -> Vertex(vertexId, position) },
        )

    private fun positionOf(repository: EntityRepository, entityId: EntityId, vertexId: VertexId) =
        (repository.entities.value.first { it.id == entityId } as DrawnPolygon).vertices.first { it.id == vertexId }.position

    @Test
    fun modeChangeAlwaysChangesMode() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val result = reduce(startState, DrawEvent.ModeChange(DrawMode.Pan))
        assertEquals(DrawMode.Pan, result.state.mode)
    }

    @Test
    fun clickInDrawModeAddsPendingVertex() {
        val startState = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Polygon, emptyList()))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = reduce(startState, event)
        assertEquals(1, (result.state.mode as DrawMode.Draw).pendingVertices.size)
    }

    @Test
    fun clickOnEntityWhileIdleSelectsIt() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("x"))
        val result = reduce(startState, event)
        assertEquals(DrawMode.Select(EntityId("x")), result.state.mode)
    }

    @Test
    fun clickOnEmptySpaceWhileIdleStaysIdle() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = reduce(startState, event)
        assertEquals(result.state.mode, DrawMode.Idle)
    }

    @Test
    fun clickingSameSelectedShapeEntersEdit() {
        // Clicking the shape that's already selected is treated as "I want to edit this
        // one now" — a deliberate second click, not just a re-selection.
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("A"))
        val result = reduce(startState, event)
        assertEquals(result.state.mode, DrawMode.Edit(EntityId("A")))
    }

    @Test
    fun clickingDifferentShapeJustSelectsIt() {
        // Clicking a *different* shape while one is already selected only switches the
        // selection — it does not jump straight into editing the new shape.
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("B"))
        val result = reduce(startState, event)
        assertEquals(result.state.mode, DrawMode.Select(EntityId("B")))
    }

    @Test
    fun clickingEmptySpaceDeselects() {
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = reduce(startState, event)
        assertEquals(result.state.mode, DrawMode.Idle)
    }

    @Test
    fun modeChangeProducesNoCommands() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val result = reduce(startState, DrawEvent.ModeChange(DrawMode.Pan))
        assertEquals(emptyList(), result.commands)
        assertEquals(emptyList(), result.previewCommands)
    }

    @Test
    fun clickOnADifferentEntityWhileEditingSwitchesToSelectingIt() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("B"))
        val result = reduce(startState, event)
        assertEquals(DrawMode.Select(EntityId("B")), result.state.mode)
    }

    @Test
    fun clickOnEmptySpaceWhileEditingExitsToIdle() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), null)
        val result = reduce(startState, event)
        assertEquals(DrawMode.Idle, result.state.mode)
    }

    @Test
    fun clickOnTheEditedEntitysOwnBodyIsANoOp() {
        // Inserting a vertex is done by dragging a midpoint handle now, not by tapping —
        // see the ActiveDrag.NewVertex tests below.
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val event = DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("A"))
        val result = reduce(startState, event)
        assertEquals(startState, result.state)
        assertEquals(emptyList(), result.commands)
    }

    // --- Dragging an existing vertex ---

    @Test
    fun dragStartOutsideEditModeIsIgnored() {
        val startState = DrawEngineState(mode = DrawMode.Select(EntityId("A")))
        val event = DrawEvent.DragStart(GeoPosition(0.0, 0.0), VertexId("v1"), EntityId("A"))
        val result = reduce(startState, event)
        assertEquals(startState, result.state)
    }

    @Test
    fun dragStartForADifferentEntityThanTheOneBeingEditedIsIgnored() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val event = DrawEvent.DragStart(GeoPosition(0.0, 0.0), VertexId("v1"), EntityId("B"))
        val result = reduce(startState, event)
        assertEquals(startState, result.state)
    }

    @Test
    fun dragStartWithAVertexHitArmsAnExistingVertexDrag() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val origin = GeoPosition(1.0, 2.0)
        val event = DrawEvent.DragStart(origin, VertexId("v1"), EntityId("A"))
        val result = reduce(startState, event)
        val expectedDrag = DrawMode.Edit.ActiveDrag.ExistingVertex(VertexId("v1"), origin)
        assertEquals(DrawMode.Edit(EntityId("A"), expectedDrag), result.state.mode)
    }

    @Test
    fun dragMoveWithNoArmedDragIsIgnored() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val result = reduce(startState, DrawEvent.DragMove(GeoPosition(5.0, 5.0)))
        assertEquals(emptyList(), result.previewCommands)
    }

    @Test
    fun dragMoveOnAnExistingVertexProducesAPreviewCommandThatActuallyMovesItButIsntRecorded() {
        val repository = EntityRepository()
        val entityId = EntityId("A")
        val vertexId = VertexId("v1")
        val origin = GeoPosition(0.0, 0.0)
        repository.add(polygonAt(entityId, vertexId to origin, VertexId("v2") to GeoPosition(1.0, 0.0), VertexId("v3") to GeoPosition(1.0, 1.0)))
        val drag = DrawMode.Edit.ActiveDrag.ExistingVertex(vertexId, origin)
        val startState = DrawEngineState(mode = DrawMode.Edit(entityId, drag))
        val newPosition = GeoPosition(9.0, 9.0)

        val result = reduce(startState, DrawEvent.DragMove(newPosition), repository)

        assertEquals(emptyList(), result.commands)
        assertEquals(1, result.previewCommands.size)
        result.previewCommands.forEach { it.execute() }
        assertEquals(newPosition, positionOf(repository, entityId, vertexId))
        // Mode stays armed for the same drag — DragMove alone doesn't end the gesture.
        assertEquals(DrawMode.Edit(entityId, drag), result.state.mode)
    }

    @Test
    fun dragEndOnAnExistingVertexProducesOneRecordedCommandAndDisarmsTheDrag() {
        val repository = EntityRepository()
        val entityId = EntityId("A")
        val vertexId = VertexId("v1")
        val origin = GeoPosition(0.0, 0.0)
        repository.add(polygonAt(entityId, vertexId to origin, VertexId("v2") to GeoPosition(1.0, 0.0), VertexId("v3") to GeoPosition(1.0, 1.0)))
        val startState = DrawEngineState(mode = DrawMode.Edit(entityId, DrawMode.Edit.ActiveDrag.ExistingVertex(vertexId, origin)))
        val finalPosition = GeoPosition(9.0, 9.0)

        val result = reduce(startState, DrawEvent.DragEnd(finalPosition), repository)

        assertEquals(1, result.commands.size)
        assertEquals(emptyList(), result.previewCommands)
        assertEquals(DrawMode.Edit(entityId), result.state.mode)

        // The single recorded command must be undoable back to the drag's origin.
        result.commands.forEach { it.execute() }
        assertEquals(finalPosition, positionOf(repository, entityId, vertexId))
        result.commands.forEach { it.undo() }
        assertEquals(origin, positionOf(repository, entityId, vertexId))
    }

    @Test
    fun dragEndWithNoArmedDragIsIgnored() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val result = reduce(startState, DrawEvent.DragEnd(GeoPosition(5.0, 5.0)))
        assertTrue(result.commands.isEmpty())
    }

    // --- Dragging a midpoint handle into a brand new vertex ---

    @Test
    fun dragStartWithAnEdgeHitArmsANewVertexDrag() {
        val startState = DrawEngineState(mode = DrawMode.Edit(EntityId("A")))
        val position = GeoPosition(3.0, 4.0)
        val event = DrawEvent.DragStart(position, hitVertexId = null, hitEntityId = EntityId("A"), hitEdgeIndex = 1)
        val result = reduce(startState, event)
        val expectedDrag = DrawMode.Edit.ActiveDrag.NewVertex(edgeIndex = 1, position = position)
        assertEquals(DrawMode.Edit(EntityId("A"), expectedDrag), result.state.mode)
    }

    @Test
    fun dragMoveOnANewVertexDragUpdatesTheLivePositionWithoutTouchingTheRepository() {
        val repository = EntityRepository()
        val entityId = EntityId("A")
        repository.add(polygonAt(entityId, VertexId("v1") to GeoPosition(0.0, 0.0), VertexId("v2") to GeoPosition(1.0, 0.0), VertexId("v3") to GeoPosition(1.0, 1.0)))
        val startDrag = DrawMode.Edit.ActiveDrag.NewVertex(edgeIndex = 0, position = GeoPosition(0.5, 0.0))
        val startState = DrawEngineState(mode = DrawMode.Edit(entityId, startDrag))
        val newPosition = GeoPosition(0.5, 0.3)

        val result = reduce(startState, DrawEvent.DragMove(newPosition), repository)

        // No command at all — nothing to execute yet, the vertex doesn't exist in the
        // repository during the drag.
        assertEquals(emptyList(), result.commands)
        assertEquals(emptyList(), result.previewCommands)
        val updatedDrag = (result.state.mode as DrawMode.Edit).activeDrag as DrawMode.Edit.ActiveDrag.NewVertex
        assertEquals(newPosition, updatedDrag.position)
        assertEquals(0, updatedDrag.edgeIndex)
        // The repository genuinely wasn't touched.
        assertEquals(3, (repository.entities.value.first { it.id == entityId } as DrawnPolygon).vertices.size)
    }

    @Test
    fun dragEndOnANewVertexDragInsertsExactlyOneVertexAsASingleUndoableCommand() {
        val repository = EntityRepository()
        val entityId = EntityId("A")
        repository.add(polygonAt(entityId, VertexId("v1") to GeoPosition(0.0, 0.0), VertexId("v2") to GeoPosition(1.0, 0.0), VertexId("v3") to GeoPosition(1.0, 1.0)))
        val drag = DrawMode.Edit.ActiveDrag.NewVertex(edgeIndex = 0, position = GeoPosition(0.5, 0.3))
        val startState = DrawEngineState(mode = DrawMode.Edit(entityId, drag))
        val newVertexId = VertexId("new")
        val finalPosition = GeoPosition(0.5, 0.4)

        val result = reduce(startState, DrawEvent.DragEnd(finalPosition, newVertexId), repository)

        assertEquals(1, result.commands.size)
        assertEquals(emptyList(), result.previewCommands)
        assertEquals(DrawMode.Edit(entityId), result.state.mode)

        result.commands.forEach { it.execute() }
        val afterInsert = repository.entities.value.first { it.id == entityId } as DrawnPolygon
        assertEquals(4, afterInsert.vertices.size)
        assertEquals(finalPosition, afterInsert.vertices.first { it.id == newVertexId }.position)
        assertEquals(listOf(VertexId("v1"), newVertexId, VertexId("v2"), VertexId("v3")), afterInsert.vertices.map { it.id })

        // Undo must remove the vertex entirely (not just move it) — the ring goes back to
        // exactly 3 vertices, not 4-with-a-relocated-one.
        result.commands.forEach { it.undo() }
        val afterUndo = repository.entities.value.first { it.id == entityId } as DrawnPolygon
        assertEquals(3, afterUndo.vertices.size)
        assertFalse(afterUndo.vertices.any { it.id == newVertexId })
    }

    @Test
    fun dragEndOnANewVertexDragWithNoIdSuppliedSkipsTheInsert() {
        // A hand-built event that leaves newVertexId at its null default — the reducer must
        // degrade gracefully (no crash, no insert) rather than assume an id.
        val repository = EntityRepository()
        val entityId = EntityId("A")
        repository.add(polygonAt(entityId, VertexId("v1") to GeoPosition(0.0, 0.0), VertexId("v2") to GeoPosition(1.0, 0.0), VertexId("v3") to GeoPosition(1.0, 1.0)))
        val drag = DrawMode.Edit.ActiveDrag.NewVertex(edgeIndex = 0, position = GeoPosition(0.5, 0.3))
        val startState = DrawEngineState(mode = DrawMode.Edit(entityId, drag))

        val result = reduce(startState, DrawEvent.DragEnd(GeoPosition(0.5, 0.4)), repository)

        assertEquals(emptyList(), result.commands)
        assertEquals(DrawMode.Edit(entityId), result.state.mode) // still disarms, just doesn't insert
    }
}
