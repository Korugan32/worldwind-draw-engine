package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException
import com.tcsappdev.worldwinddraw.core.overlay.OverlayEngine
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayReducerTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    private fun v(id: String, lat: Double, lon: Double) = Vertex(VertexId(id), GeoPosition(lat, lon))

    private fun polygon(id: String, x: Double) =
        DrawnPolygon(EntityId(id), style, listOf(v("$id-1", 0.0, x), v("$id-2", 0.0, x + 1), v("$id-3", 1.0, x + 1)))

    // An engine with the answers it is told to give, that also records what it was asked.
    private class FakeEngine(
        var union: (() -> VertexId) -> List<List<Vertex>> = { emptyList() },
        var difference: (() -> VertexId) -> List<List<Vertex>> = { emptyList() },
        var invalid: Boolean = false,
    ) : OverlayEngine {
        val asked = ArrayList<String>()
        override fun validate(polygon: DrawnPolygon): String? = null
        override fun union(a: DrawnPolygon, b: DrawnPolygon, newVertexId: () -> VertexId): List<List<Vertex>> {
            asked += "union ${a.id.id} ${b.id.id}"
            if (invalid) throw InvalidShapeException("bad")
            return union(newVertexId)
        }
        override fun difference(a: DrawnPolygon, b: DrawnPolygon, newVertexId: () -> VertexId): List<List<Vertex>> {
            asked += "difference ${a.id.id} ${b.id.id}"
            if (invalid) throw InvalidShapeException("bad")
            return difference(newVertexId)
        }
    }

    private val result = listOf(v("r-1", 0.0, 0.0), v("r-2", 0.0, 5.0), v("r-3", 5.0, 5.0))

    private fun repository(vararg entities: DrawableEntity) = EntityRepository().apply { entities.forEach(::add) }
    private fun select(id: String) = DrawMode.Select(EntityId(id))
    private fun edit(id: String, vertex: String? = null) = DrawMode.Edit(EntityId(id), selectedVertexId = vertex?.let { VertexId(it) })

    private fun reduce(mode: DrawMode, event: DrawEvent, repository: EntityRepository, engine: OverlayEngine?) =
        DrawEngineReducer.reduce(DrawEngineState(mode = mode), event, repository, engine)

    private fun merge(other: String = "B", prefix: String = "p") = DrawEvent.MergeWith(EntityId(other), prefix)
    private fun cut(other: String = "B", prefix: String = "p") = DrawEvent.CutBy(EntityId(other), prefix)

    // ---- merge ----

    @Test
    fun aMergeOfTheSelectedPolygonWithAnotherIsOneCommandAndTheOverlayIsAskedOnce() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { listOf(result) })
        for (mode in listOf(select("A"), edit("A"))) {
            val outcome = reduce(mode, merge(), repository, engine)
            outcome.commands.single().execute()
            assertEquals(listOf("A"), repository.entities.value.map { it.id.id })
            assertEquals(result, (repository.entities.value.single() as DrawnPolygon).vertices)
            repository.replaceAll(listOf(polygon("A", 0.0), polygon("B", 3.0)))
        }
        assertEquals(listOf("union A B", "union A B"), engine.asked)
    }

    @Test
    fun theReducerStaysPureNothingChangesUntilTheCommandRuns() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        reduce(select("A"), merge(), repository, FakeEngine(union = { listOf(result) }))
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun theIdsOfNewVerticesAreMadeFromThePrefix() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { newId -> listOf(listOf(v(newId().id, 0.0, 0.0), v(newId().id, 0.0, 1.0), v(newId().id, 1.0, 1.0))) })
        reduce(select("A"), merge(prefix = "m7"), repository, engine).commands.single().execute()
        assertEquals(listOf("m7-1", "m7-2", "m7-3"), (repository.entities.value.single() as DrawnPolygon).vertices.map { it.id.id })
    }

    // ---- cut ----

    @Test
    fun aCutTakesTheOtherPolygonOutOfTheSelectedOneAndLeavesTheOtherAlone() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(difference = { listOf(result) })
        val outcome = reduce(select("A"), cut(), repository, engine)
        outcome.commands.single().execute()
        assertEquals(listOf("A", "B"), repository.entities.value.map { it.id.id })
        assertEquals(result, (repository.entities.value[0] as DrawnPolygon).vertices)
        assertEquals(listOf("difference A B"), engine.asked)
        assertEquals(select("A"), outcome.state.mode)
    }

    @Test
    fun aCutThatLeavesNothingRemovesTheShapeAndLeavesTheSelection() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        for (mode in listOf(select("A"), edit("A", vertex = "A-1"))) {
            val outcome = reduce(mode, cut(), repository, FakeEngine(difference = { emptyList() }))
            assertEquals(DrawMode.Idle, outcome.state.mode)
            outcome.commands.single().execute()
            assertEquals(listOf("B"), repository.entities.value.map { it.id.id })
            repository.replaceAll(listOf(polygon("A", 0.0), polygon("B", 3.0)))
        }
    }

    @Test
    fun aMergeOrCutInEditModeForgetsTheSelectedVertexAndTheDragBecauseTheVerticesChanged() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val drag = DrawMode.Edit.ActiveDrag.ExistingVertex(VertexId("A-1"), GeoPosition(0.0, 0.0))
        val mode = DrawMode.Edit(EntityId("A"), selectedVertexId = VertexId("A-1"), activeDrag = drag)
        val engine = FakeEngine(union = { listOf(result) }, difference = { listOf(result) })
        for (event in listOf(merge(), cut())) {
            val after = reduce(mode, event, repository, engine).state.mode as DrawMode.Edit
            assertEquals(null, after.selectedVertexId)
            assertEquals(null, after.activeDrag)
            assertEquals(EntityId("A"), after.entityId)
        }
    }

    // ---- nothing happens ----

    @Test
    fun withoutAnOverlayEngineNothingHappens() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        for (event in listOf(merge(), cut())) {
            val outcome = reduce(select("A"), event, repository, null)
            assertTrue(outcome.commands.isEmpty())
            assertEquals(select("A"), outcome.state.mode)
        }
    }

    @Test
    fun inAnyModeWithNothingSelectedNothingHappensAndTheEngineIsNotAsked() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { listOf(result) }, difference = { listOf(result) })
        for (mode in listOf(DrawMode.Idle, DrawMode.Pan, DrawMode.Draw(DrawableEntityType.Polygon, emptyList()))) {
            assertTrue(reduce(mode, merge(), repository, engine).commands.isEmpty(), "$mode")
            assertTrue(reduce(mode, cut(), repository, engine).commands.isEmpty(), "$mode")
        }
        assertEquals(emptyList(), engine.asked)
    }

    @Test
    fun aMissingShapeTheSameShapeOrOneThatIsNotAPolygonMeansNothingHappens() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0), DrawnMarker(EntityId("M"), style, GeoPosition(1.0, 1.0)))
        val engine = FakeEngine(union = { listOf(result) }, difference = { listOf(result) })
        assertTrue(reduce(select("A"), merge("nope"), repository, engine).commands.isEmpty())
        assertTrue(reduce(select("A"), merge("A"), repository, engine).commands.isEmpty())
        assertTrue(reduce(select("A"), cut("M"), repository, engine).commands.isEmpty())
        assertTrue(reduce(select("M"), merge("B"), repository, engine).commands.isEmpty())
        assertTrue(reduce(select("nope"), merge("B"), repository, engine).commands.isEmpty())
        assertEquals(emptyList(), engine.asked)
    }

    @Test
    fun aShapeTheEngineRefusesMeansNothingHappensAndTheStateIsAsItWas() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val mode = edit("A", vertex = "A-1")
        val engine = FakeEngine(invalid = true)
        for (event in listOf(merge(), cut())) {
            val outcome = reduce(mode, event, repository, engine)
            assertTrue(outcome.commands.isEmpty())
            assertEquals(mode, outcome.state.mode)
        }
    }

    // ---- through the state machine: history ----

    private fun machine(repository: EntityRepository, history: HistoryManager, engine: OverlayEngine?) = DrawStateMachine(history, repository, engine)

    @Test
    fun aMergeIsOneUndoStepFromSelectMode() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        val history = HistoryManager()
        val machine = machine(repository, history, FakeEngine(union = { listOf(result) }))
        machine.onEvent(DrawEvent.ModeChange(select("A")))

        machine.onEvent(merge())
        assertEquals(listOf("A"), repository.entities.value.map { it.id.id })
        assertTrue(history.canUndo.value)

        history.undo()
        assertEquals(before, repository.entities.value)
        history.redo()
        assertEquals(listOf("A"), repository.entities.value.map { it.id.id })
        assertEquals(select("A"), machine.state.value.mode)
    }

    @Test
    fun aMergeInEditModeIsAnUndoStepOfTheEditSession() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        val history = HistoryManager()
        val machine = machine(repository, history, FakeEngine(union = { listOf(result) }))
        machine.onEvent(DrawEvent.ModeChange(edit("A")))

        machine.onEvent(merge())
        history.undo()

        assertEquals(before, repository.entities.value)
        assertTrue(machine.state.value.mode is DrawMode.Edit)
    }

    @Test
    fun aCutThatTakesAllOfAShapeBeingEditedLeavesTheSessionButCanStillBeUndone() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        val history = HistoryManager()
        val machine = machine(repository, history, FakeEngine(difference = { emptyList() }))
        machine.onEvent(DrawEvent.ModeChange(edit("A")))

        machine.onEvent(cut())
        assertEquals(DrawMode.Idle, machine.state.value.mode)
        assertEquals(listOf("B"), repository.entities.value.map { it.id.id })
        assertTrue(history.canUndo.value)

        history.undo()
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun withoutAnEngineTheStateMachineLeavesMergeAndCutAlone() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        val history = HistoryManager()
        val machine = machine(repository, history, null)
        machine.onEvent(DrawEvent.ModeChange(select("A")))
        machine.onEvent(merge())
        machine.onEvent(cut())
        assertEquals(before, repository.entities.value)
        assertFalse(history.canUndo.value)
    }

    // ---- waiting for the second shape (DrawMode.Combine) ----

    private fun combine(id: String = "A", kind: CombineKind = CombineKind.Merge, prefix: String = "p") = DrawMode.Combine(EntityId(id), kind, prefix)
    private fun click(hit: String?) = DrawEvent.Click(GeoPosition(0.0, 0.0), hit?.let { EntityId(it) })

    @Test
    fun theModeCanBeEnteredFromASelectedOrEditedPolygonWhenThereIsAnEngine() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine()
        for (from in listOf(select("A"), edit("A"))) {
            assertEquals(combine(), reduce(from, DrawEvent.ModeChange(combine()), repository, engine).state.mode)
        }
    }

    @Test
    fun theModeCannotBeEnteredWithoutAnEngineOrForAShapeThatIsNotAPolygonOrMissing() {
        val repository = repository(polygon("A", 0.0), DrawnMarker(EntityId("M"), style, GeoPosition(1.0, 1.0)))
        assertEquals(select("A"), reduce(select("A"), DrawEvent.ModeChange(combine()), repository, null).state.mode)
        assertEquals(select("M"), reduce(select("M"), DrawEvent.ModeChange(combine("M")), repository, FakeEngine()).state.mode)
        assertEquals(DrawMode.Idle, reduce(DrawMode.Idle, DrawEvent.ModeChange(combine("nope")), repository, FakeEngine()).state.mode)
    }

    @Test
    fun aTapOnAnotherPolygonMergesItAndGoesBackToSelectingTheFirst() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { listOf(result) })
        val outcome = reduce(combine(prefix = "mm"), click("B"), repository, engine)

        outcome.commands.single().execute()
        assertEquals(select("A"), outcome.state.mode)
        assertEquals(listOf("A"), repository.entities.value.map { it.id.id })
        assertEquals(listOf("union A B"), engine.asked)
    }

    @Test
    fun theIdPrefixOfTheModeIsUsedForTheNewVertices() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { newId -> listOf(listOf(v(newId().id, 0.0, 0.0), v(newId().id, 0.0, 1.0), v(newId().id, 1.0, 1.0))) })
        reduce(combine(prefix = "k9"), click("B"), repository, engine).commands.single().execute()
        assertEquals(listOf("k9-1", "k9-2", "k9-3"), (repository.entities.value.single() as DrawnPolygon).vertices.map { it.id.id })
    }

    @Test
    fun aTapOnAnotherPolygonInCutModeCutsItOutAndLeavesIt() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(difference = { listOf(result) })
        val outcome = reduce(combine(kind = CombineKind.Cut), click("B"), repository, engine)
        outcome.commands.single().execute()
        assertEquals(listOf("A", "B"), repository.entities.value.map { it.id.id })
        assertEquals(listOf("difference A B"), engine.asked)
        assertEquals(select("A"), outcome.state.mode)
    }

    @Test
    fun aCutThatTakesAllOfTheFirstShapeLeavesTheSelection() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val outcome = reduce(combine(kind = CombineKind.Cut), click("B"), repository, FakeEngine(difference = { emptyList() }))
        assertEquals(DrawMode.Idle, outcome.state.mode)
        outcome.commands.single().execute()
        assertEquals(listOf("B"), repository.entities.value.map { it.id.id })
    }

    @Test
    fun aTapOnNothingOrOnTheFirstShapeCancelsAndGoesBackToSelectingIt() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { listOf(result) })
        for (hit in listOf(null, "A")) {
            val outcome = reduce(combine(), click(hit), repository, engine)
            assertTrue(outcome.commands.isEmpty(), "$hit")
            assertEquals(select("A"), outcome.state.mode, "$hit")
        }
        assertEquals(emptyList(), engine.asked)
    }

    @Test
    fun aTapOnAShapeThatIsNotAPolygonOrNotThereIsIgnoredAndTheWaitingGoesOn() {
        val repository = repository(polygon("A", 0.0), DrawnMarker(EntityId("M"), style, GeoPosition(1.0, 1.0)))
        val engine = FakeEngine(union = { listOf(result) })
        for (hit in listOf("M", "ghost")) {
            val outcome = reduce(combine(), click(hit), repository, engine)
            assertTrue(outcome.commands.isEmpty(), hit)
            assertEquals(combine(), outcome.state.mode, hit)
        }
        assertEquals(emptyList(), engine.asked)
    }

    @Test
    fun aShapeTheEngineRefusesEndsTheWaitingWithoutAChange() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val outcome = reduce(combine(), click("B"), repository, FakeEngine(invalid = true))
        assertTrue(outcome.commands.isEmpty())
        assertEquals(select("A"), outcome.state.mode)
    }

    @Test
    fun otherEventsDoNothingWhileWaitingExceptChangingTheMode() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val engine = FakeEngine(union = { listOf(result) }, difference = { listOf(result) })
        for (event in listOf(DrawEvent.DeleteSelected, DrawEvent.SetDescription("x"), DrawEvent.BringToFront, merge(), cut(), DrawEvent.DeleteSelectedVertex)) {
            val outcome = reduce(combine(), event, repository, engine)
            assertTrue(outcome.commands.isEmpty(), "$event")
            assertEquals(combine(), outcome.state.mode, "$event")
        }
        assertEquals(select("A"), reduce(combine(), DrawEvent.ModeChange(select("A")), repository, engine).state.mode)
        assertEquals(DrawMode.Idle, reduce(combine(), DrawEvent.ModeChange(DrawMode.Idle), repository, engine).state.mode)
    }

    @Test
    fun theFirstShapeIsHighlightedAsSelectedWhileWaiting() {
        assertEquals(com.tcsappdev.worldwinddraw.core.style.StyleState.SELECTED, combine().styleStateFor(EntityId("A")))
        assertEquals(com.tcsappdev.worldwinddraw.core.style.StyleState.DEFAULT, combine().styleStateFor(EntityId("B")))
    }

    @Test
    fun theWholeWayThroughTheStateMachineIsOneUndoStep() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        val history = HistoryManager()
        val machine = machine(repository, history, FakeEngine(union = { listOf(result) }))
        machine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("A"))) // select it
        machine.onEvent(DrawEvent.ModeChange(combine()))
        assertEquals(combine(), machine.state.value.mode)
        assertFalse(history.canUndo.value)

        machine.onEvent(click("B"))
        assertEquals(select("A"), machine.state.value.mode)
        assertEquals(listOf("A"), repository.entities.value.map { it.id.id })
        assertTrue(history.canUndo.value)

        history.undo()
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun enteringTheModeFromEditEndsTheEditSessionAndTheMergeIsStillUndoable() {
        val repository = repository(polygon("A", 0.0), polygon("B", 3.0))
        val before = repository.entities.value
        val history = HistoryManager()
        val machine = machine(repository, history, FakeEngine(union = { listOf(result) }))
        machine.onEvent(DrawEvent.ModeChange(edit("A")))
        machine.onEvent(DrawEvent.ModeChange(combine()))
        machine.onEvent(click("B"))
        assertTrue(history.canUndo.value)
        history.undo()
        assertEquals(before, repository.entities.value)
    }
}
