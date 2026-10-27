package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The whole flow of giving a polygon another ring, through the state machine with its history. */
class RingsStateMachineTest {
    private val id = EntityId("P")
    private val history = HistoryManager()
    private val repository = EntityRepository().apply {
        add(
            DrawnPolygon(
                id, ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = listOf(
                    Vertex(VertexId("a"), GeoPosition(0.0, 0.0)), Vertex(VertexId("b"), GeoPosition(0.0, 10.0)),
                    Vertex(VertexId("c"), GeoPosition(10.0, 10.0)), Vertex(VertexId("d"), GeoPosition(10.0, 0.0)),
                ),
            )
        )
    }
    private val machine = DrawStateMachine(history, repository)

    private fun polygon() = repository.entities.value.single() as DrawnPolygon

    private fun edit() {
        machine.onEvent(DrawEvent.Click(GeoPosition(1.0, 1.0), id))
        machine.onEvent(DrawEvent.Click(GeoPosition(1.0, 1.0), id))
    }

    private fun drawRing(vararg points: GeoPosition) {
        machine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList(), targetEntityId = id)))
        points.forEach { machine.onEvent(DrawEvent.Click(it, null)) }
        machine.onEvent(DrawEvent.FinishDrawing(EntityId("unused"), List(points.size) { VertexId("h$it") }))
    }

    @Test
    fun aRingDrawnForAPolygonJoinsItAndTheEngineKeepsEditingIt() {
        edit()
        drawRing(GeoPosition(3.0, 3.0), GeoPosition(3.0, 6.0), GeoPosition(6.0, 6.0))

        assertEquals(DrawMode.Edit(id), machine.state.value.mode)
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("h0", "h1", "h2")), polygon().rings.map { r -> r.map { it.id.id } })
        assertEquals(1, repository.entities.value.size)
    }

    @Test
    fun theDraftOfARingCanBeUndoneStepByStepLikeAnyDraft() {
        edit()
        machine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList(), targetEntityId = id)))
        machine.onEvent(DrawEvent.Click(GeoPosition(3.0, 3.0), null))
        machine.onEvent(DrawEvent.Click(GeoPosition(3.0, 6.0), null))
        assertEquals(2, (machine.state.value.mode as DrawMode.Draw).pendingVertices.size)

        history.undo()
        val draft = machine.state.value.mode as DrawMode.Draw
        assertEquals(1, draft.pendingVertices.size)
        assertEquals(id, draft.targetEntityId, "it is still a ring for that polygon")
    }

    @Test
    fun finishingTheRingIsNotAnUndoStepButDeletingItAfterwardsIsAndUndoBringsItBack() {
        edit()
        drawRing(GeoPosition(3.0, 3.0), GeoPosition(3.0, 6.0), GeoPosition(6.0, 6.0))
        assertFalse(history.canUndo.value, "finishing ends the drawing session like any finished drawing")

        machine.onEvent(DrawEvent.Click(GeoPosition(3.0, 3.0), id, hitVertexId = VertexId("h1"))) // select a vertex of the new ring
        machine.onEvent(DrawEvent.DeleteSelectedRing)
        assertEquals(1, polygon().rings.size)
        assertTrue(history.canUndo.value)

        history.undo()
        assertEquals(listOf(listOf("a", "b", "c", "d"), listOf("h0", "h1", "h2")), polygon().rings.map { r -> r.map { it.id.id } })
        history.redo()
        assertEquals(1, polygon().rings.size)
    }

    @Test
    fun dragAVertexOfTheNewRingAsOneUndoStep() {
        edit()
        drawRing(GeoPosition(3.0, 3.0), GeoPosition(3.0, 6.0), GeoPosition(6.0, 6.0))

        machine.onEvent(DrawEvent.DragStart(GeoPosition(3.0, 3.0), VertexId("h0"), id))
        machine.onEvent(DrawEvent.DragMove(GeoPosition(4.0, 4.0)))
        machine.onEvent(DrawEvent.DragEnd(GeoPosition(4.0, 4.0)))
        assertEquals(GeoPosition(4.0, 4.0), polygon().rings[1][0].position)
        assertEquals(GeoPosition(0.0, 0.0), polygon().rings[0][0].position)

        history.undo()
        assertEquals(GeoPosition(3.0, 3.0), polygon().rings[1][0].position)
    }
}
