package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
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

class DetailsReducerTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))
    private val ring = listOf(
        Vertex(VertexId("a"), GeoPosition(0.0, 0.0)), Vertex(VertexId("b"), GeoPosition(0.0, 1.0)), Vertex(VertexId("c"), GeoPosition(1.0, 1.0)),
    )

    private fun polygon(id: String, z: Int = 0) = DrawnPolygon(EntityId(id), style, ring.map { it.copy(id = VertexId(id + it.id.id)) }, zIndex = z)

    private fun repository(vararg entities: DrawableEntity) = EntityRepository().apply { entities.forEach(::add) }
    private fun EntityRepository.zOf(id: String) = entities.value.first { it.id.id == id }.zIndex

    private fun reduce(mode: DrawMode, event: DrawEvent, repository: EntityRepository) =
        DrawEngineReducer.reduce(DrawEngineState(mode = mode), event, repository)

    private fun select(id: String) = DrawMode.Select(EntityId(id))
    private fun edit(id: String) = DrawMode.Edit(EntityId(id))

    // -- label and description

    @Test
    fun theLabelOfTheSelectedOrEditedMarkerIsSetAsOneCommand() {
        val repository = repository(DrawnMarker(EntityId("M"), style, GeoPosition(0.0, 0.0)))
        for (mode in listOf(select("M"), edit("M"))) {
            val result = reduce(mode, DrawEvent.SetMarkerLabel(MarkerLabel("hello")), repository)
            result.commands.single().execute()
            assertEquals(MarkerLabel("hello"), (repository.entities.value.single() as DrawnMarker).label)
            repository.update((repository.entities.value.single() as DrawnMarker).copy(label = null))
        }
    }

    @Test
    fun theDescriptionOfTheSelectedOrEditedShapeIsSetAsOneCommand() {
        val repository = repository(polygon("P"))
        for (mode in listOf(select("P"), edit("P"))) {
            val result = reduce(mode, DrawEvent.SetDescription("a lake"), repository)
            result.commands.single().execute()
            assertEquals("a lake", repository.entities.value.single().description)
            repository.update(repository.entities.value.single().let { (it as DrawnPolygon).copy(description = null) })
        }
    }

    @Test
    fun thePropertiesOfTheSelectedOrEditedShapeAreSetAsOneCommand() {
        val repository = repository(polygon("P"))
        for (mode in listOf(select("P"), edit("P"))) {
            val result = reduce(mode, DrawEvent.SetProperties(mapOf("year" to "1914")), repository)
            result.commands.single().execute()
            assertEquals(mapOf("year" to "1914"), repository.entities.value.single().properties)
            repository.update(repository.entities.value.single().let { (it as DrawnPolygon).copy(properties = emptyMap()) })
        }
    }

    @Test
    fun detailsAreIgnoredWhenNothingIsSelected() {
        val repository = repository(polygon("P"))
        for (mode in listOf(DrawMode.Idle, DrawMode.Pan, DrawMode.Draw(DrawableEntityType.Polygon, emptyList()))) {
            assertTrue(reduce(mode, DrawEvent.SetMarkerLabel(MarkerLabel("x")), repository).commands.isEmpty(), "$mode")
            assertTrue(reduce(mode, DrawEvent.SetDescription("x"), repository).commands.isEmpty(), "$mode")
            assertTrue(reduce(mode, DrawEvent.SetProperties(mapOf("x" to "1")), repository).commands.isEmpty(), "$mode")
            assertTrue(reduce(mode, DrawEvent.SetZIndex(3), repository).commands.isEmpty(), "$mode")
            assertTrue(reduce(mode, DrawEvent.BringToFront, repository).commands.isEmpty(), "$mode")
            assertTrue(reduce(mode, DrawEvent.SendToBack, repository).commands.isEmpty(), "$mode")
        }
    }

    @Test
    fun aZIndexIsSetOnTheSelectedShape() {
        val repository = repository(polygon("P"))
        reduce(select("P"), DrawEvent.SetZIndex(5), repository).commands.single().execute()
        assertEquals(5, repository.zOf("P"))
    }

    // -- bring to front / send to back

    @Test
    fun bringingToTheFrontPutsTheShapeOneAboveTheHighest() {
        val repository = repository(polygon("A", z = 0), polygon("B", z = 5), polygon("C", z = 2))
        reduce(select("A"), DrawEvent.BringToFront, repository).commands.single().execute()
        assertEquals(6, repository.zOf("A"))
        assertEquals(5, repository.zOf("B"), "the others stay")
    }

    @Test
    fun sendingToTheBackPutsTheShapeOneBelowTheLowest() {
        val repository = repository(polygon("A", z = 0), polygon("B", z = 5), polygon("C", z = 2))
        reduce(edit("B"), DrawEvent.SendToBack, repository).commands.single().execute()
        assertEquals(-1, repository.zOf("B"))
    }

    @Test
    fun aShapeThatIsAlreadyOnTopOrAtTheBottomIsLeftAloneAndSoIsTheOnlyShape() {
        val repository = repository(polygon("A", z = 9), polygon("B", z = 5), polygon("C", z = 2))
        assertTrue(reduce(select("A"), DrawEvent.BringToFront, repository).commands.isEmpty(), "already above all")
        assertTrue(reduce(select("C"), DrawEvent.SendToBack, repository).commands.isEmpty(), "already below all")
        val alone = repository(polygon("A"))
        assertTrue(reduce(select("A"), DrawEvent.BringToFront, alone).commands.isEmpty(), "no other shape")
        assertTrue(reduce(select("A"), DrawEvent.SendToBack, alone).commands.isEmpty())
    }

    @Test
    fun sharingThePlaceOfAnotherShapeIsNotBeingAboveIt() {
        val front = repository(polygon("A", z = 3), polygon("B", z = 3))
        reduce(select("A"), DrawEvent.BringToFront, front).commands.single().execute()
        assertEquals(4, front.zOf("A"))
        val back = repository(polygon("A", z = 3), polygon("B", z = 3))
        reduce(select("B"), DrawEvent.SendToBack, back).commands.single().execute()
        assertEquals(2, back.zOf("B"))
    }

    @Test
    fun aMissingSelectedShapeIsIgnored() {
        val repository = repository(polygon("A"), polygon("B"))
        assertTrue(reduce(select("ghost"), DrawEvent.BringToFront, repository).commands.isEmpty())
    }

    // -- through the state machine and its history

    @Test
    fun bringingToTheFrontIsAnUndoStepEvenFromSelectMode() {
        val repository = repository(polygon("A"), polygon("B"))
        val history = HistoryManager()
        val machine = DrawStateMachine(history, repository)
        machine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("A")))
        assertEquals(DrawMode.Select(EntityId("A")), machine.state.value.mode)

        machine.onEvent(DrawEvent.BringToFront)
        assertEquals(1, repository.zOf("A"))
        assertTrue(history.canUndo.value)
        history.undo()
        assertEquals(0, repository.zOf("A"))
        history.redo()
        assertEquals(1, repository.zOf("A"))
        assertFalse(history.canRedo.value)
    }

    @Test
    fun aDescriptionAndALabelAreUndoStepsToo() {
        val repository = repository(DrawnMarker(EntityId("M"), style, GeoPosition(0.0, 0.0)))
        val history = HistoryManager()
        val machine = DrawStateMachine(history, repository)
        machine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("M")))

        machine.onEvent(DrawEvent.SetDescription("a pin"))
        machine.onEvent(DrawEvent.SetMarkerLabel(MarkerLabel("Pin")))
        val marker = { repository.entities.value.single() as DrawnMarker }
        assertEquals("a pin", marker().description)
        assertEquals("Pin", marker().label?.text)

        history.undo()
        assertEquals(null, marker().label)
        assertEquals("a pin", marker().description)
        history.undo()
        assertEquals(null, marker().description)
    }

    @Test
    fun propertiesAreAnUndoStepToo() {
        val repository = repository(DrawnMarker(EntityId("M"), style, GeoPosition(0.0, 0.0)))
        val history = HistoryManager()
        val machine = DrawStateMachine(history, repository)
        machine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), EntityId("M")))

        machine.onEvent(DrawEvent.SetProperties(mapOf("kind" to "city")))
        assertEquals(mapOf("kind" to "city"), repository.entities.value.single().properties)
        history.undo()
        assertEquals(emptyMap(), repository.entities.value.single().properties)
        history.redo()
        assertEquals(mapOf("kind" to "city"), repository.entities.value.single().properties)
    }
}
