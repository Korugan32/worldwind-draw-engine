package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.LabelPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailCommandsTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))
    private val ring = listOf(
        Vertex(VertexId("a"), GeoPosition(0.0, 0.0)), Vertex(VertexId("b"), GeoPosition(0.0, 1.0)), Vertex(VertexId("c"), GeoPosition(1.0, 1.0)),
    )
    private val polygon = DrawnPolygon(EntityId("P"), style, ring)
    private val polyline = DrawnPolyline(EntityId("L"), style, ring)
    private val marker = DrawnMarker(EntityId("M"), style, GeoPosition(1.0, 1.0), label = MarkerLabel("old"))

    private fun repository() = EntityRepository().apply { add(polygon); add(polyline); add(marker) }
    private fun EntityRepository.get(id: String): DrawableEntity = entities.value.first { it.id.id == id }

    // -- the label

    @Test
    fun settingALabelReplacesTheOldOneAndUndoPutsItBack() {
        val repository = repository()
        val label = MarkerLabel("new", ColorRgba(255, 0, 0, 128), size = 20, position = LabelPosition.Right)
        val command = SetMarkerLabelCommand(repository, marker.id, label)

        command.execute()
        assertEquals(label, (repository.get("M") as DrawnMarker).label)
        command.undo()
        assertEquals(MarkerLabel("old"), (repository.get("M") as DrawnMarker).label)
        command.execute() // redo
        assertEquals(label, (repository.get("M") as DrawnMarker).label)
    }

    @Test
    fun aNullLabelTakesTheLabelAwayAndUndoBringsItBack() {
        val repository = repository()
        val command = SetMarkerLabelCommand(repository, marker.id, null)
        command.execute()
        assertNull((repository.get("M") as DrawnMarker).label)
        command.undo()
        assertEquals(MarkerLabel("old"), (repository.get("M") as DrawnMarker).label)
    }

    @Test
    fun aLabelForAShapeThatIsNotAMarkerOrMissingIsIgnoredAndSoIsItsUndo() {
        val repository = repository()
        val before = repository.entities.value
        SetMarkerLabelCommand(repository, polygon.id, MarkerLabel("x")).also { it.execute(); it.undo() }
        SetMarkerLabelCommand(repository, EntityId("missing"), MarkerLabel("x")).also { it.execute(); it.undo() }
        assertEquals(before, repository.entities.value)
    }

    // -- the description

    @Test
    fun aDescriptionIsSetOnAnyKindAndUndoPutsTheOldOneBack() {
        val repository = repository()
        for (id in listOf("P", "L", "M")) {
            val command = SetDescriptionCommand(repository, EntityId(id), "about $id")
            command.execute()
            assertEquals("about $id", repository.get(id).description)
            command.undo()
            assertNull(repository.get(id).description)
        }
    }

    @Test
    fun undoingADescriptionPutsBackWhatItReplacedNotJustNothing() {
        val repository = repository()
        SetDescriptionCommand(repository, polygon.id, "first").execute()
        val second = SetDescriptionCommand(repository, polygon.id, "second")
        second.execute()
        second.undo()
        assertEquals("first", repository.get("P").description)
        second.execute() // redo
        assertEquals("second", repository.get("P").description)
        SetDescriptionCommand(repository, polygon.id, null).execute()
        assertNull(repository.get("P").description)
    }

    @Test
    fun aDescriptionForAMissingShapeIsIgnored() {
        val repository = repository()
        val before = repository.entities.value
        SetDescriptionCommand(repository, EntityId("missing"), "x").also { it.execute(); it.undo() }
        assertEquals(before, repository.entities.value)
    }

    // -- the properties

    @Test
    fun propertiesAreSetOnAnyKindAndUndoPutsTheOldOnesBack() {
        val repository = repository()
        for (id in listOf("P", "L", "M")) {
            val command = SetPropertiesCommand(repository, EntityId(id), mapOf("name" to id))
            command.execute()
            assertEquals(mapOf("name" to id), repository.get(id).properties)
            command.undo()
            assertEquals(emptyMap(), repository.get(id).properties)
        }
    }

    @Test
    fun settingPropertiesReplacesAllOfThemAndUndoBringsAllBack() {
        val repository = repository()
        SetPropertiesCommand(repository, polygon.id, mapOf("a" to "1", "b" to "2")).execute()
        val second = SetPropertiesCommand(repository, polygon.id, mapOf("c" to "3"))
        second.execute()
        assertEquals(mapOf("c" to "3"), repository.get("P").properties)
        second.undo()
        assertEquals(mapOf("a" to "1", "b" to "2"), repository.get("P").properties)
        second.execute() // redo
        assertEquals(mapOf("c" to "3"), repository.get("P").properties)
        SetPropertiesCommand(repository, polygon.id, emptyMap()).execute()
        assertEquals(emptyMap(), repository.get("P").properties)
    }

    @Test
    fun propertiesForAMissingShapeAreIgnored() {
        val repository = repository()
        val before = repository.entities.value
        SetPropertiesCommand(repository, EntityId("missing"), mapOf("x" to "1")).also { it.execute(); it.undo() }
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun undoingBeforeAnythingWasDoneChangesNothing() {
        val repository = repository()
        val before = repository.entities.value
        SetPropertiesCommand(repository, polygon.id, mapOf("x" to "1")).undo()
        assertEquals(before, repository.entities.value)
    }

    // -- the place in the drawing order

    @Test
    fun aZIndexIsSetOnAnyKindAndUndoPutsTheOldOneBack() {
        val repository = repository()
        for ((id, z) in listOf("P" to 4, "L" to -1, "M" to 9)) {
            val command = SetZIndexCommand(repository, EntityId(id), z)
            command.execute()
            assertEquals(z, repository.get(id).zIndex)
            command.undo()
            assertEquals(0, repository.get(id).zIndex)
            command.execute() // redo
            assertEquals(z, repository.get(id).zIndex)
        }
    }

    @Test
    fun aZIndexForAMissingShapeIsIgnored() {
        val repository = repository()
        val before = repository.entities.value
        SetZIndexCommand(repository, EntityId("missing"), 3).also { it.execute(); it.undo() }
        assertEquals(before, repository.entities.value)
    }
}
