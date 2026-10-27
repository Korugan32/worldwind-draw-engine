package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.commands.AddRingCommand
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingsReducerTest {
    private val id = EntityId("P")
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))

    private fun ring(prefix: String, count: Int) = (0 until count).map { Vertex(VertexId("$prefix$it"), GeoPosition(it.toDouble(), prefix.length.toDouble())) }

    /** A ring of 5, a hole of 3 and another part of 4. */
    private fun repository() = EntityRepository().apply {
        add(DrawnPolygon(id, style, ring("o", 5), extraRings = listOf(ring("h", 3), ring("pp", 4))))
    }

    private fun EntityRepository.polygon() = entities.value.single() as DrawnPolygon
    private fun EntityRepository.ids() = polygon().rings.map { ring -> ring.map { it.id.id } }
    private fun edit(selected: String? = null, drag: DrawMode.Edit.ActiveDrag? = null) =
        DrawEngineState(mode = DrawMode.Edit(id, activeDrag = drag, selectedVertexId = selected?.let(::VertexId)))

    private fun reduce(state: DrawEngineState, event: DrawEvent, repository: EntityRepository) = DrawEngineReducer.reduce(state, event, repository)

    // -- dragging a midpoint handle of a ring

    @Test
    fun aMidpointHandleDragRemembersWhichRingItBelongsTo() {
        val repository = repository()
        val start = reduce(edit(), DrawEvent.DragStart(GeoPosition(1.0, 1.0), hitVertexId = null, hitEntityId = id, hitEdgeIndex = 1, hitRingIndex = 2), repository)
        val drag = (start.state.mode as DrawMode.Edit).activeDrag as DrawMode.Edit.ActiveDrag.NewVertex
        assertEquals(1, drag.edgeIndex)
        assertEquals(2, drag.ringIndex)
    }

    @Test
    fun withoutARingIndexTheHandleBelongsToTheFirstRingAsBefore() {
        val start = reduce(edit(), DrawEvent.DragStart(GeoPosition(1.0, 1.0), hitVertexId = null, hitEntityId = id, hitEdgeIndex = 1), repository())
        assertEquals(0, ((start.state.mode as DrawMode.Edit).activeDrag as DrawMode.Edit.ActiveDrag.NewVertex).ringIndex)
    }

    @Test
    fun endingTheDragInsertsTheNewVertexIntoThatRing() {
        val repository = repository()
        val state = edit(drag = DrawMode.Edit.ActiveDrag.NewVertex(edgeIndex = 0, position = GeoPosition(2.0, 2.0), ringIndex = 1))
        val end = reduce(state, DrawEvent.DragEnd(GeoPosition(2.0, 2.0), newVertexId = VertexId("new")), repository)

        end.commands.single().execute()
        assertEquals(listOf("h0", "new", "h1", "h2"), repository.ids()[1])
        assertEquals(5, repository.ids()[0].size)
    }

    // -- deleting a vertex: its own ring is what has to keep enough of them

    @Test
    fun aVertexOfAThreeVertexRingCannotBeDeletedEvenThoughAnotherRingHasMore() {
        val repository = repository()
        val result = reduce(edit(selected = "h1"), DrawEvent.DeleteSelectedVertex, repository)
        assertTrue(result.commands.isEmpty())
        assertEquals(VertexId("h1"), (result.state.mode as DrawMode.Edit).selectedVertexId, "and it stays selected")
    }

    @Test
    fun aVertexOfARingWithRoomToSpareIsDeletedFromThatRingOnly() {
        val repository = repository()
        val result = reduce(edit(selected = "pp2"), DrawEvent.DeleteSelectedVertex, repository)
        result.commands.single().execute()
        assertEquals(listOf("pp0", "pp1", "pp3"), repository.ids()[2])
        assertEquals(3, repository.ids()[1].size)
        assertEquals(null, (result.state.mode as DrawMode.Edit).selectedVertexId)
    }

    // -- deleting a whole ring

    @Test
    fun theRingOfTheSelectedVertexCanBeDeletedAsOneStep() {
        val repository = repository()
        val result = reduce(edit(selected = "h1"), DrawEvent.DeleteSelectedRing, repository)
        result.commands.single().execute()
        assertEquals(listOf(5, 4), repository.ids().map { it.size })
        assertEquals(null, (result.state.mode as DrawMode.Edit).selectedVertexId)
    }

    @Test
    fun deletingTheRingIsIgnoredWithNothingSelectedWhileDraggingForAnUnknownVertexAndForTheOnlyRing() {
        val repository = repository()
        assertTrue(reduce(edit(), DrawEvent.DeleteSelectedRing, repository).commands.isEmpty(), "nothing selected")
        val dragging = edit(selected = "h1", drag = DrawMode.Edit.ActiveDrag.ExistingVertex(VertexId("h1"), GeoPosition(0.0, 0.0)))
        assertTrue(reduce(dragging, DrawEvent.DeleteSelectedRing, repository).commands.isEmpty(), "while dragging")
        assertTrue(reduce(edit(selected = "ghost"), DrawEvent.DeleteSelectedRing, repository).commands.isEmpty(), "a vertex the polygon doesn't have")
        assertTrue(reduce(DrawEngineState(mode = DrawMode.Idle), DrawEvent.DeleteSelectedRing, repository).commands.isEmpty(), "not editing")

        val single = EntityRepository().apply { add(DrawnPolygon(id, style, ring("o", 4))) }
        assertTrue(reduce(edit(selected = "o1"), DrawEvent.DeleteSelectedRing, single).commands.isEmpty(), "the only ring")
    }

    // -- simplifying counts what each ring can give

    @Test
    fun simplifyingIsIgnoredWhenEveryRingIsAlreadyAtItsMinimum() {
        val minimal = EntityRepository().apply { add(DrawnPolygon(id, style, ring("o", 3), extraRings = listOf(ring("h", 3)))) }
        val result = reduce(edit(), DrawEvent.SimplifyShape(0.5f), minimal)
        assertTrue(result.commands.isEmpty(), "6 vertices in two rings of 3: nothing to remove, although 3 would be enough for one ring")
        val roomy = reduce(edit(), DrawEvent.SimplifyShape(0.5f), repository())
        assertEquals(1, roomy.commands.size)
    }

    // -- drawing a ring for a polygon that exists

    private fun ringDraft(target: EntityId? = id, count: Int = 3, type: DrawableEntityType = DrawableEntityType.Polygon) = DrawEngineState(
        mode = DrawMode.Draw(type, List(count) { GeoPosition(it.toDouble(), 1.0) }, targetEntityId = target),
    )

    private fun finish(state: DrawEngineState, repository: EntityRepository) =
        reduce(state, DrawEvent.FinishDrawing(EntityId("unused"), (state.mode as DrawMode.Draw).pendingVertices.indices.map { VertexId("n$it") }), repository)

    @Test
    fun finishingARingDraftAddsTheRingToThePolygonAndKeepsEditingIt() {
        val repository = repository()
        val result = finish(ringDraft(), repository)

        assertEquals(DrawMode.Edit(id), result.state.mode)
        val command = result.commands.single()
        assertTrue(command is AddRingCommand)
        command.execute()
        assertEquals(listOf("n0", "n1", "n2"), repository.ids()[3])
        assertEquals(1, repository.entities.value.size, "no new entity")
    }

    @Test
    fun aRingDraftThatCannotBeFinishedStaysOpen() {
        val repository = repository()
        val tooFew = ringDraft(count = 2)
        assertEquals(tooFew, finish(tooFew, repository).state, "fewer than 3 points")
        assertTrue(finish(tooFew, repository).commands.isEmpty())

        val missing = ringDraft(target = EntityId("missing"))
        assertEquals(missing, finish(missing, repository).state, "the polygon is gone")

        val line = EntityRepository().apply { add(DrawnPolyline(id, style, ring("l", 3))) }
        val notAPolygon = ringDraft()
        assertEquals(notAPolygon, finish(notAPolygon, line).state, "the target is not a polygon")

        val wrongType = ringDraft(type = DrawableEntityType.Polyline)
        assertEquals(wrongType, finish(wrongType, repository).state, "a ring is a polygon's")
    }

    @Test
    fun aDraftWithoutATargetStillMakesANewShapeAsBefore() {
        val repository = repository()
        val result = finish(ringDraft(target = null), repository)
        assertEquals(DrawMode.Select(EntityId("unused")), result.state.mode)
        result.commands.single().execute()
        assertEquals(2, repository.entities.value.size)
    }
}
