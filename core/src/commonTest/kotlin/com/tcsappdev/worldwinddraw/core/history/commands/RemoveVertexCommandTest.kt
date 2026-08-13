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

class RemoveVertexCommandTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun executeRemovesAndUndoRestoresAtTheOriginalIndex() {
        val repository = EntityRepository()
        val a = Vertex(VertexId("A"), GeoPosition(0.0, 0.0))
        val b = Vertex(VertexId("B"), GeoPosition(1.0, 1.0))
        val c = Vertex(VertexId("C"), GeoPosition(2.0, 2.0))
        val d = Vertex(VertexId("D"), GeoPosition(3.0, 3.0))
        // Starts with 4 vertices, not 3: removing one must still leave a valid polygon
        // (DrawnPolygon requires >= 3), so a minimal triangle can't be used here.
        repository.add(DrawnPolygon(EntityId("p1"), style, listOf(a, b, c, d)))

        val command = RemoveVertexCommand(repository, EntityId("p1"), removedVertex = b, removedAtIndex = 1)

        command.execute()
        val afterExecute = repository.entities.value.first() as DrawnPolygon
        assertEquals(listOf(a, c, d), afterExecute.vertices)

        command.undo()
        val afterUndo = repository.entities.value.first() as DrawnPolygon
        assertEquals(listOf(a, b, c, d), afterUndo.vertices)
    }
}
