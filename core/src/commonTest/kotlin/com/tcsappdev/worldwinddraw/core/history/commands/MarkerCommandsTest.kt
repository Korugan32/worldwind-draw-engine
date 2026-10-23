package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
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
import kotlin.test.assertFailsWith

class MarkerCommandsTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))
    private val id = EntityId("m")

    private fun repositoryWithMarker(): Pair<EntityRepository, DrawnMarker> {
        val marker = DrawnMarker(id, style, GeoPosition(1.0, 1.0), size = 20f)
        return EntityRepository().apply { add(marker) } to marker
    }

    private fun EntityRepository.marker() = entities.value.single { it.id == id } as DrawnMarker

    @Test
    fun moveMarkerMovesItAndUndoPutsItBack() {
        val (repository, _) = repositoryWithMarker()
        val command = MoveMarkerCommand(repository, id, GeoPosition(1.0, 1.0), GeoPosition(5.0, 6.0))

        command.execute()
        assertEquals(GeoPosition(5.0, 6.0), repository.marker().position)
        command.undo()
        assertEquals(GeoPosition(1.0, 1.0), repository.marker().position)
    }

    @Test
    fun moveMarkerKeepsEverythingElseAboutTheMarker() {
        val (repository, original) = repositoryWithMarker()

        MoveMarkerCommand(repository, id, original.position, GeoPosition(5.0, 6.0)).execute()

        assertEquals(original.copy(position = GeoPosition(5.0, 6.0)), repository.marker())
    }

    @Test
    fun resizeMarkerRemembersTheSizeItHadWhenItRanSoUndoRestoresIt() {
        val (repository, _) = repositoryWithMarker()
        val command = ResizeMarkerCommand(repository, id, newSize = 50f)
        // The marker is resized by something else after the command is built but before it runs.
        repository.update(repository.marker().copy(size = 30f))

        command.execute()
        assertEquals(50f, repository.marker().size)
        command.undo()
        assertEquals(30f, repository.marker().size, "back to the size it had when the command ran")
    }

    @Test
    fun resizeMarkerNeedsAPositiveSize() {
        val (repository, _) = repositoryWithMarker()

        assertFailsWith<IllegalArgumentException> { ResizeMarkerCommand(repository, id, newSize = 0f) }
        assertFailsWith<IllegalArgumentException> { ResizeMarkerCommand(repository, id, newSize = -4f) }
    }

    @Test
    fun bothDoNothingForAShapeThatIsNotAMarkerOrDoesNotExist() {
        val polygon = DrawnPolygon(
            id = EntityId("p"),
            style = style,
            vertices = listOf(
                Vertex(VertexId("a"), GeoPosition(0.0, 0.0)),
                Vertex(VertexId("b"), GeoPosition(1.0, 0.0)),
                Vertex(VertexId("c"), GeoPosition(1.0, 1.0)),
            ),
        )
        val repository = EntityRepository().apply { add(polygon) }

        MoveMarkerCommand(repository, EntityId("p"), GeoPosition(0.0, 0.0), GeoPosition(9.0, 9.0)).execute()
        ResizeMarkerCommand(repository, EntityId("p"), 99f).execute()
        MoveMarkerCommand(repository, EntityId("missing"), GeoPosition(0.0, 0.0), GeoPosition(9.0, 9.0)).execute()
        ResizeMarkerCommand(repository, EntityId("missing"), 99f).apply { execute(); undo() }

        assertEquals(listOf(polygon), repository.entities.value)
    }
}
