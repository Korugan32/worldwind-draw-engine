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

class AddVertexCommandTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun executeInsertsRightAfterTheGivenEdgeAndUndoRemovesIt() {
        val repository = EntityRepository()
        val a = Vertex(VertexId("A"), GeoPosition(0.0, 0.0))
        val b = Vertex(VertexId("B"), GeoPosition(1.0, 1.0))
        val c = Vertex(VertexId("C"), GeoPosition(2.0, 2.0))
        repository.add(DrawnPolygon(EntityId("p1"), style, listOf(a, b, c)))

        val newVertex = Vertex(VertexId("X"), GeoPosition(0.5, 0.5))
        val command = AddVertexCommand(repository, EntityId("p1"), afterEdgeIndex = 0, newVertex = newVertex)

        command.execute()
        val afterExecute = repository.entities.value.first() as DrawnPolygon
        assertEquals(listOf(a, newVertex, b, c), afterExecute.vertices)

        command.undo()
        val afterUndo = repository.entities.value.first() as DrawnPolygon
        assertEquals(listOf(a, b, c), afterUndo.vertices)
    }
}
