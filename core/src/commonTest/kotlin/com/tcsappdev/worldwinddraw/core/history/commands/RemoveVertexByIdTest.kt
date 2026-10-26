package com.tcsappdev.worldwinddraw.core.history.commands

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

class RemoveVertexByIdTest {
    private val id = EntityId("P")

    private fun repository() = EntityRepository().apply {
        add(
            DrawnPolygon(
                id = id,
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = (0 until 5).map { Vertex(VertexId("v$it"), GeoPosition(it.toDouble(), 0.0)) },
            )
        )
    }

    private fun EntityRepository.ids() = (entities.value.single() as DrawnPolygon).vertices.map { it.id.id }

    @Test
    fun builtFromAnIdItFindsTheVertexWhenItRunsAndUndoPutsItBackAtTheSameIndex() {
        val repository = repository()
        val command = RemoveVertexCommand(repository, id, VertexId("v2"))

        command.execute()
        assertEquals(listOf("v0", "v1", "v3", "v4"), repository.ids())
        command.undo()
        assertEquals(listOf("v0", "v1", "v2", "v3", "v4"), repository.ids())
        command.execute() // redo
        assertEquals(listOf("v0", "v1", "v3", "v4"), repository.ids())
    }

    @Test
    fun itRemovesTheVertexAsItIsAtThatMomentEvenIfTheListChangedSinceItWasBuilt() {
        val repository = repository()
        val command = RemoveVertexCommand(repository, id, VertexId("v3"))
        val shape = repository.entities.value.single() as DrawnPolygon
        repository.update(shape.copy(vertices = shape.vertices.filter { it.id.id != "v0" })) // an earlier removal

        command.execute()
        assertEquals(listOf("v1", "v2", "v4"), repository.ids())
        command.undo()
        assertEquals(listOf("v1", "v2", "v3", "v4"), repository.ids(), "back where it was in the list as it was then")
    }

    @Test
    fun anUnknownVertexOrEntityMakesBothExecuteAndUndoDoNothing() {
        val repository = repository()
        RemoveVertexCommand(repository, id, VertexId("nope")).also { it.execute(); it.undo() }
        RemoveVertexCommand(repository, EntityId("missing"), VertexId("v1")).also { it.execute(); it.undo() }
        assertEquals(listOf("v0", "v1", "v2", "v3", "v4"), repository.ids())
    }

    @Test
    fun theOlderConstructorWithTheVertexAndItsIndexStillWorks() {
        val repository = repository()
        val vertex = (repository.entities.value.single() as DrawnPolygon).vertices[1]
        val command = RemoveVertexCommand(repository, id, vertex, 1)
        command.execute()
        command.undo()
        assertEquals(listOf("v0", "v1", "v2", "v3", "v4"), repository.ids())
    }
}
