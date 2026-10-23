package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlin.test.Test
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun p(lat: Double, lon: Double) = GeoPosition(lat, lon)

class DrawStateMachineTest {
    @Test
    fun onEventUpdatesState() {
        val machine = DrawStateMachine(HistoryManager(), EntityRepository())
        machine.onEvent(DrawEvent.ModeChange(DrawMode.Pan))
        assertEquals(DrawMode.Pan, machine.state.value.mode)
    }

    private class Setup {
        val history = HistoryManager()
        val repository = EntityRepository()
        val machine = DrawStateMachine(history, repository)
        fun mode() = machine.state.value.mode
        fun draft() = mode() as DrawMode.Draw
        fun points() = draft().pendingVertices
        fun startPolygon() = machine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList())))
        fun click(position: GeoPosition) = machine.onEvent(DrawEvent.Click(position, null))
        fun finish(id: String = "new") =
            machine.onEvent(DrawEvent.FinishDrawing(EntityId(id), List(draft().pendingVertices.size) { VertexId("$id-$it") }))
        fun addPolygon(id: String) = repository.add(
            DrawnPolygon(
                id = EntityId(id),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = listOf(
                    Vertex(VertexId("$id-a"), p(0.0, 0.0)),
                    Vertex(VertexId("$id-b"), p(1.0, 0.0)),
                    Vertex(VertexId("$id-c"), p(1.0, 1.0)),
                ),
            )
        )
        fun vertexPositions(id: String) =
            (repository.entities.value.first { it.id == EntityId(id) } as DrawnPolygon).vertices.map { it.position }

        /** Select then Edit the entity — the two taps it takes. */
        fun startEditing(id: String) {
            machine.onEvent(DrawEvent.Click(p(0.0, 0.0), EntityId(id)))
            machine.onEvent(DrawEvent.Click(p(0.0, 0.0), EntityId(id)))
        }
        fun dragVertex(id: String, vertex: String, from: GeoPosition, to: GeoPosition) {
            machine.onEvent(DrawEvent.DragStart(from, VertexId("$id-$vertex"), EntityId(id)))
            machine.onEvent(DrawEvent.DragMove(to))
            machine.onEvent(DrawEvent.DragEnd(to))
        }
    }

    // -- Drawing session

    @Test
    fun undoWalksBackThroughTheDraftOnePointAtATimeAndRedoWalksForward() {
        val s = Setup()
        s.startPolygon()
        s.click(p(0.0, 0.0)); s.click(p(1.0, 0.0)); s.click(p(1.0, 1.0))

        s.history.undo()
        assertEquals(listOf(p(0.0, 0.0), p(1.0, 0.0)), s.points())
        s.history.undo()
        assertEquals(listOf(p(0.0, 0.0)), s.points())

        s.history.redo()
        s.history.redo()
        assertEquals(3, s.points().size)
        assertFalse(s.history.canRedo.value)
    }

    @Test
    fun startingToDrawIsNotAnUndoStep() {
        val s = Setup()
        s.startPolygon()
        assertFalse(s.history.canUndo.value, "nothing to undo before the first point")

        s.click(p(0.0, 0.0))
        s.history.undo()

        assertEquals(emptyList(), s.points())
        assertFalse(s.history.canUndo.value)
        assertTrue(s.mode() is DrawMode.Draw, "undo never leaves drawing mode")
    }

    @Test
    fun finishingKeepsTheShapeAndEndsTheSessionSoUndoCannotDeleteIt() {
        val s = Setup()
        s.startPolygon()
        s.click(p(0.0, 0.0)); s.click(p(1.0, 0.0)); s.click(p(1.0, 1.0))
        assertTrue(s.history.canUndo.value)

        s.finish()

        assertEquals(1, s.repository.entities.value.size)
        assertEquals(DrawMode.Select(EntityId("new")), s.mode())
        assertFalse(s.history.canUndo.value, "the drawing session is over")
        assertFalse(s.history.canRedo.value)
        s.history.undo() // pressing undo anyway must not touch the finished shape
        assertEquals(1, s.repository.entities.value.size)
    }

    @Test
    fun abandoningTheDraftEndsTheSessionToo() {
        val s = Setup()
        s.startPolygon()
        s.click(p(0.0, 0.0)); s.click(p(1.0, 0.0))

        s.machine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))

        assertEquals(DrawMode.Idle, s.mode())
        assertFalse(s.history.canUndo.value)
    }

    @Test
    fun aNewDrawingStartsWithAnEmptyHistory() {
        val s = Setup()
        s.startPolygon()
        s.click(p(0.0, 0.0)); s.click(p(1.0, 0.0)); s.click(p(1.0, 1.0))
        s.finish()

        s.startPolygon()

        assertFalse(s.history.canUndo.value)
        assertEquals(1, s.repository.entities.value.size, "the finished shape is untouched")
    }

    @Test
    fun aMovedDraftPointAndAnInsertedPointEachUndoInOneStep() {
        val s = Setup()
        s.startPolygon()
        s.click(p(0.0, 0.0)); s.click(p(1.0, 0.0)); s.click(p(1.0, 1.0))

        s.machine.onEvent(DrawEvent.DragStart(p(1.0, 0.0), null, null, hitDraftPointIndex = 1))
        s.machine.onEvent(DrawEvent.DragMove(p(1.5, 0.2)))
        s.machine.onEvent(DrawEvent.DragMove(p(2.0, 0.4)))
        s.machine.onEvent(DrawEvent.DragEnd(p(2.0, 0.5)))
        s.machine.onEvent(DrawEvent.DragStart(p(1.0, 0.25), null, null, hitDraftEdgeIndex = 0))
        s.machine.onEvent(DrawEvent.DragEnd(p(1.0, 0.3)))
        assertEquals(4, s.points().size)

        s.history.undo()
        assertEquals(3, s.points().size, "the insert is gone")
        assertEquals(p(2.0, 0.5), s.points()[1], "the move is still there")
        s.history.undo()
        assertEquals(p(1.0, 0.0), s.points()[1], "the whole drag collapsed to one step back to the origin")
    }

    @Test
    fun aNewActionAfterUndoDropsTheRedoHistory() {
        val s = Setup()
        s.startPolygon()
        s.click(p(0.0, 0.0)); s.click(p(1.0, 0.0))
        s.history.undo()
        assertTrue(s.history.canRedo.value)

        s.click(p(5.0, 5.0))

        assertFalse(s.history.canRedo.value)
        assertEquals(listOf(p(0.0, 0.0), p(5.0, 5.0)), s.points())
    }

    // -- Editing session

    @Test
    fun editsAreUndoableWhileEditingAndForgottenOnceEditingEnds() {
        val s = Setup()
        s.addPolygon("A")
        s.startEditing("A")
        assertFalse(s.history.canUndo.value, "entering edit mode is not a step")

        s.dragVertex("A", "b", p(1.0, 0.0), p(2.0, 0.0))
        assertEquals(p(2.0, 0.0), s.vertexPositions("A")[1])
        assertTrue(s.history.canUndo.value)
        s.history.undo()
        assertEquals(p(1.0, 0.0), s.vertexPositions("A")[1])
        s.history.redo()
        assertEquals(p(2.0, 0.0), s.vertexPositions("A")[1])

        // Leave edit mode (tap empty space): the session is over.
        s.machine.onEvent(DrawEvent.Click(p(9.0, 9.0), null))
        assertEquals(DrawMode.Idle, s.mode())
        assertFalse(s.history.canUndo.value)
        assertFalse(s.history.canRedo.value)
        assertEquals(p(2.0, 0.0), s.vertexPositions("A")[1], "the edit itself stays")
    }

    @Test
    fun editingADifferentEntityStartsAFreshHistory() {
        val s = Setup()
        s.addPolygon("A"); s.addPolygon("B")
        s.startEditing("A")
        s.dragVertex("A", "b", p(1.0, 0.0), p(2.0, 0.0))
        assertTrue(s.history.canUndo.value)

        s.machine.onEvent(DrawEvent.Click(p(0.0, 0.0), EntityId("B"))) // Edit(A) -> Select(B)
        s.machine.onEvent(DrawEvent.Click(p(0.0, 0.0), EntityId("B"))) // -> Edit(B)

        assertFalse(s.history.canUndo.value, "A's edits are no longer undoable from inside B's session")
    }

    // -- Deleting

    @Test
    fun deletingASelectedShapeIsUndoableAndRedoable() {
        val s = Setup()
        s.addPolygon("A")
        s.machine.onEvent(DrawEvent.Click(p(0.0, 0.0), EntityId("A"))) // Select(A)
        assertFalse(s.history.canUndo.value)

        s.machine.onEvent(DrawEvent.DeleteSelected)

        assertTrue(s.repository.entities.value.isEmpty())
        assertEquals(DrawMode.Idle, s.mode())
        assertTrue(s.history.canUndo.value, "an accidental delete needs a way out")

        s.history.undo()
        assertEquals(1, s.repository.entities.value.size)
        s.history.redo()
        assertTrue(s.repository.entities.value.isEmpty())
    }

    @Test
    fun deletingWhileEditingLeavesTheEditSessionButStaysUndoableAsExactlyOneStep() {
        val s = Setup()
        s.addPolygon("A")
        s.startEditing("A")
        s.dragVertex("A", "b", p(1.0, 0.0), p(2.0, 0.0))
        assertTrue(s.history.canUndo.value)

        s.machine.onEvent(DrawEvent.DeleteSelected)

        assertEquals(DrawMode.Idle, s.mode())
        assertTrue(s.repository.entities.value.isEmpty())
        // The edit session ended, so the move is no longer separately undoable — but the delete is.
        s.history.undo()
        assertEquals(1, s.repository.entities.value.size)
        assertEquals(p(2.0, 0.0), s.vertexPositions("A")[1], "restored as it was when deleted, with the edit")
        assertFalse(s.history.canUndo.value, "only the delete was undoable, not the earlier move")
    }

    @Test
    fun startingANewSessionAfterADeleteForgetsItsUndo() {
        val s = Setup()
        s.addPolygon("A")
        s.machine.onEvent(DrawEvent.Click(p(0.0, 0.0), EntityId("A")))
        s.machine.onEvent(DrawEvent.DeleteSelected)
        assertTrue(s.history.canUndo.value)

        s.startPolygon()

        assertFalse(s.history.canUndo.value)
        assertTrue(s.repository.entities.value.isEmpty())
    }
}
