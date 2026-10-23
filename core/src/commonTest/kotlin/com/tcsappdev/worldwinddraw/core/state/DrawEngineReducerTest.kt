package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
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

    // --- Finishing a draw ---

    @Test
    fun finishDrawingOutsideDrawModeIsANoOp() {
        val startState = DrawEngineState(mode = DrawMode.Idle)
        val result = reduce(startState, DrawEvent.FinishDrawing(EntityId("new"), listOf(VertexId("v1"))))
        assertEquals(startState, result.state)
        assertEquals(emptyList(), result.commands)
    }

    @Test
    fun finishDrawingWithMismatchedVertexIdCountIsANoOp() {
        val startState = DrawEngineState(
            mode = DrawMode.Draw(DrawableEntityType.Polygon, listOf(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0), GeoPosition(1.0, 1.0)))
        )
        // Only 2 ids for 3 pending vertices.
        val event = DrawEvent.FinishDrawing(EntityId("new"), listOf(VertexId("v1"), VertexId("v2")))
        val result = reduce(startState, event)
        assertEquals(startState, result.state)
        assertEquals(emptyList(), result.commands)
    }

    @Test
    fun finishDrawingAPolygonWithTooFewPointsStaysInDrawMode() {
        val startState = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Polygon, listOf(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0))))
        val event = DrawEvent.FinishDrawing(EntityId("new"), listOf(VertexId("v1"), VertexId("v2")))
        val result = reduce(startState, event)
        assertEquals(startState, result.state) // draft kept, not discarded
        assertEquals(emptyList(), result.commands)
    }

    @Test
    fun finishDrawingAPolygonWithEnoughPointsAddsItAndSelectsIt() {
        val repository = EntityRepository()
        val style = ShapeStyle.SolidColor(ColorRgba(10, 20, 30))
        val positions = listOf(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0), GeoPosition(1.0, 1.0))
        val startState = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Polygon, positions, style))
        val entityId = EntityId("new-polygon")
        val vertexIds = listOf(VertexId("v1"), VertexId("v2"), VertexId("v3"))

        val result = reduce(startState, DrawEvent.FinishDrawing(entityId, vertexIds), repository)

        assertEquals(1, result.commands.size)
        assertEquals(DrawMode.Select(entityId), result.state.mode)
        result.commands.forEach { it.execute() }
        val created = repository.entities.value.first { it.id == entityId } as DrawnPolygon
        assertEquals(style, created.style)
        assertEquals(vertexIds, created.vertices.map { it.id })
        assertEquals(positions, created.vertices.map { it.position })

        // Undo must remove the whole entity, not just some of its vertices.
        result.commands.forEach { it.undo() }
        assertTrue(repository.entities.value.none { it.id == entityId })
    }

    @Test
    fun finishDrawingAPolylineRequiresAtLeastTwoPoints() {
        val repository = EntityRepository()
        val tooFew = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Polyline, listOf(GeoPosition(0.0, 0.0))))
        val tooFewResult = reduce(tooFew, DrawEvent.FinishDrawing(EntityId("new"), listOf(VertexId("v1"))), repository)
        assertEquals(tooFew, tooFewResult.state)

        val enough = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Polyline, listOf(GeoPosition(0.0, 0.0), GeoPosition(1.0, 1.0))))
        val entityId = EntityId("new-polyline")
        val enoughResult = reduce(enough, DrawEvent.FinishDrawing(entityId, listOf(VertexId("v1"), VertexId("v2"))), repository)
        assertEquals(1, enoughResult.commands.size)
        enoughResult.commands.forEach { it.execute() }
        assertTrue(repository.entities.value.first { it.id == entityId } is DrawnPolyline)
    }

    @Test
    fun finishDrawingAMarkerUsesTheFirstPendingPointAsItsPosition() {
        val repository = EntityRepository()
        val position = GeoPosition(12.0, 34.0)
        val startState = DrawEngineState(mode = DrawMode.Draw(DrawableEntityType.Marker, listOf(position)))
        val entityId = EntityId("new-marker")

        val result = reduce(startState, DrawEvent.FinishDrawing(entityId, listOf(VertexId("ignored"))), repository)

        assertEquals(1, result.commands.size)
        result.commands.forEach { it.execute() }
        val created = repository.entities.value.first { it.id == entityId } as DrawnMarker
        assertEquals(position, created.position)
    }

    // -- Reshaping a draft (DrawMode.Draw): drag a point, or drag an edge's midpoint to add one.

    private fun p(lat: Double, lon: Double) = GeoPosition(lat, lon)

    private fun draft(type: DrawableEntityType = DrawableEntityType.Polygon, vararg points: GeoPosition, activeDrag: DrawMode.Draw.ActiveDrag? = null) =
        DrawEngineState(mode = DrawMode.Draw(type, points.toList(), activeDrag = activeDrag))

    private fun draftOf(state: DrawEngineState) = state.mode as DrawMode.Draw

    @Test
    fun draggingADraftPointMovesItLiveAndRecordsOneUndoStepOnDrop() {
        val repository = EntityRepository()
        var state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))

        var result = reduce(state, DrawEvent.DragStart(p(1.0, 0.0), hitVertexId = null, hitEntityId = null, hitDraftPointIndex = 1), repository)
        assertEquals(DrawMode.Draw.ActiveDrag.MovingPoint(1, origin = p(1.0, 0.0)), draftOf(result.state).activeDrag)
        state = result.state

        result = reduce(state, DrawEvent.DragMove(p(2.0, 0.5)), repository)
        assertEquals(p(2.0, 0.5), draftOf(result.state).pendingVertices[1], "follows the drag live")
        assertTrue(result.commands.isEmpty() && result.previewCommands.isEmpty(), "a draft is not an entity: no repository commands")
        assertEquals(null, result.modeChange, "a live drag step is not an undo step; only the drop is")
        state = result.state

        result = reduce(state, DrawEvent.DragEnd(p(3.0, 0.5)), repository)
        assertEquals(listOf(p(0.0, 0.0), p(3.0, 0.5), p(1.0, 1.0)), draftOf(result.state).pendingVertices)
        assertEquals(null, draftOf(result.state).activeDrag)
        assertTrue(result.commands.isEmpty())
        assertTrue(repository.entities.value.isEmpty())
        // ONE undo step for the whole gesture: back to where the point started, forward to where it was dropped.
        val change = result.modeChange!!
        assertEquals(listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0)), (change.before as DrawMode.Draw).pendingVertices)
        assertEquals(listOf(p(0.0, 0.0), p(3.0, 0.5), p(1.0, 1.0)), (change.after as DrawMode.Draw).pendingVertices)
    }

    @Test
    fun draggingADraftMidpointInsertsOneNewPointAfterItsEdgeOnlyWhenDropped() {
        var state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))

        state = reduce(state, DrawEvent.DragStart(p(0.5, 0.0), hitVertexId = null, hitEntityId = null, hitDraftEdgeIndex = 0)).state
        assertEquals(DrawMode.Draw.ActiveDrag.InsertingPoint(0, p(0.5, 0.0)), draftOf(state).activeDrag)

        state = reduce(state, DrawEvent.DragMove(p(0.6, -0.3))).state
        assertEquals(3, draftOf(state).pendingVertices.size, "nothing is inserted while dragging")
        assertEquals(p(0.6, -0.3), (draftOf(state).activeDrag as DrawMode.Draw.ActiveDrag.InsertingPoint).position)

        state = reduce(state, DrawEvent.DragEnd(p(0.7, -0.4))).state
        assertEquals(listOf(p(0.0, 0.0), p(0.7, -0.4), p(1.0, 0.0), p(1.0, 1.0)), draftOf(state).pendingVertices)
        assertEquals(null, draftOf(state).activeDrag)
    }

    @Test
    fun theClosingEdgeOfAPolygonDraftCanGrowAPointToo() {
        // 3 points = 3 edges; edge 2 is the wrap-around from the last point back to the first.
        var state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))
        state = reduce(state, DrawEvent.DragStart(p(0.5, 0.5), hitVertexId = null, hitEntityId = null, hitDraftEdgeIndex = 2)).state
        state = reduce(state, DrawEvent.DragEnd(p(0.4, 0.6))).state

        assertEquals(listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0), p(0.4, 0.6)), draftOf(state).pendingVertices)
    }

    @Test
    fun aDraftWithTooFewPointsToCloseHasNoClosingEdge() {
        val polygonOfTwo = DrawMode.Draw(DrawableEntityType.Polygon, listOf(p(0.0, 0.0), p(1.0, 0.0)))
        assertFalse(polygonOfTwo.isClosed)
        assertEquals(1, polygonOfTwo.edgeCount)
        assertEquals(3, DrawMode.Draw(DrawableEntityType.Polygon, listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))).edgeCount)
        assertEquals(2, DrawMode.Draw(DrawableEntityType.Polyline, listOf(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))).edgeCount)
        assertEquals(0, DrawMode.Draw(DrawableEntityType.Marker, listOf(p(0.0, 0.0), p(1.0, 0.0))).edgeCount)
        assertEquals(0, DrawMode.Draw(DrawableEntityType.Polygon, emptyList()).edgeCount)
    }

    @Test
    fun draftDragsWithAnOutOfRangeIndexOrNoTargetAreIgnored() {
        val state = draft(DrawableEntityType.Polyline, p(0.0, 0.0), p(1.0, 0.0))

        // A polyline of 2 points has 1 edge (index 0) and 2 points (indices 0..1).
        val badPoint = reduce(state, DrawEvent.DragStart(p(0.0, 0.0), null, null, hitDraftPointIndex = 2)).state
        val badEdge = reduce(state, DrawEvent.DragStart(p(0.0, 0.0), null, null, hitDraftEdgeIndex = 1)).state
        val nothing = reduce(state, DrawEvent.DragStart(p(0.0, 0.0), null, null)).state

        assertEquals(state, badPoint)
        assertEquals(state, badEdge)
        assertEquals(state, nothing)
    }

    @Test
    fun draftDragMoveAndEndWithNoDragStartedAreNoOps() {
        val state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))

        assertEquals(state, reduce(state, DrawEvent.DragMove(p(5.0, 5.0))).state)
        assertEquals(state, reduce(state, DrawEvent.DragEnd(p(5.0, 5.0))).state)
    }

    @Test
    fun aReshapedDraftIsWhatFinishDrawingCommits() {
        var state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))
        state = reduce(state, DrawEvent.DragStart(p(0.5, 0.0), null, null, hitDraftEdgeIndex = 0)).state
        state = reduce(state, DrawEvent.DragEnd(p(0.5, -0.5))).state
        val repository = EntityRepository()

        val ids = List(4) { VertexId("v$it") }
        val result = reduce(state, DrawEvent.FinishDrawing(EntityId("new"), ids), repository)
        result.commands.forEach { it.execute() }

        val polygon = repository.entities.value.single() as DrawnPolygon
        assertEquals(listOf(p(0.0, 0.0), p(0.5, -0.5), p(1.0, 0.0), p(1.0, 1.0)), polygon.vertices.map { it.position })
    }

    // -- Which steps are undo steps (UndoableModeChange)

    @Test
    fun placingADraftPointIsAnUndoStep() {
        val before = DrawMode.Draw(DrawableEntityType.Polygon, listOf(p(0.0, 0.0)))
        val result = reduce(DrawEngineState(mode = before), DrawEvent.Click(p(1.0, 1.0), null))

        val change = result.modeChange!!
        assertEquals(before, change.before)
        assertEquals(listOf(p(0.0, 0.0), p(1.0, 1.0)), (change.after as DrawMode.Draw).pendingVertices)
    }

    @Test
    fun startingAndStoppingDrawingAreDescribedAsModeChanges() {
        val draw = DrawMode.Draw(DrawableEntityType.Polygon, emptyList())

        val start = reduce(DrawEngineState(mode = DrawMode.Idle), DrawEvent.ModeChange(draw)).modeChange!!
        assertEquals(DrawMode.Idle, start.before)
        assertEquals(draw, start.after)

        val stop = reduce(DrawEngineState(mode = draw), DrawEvent.ModeChange(DrawMode.Idle)).modeChange!!
        assertEquals(draw, stop.before)
        assertEquals(DrawMode.Idle, stop.after)
    }

    @Test
    fun navigationBetweenSelectAndEditIsNotAnUndoStep() {
        assertEquals(null, reduce(DrawEngineState(mode = DrawMode.Idle), DrawEvent.ModeChange(DrawMode.Pan)).modeChange)
        assertEquals(null, reduce(DrawEngineState(mode = DrawMode.Idle), DrawEvent.Click(p(0.0, 0.0), EntityId("x"))).modeChange)
        assertEquals(null, reduce(DrawEngineState(mode = DrawMode.Select(EntityId("x"))), DrawEvent.Click(p(0.0, 0.0), EntityId("x"))).modeChange)
    }

    @Test
    fun aDropThatChangedNothingIsNotAnUndoStep() {
        var state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))
        state = reduce(state, DrawEvent.DragStart(p(1.0, 0.0), null, null, hitDraftPointIndex = 1)).state

        val result = reduce(state, DrawEvent.DragEnd(p(1.0, 0.0)))

        assertEquals(null, result.modeChange)
    }

    @Test
    fun finishingAddsTheEntityButIsNotReportedAsAnUndoStep() {
        val state = draft(DrawableEntityType.Polygon, p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0))

        val result = reduce(state, DrawEvent.FinishDrawing(EntityId("new"), List(3) { VertexId("v$it") }))

        assertEquals(1, result.commands.size, "the AddEntityCommand")
        // Finishing ends the drawing session (see DrawStateMachine); it must not leave a step that
        // undo could use to delete the whole shape.
        assertEquals(null, result.modeChange)
    }

    // -- Deleting the selected shape

    @Test
    fun deleteSelectedRemovesTheSelectedOrEditedShapeAndGoesIdle() {
        val id = EntityId("A")
        listOf(DrawMode.Select(id), DrawMode.Edit(id)).forEach { mode ->
            val repository = EntityRepository()
            repository.add(polygonAt(id, VertexId("a") to p(0.0, 0.0), VertexId("b") to p(1.0, 0.0), VertexId("c") to p(1.0, 1.0)))

            val result = reduce(DrawEngineState(mode = mode), DrawEvent.DeleteSelected, repository)

            assertEquals(DrawMode.Idle, result.state.mode, "mode $mode")
            assertEquals(1, result.commands.size)
            result.commands.single().execute()
            assertTrue(repository.entities.value.isEmpty())
            result.commands.single().undo()
            assertEquals(1, repository.entities.value.size, "undo brings it back")
        }
    }

    @Test
    fun deleteSelectedIsANoOpWhenNothingIsSelected() {
        listOf(DrawMode.Idle, DrawMode.Pan, DrawMode.Draw(DrawableEntityType.Polygon, listOf(p(0.0, 0.0)))).forEach { mode ->
            val state = DrawEngineState(mode = mode)

            val result = reduce(state, DrawEvent.DeleteSelected)

            assertEquals(state, result.state, "mode $mode")
            assertTrue(result.commands.isEmpty())
        }
    }
}
