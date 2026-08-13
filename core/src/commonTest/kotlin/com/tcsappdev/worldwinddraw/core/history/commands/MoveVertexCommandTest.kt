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

class MoveVertexCommandTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun executeMovesOnlyTheTargetVertexAndUndoRestoresIt() {
        val repository = EntityRepository()
        val v1 = Vertex(VertexId("v1"), GeoPosition(0.0, 0.0))
        val v2 = Vertex(VertexId("v2"), GeoPosition(1.0, 1.0))
        val v3 = Vertex(VertexId("v3"), GeoPosition(2.0, 2.0))
        repository.add(DrawnPolygon(EntityId("p1"), style, listOf(v1, v2, v3)))

        val command = MoveVertexCommand(
            repository = repository,
            entityId = EntityId("p1"),
            vertexId = VertexId("v2"),
            oldPosition = GeoPosition(1.0, 1.0),
            newPosition = GeoPosition(9.0, 9.0),
        )

        command.execute()
        val afterExecute = repository.entities.value.first() as DrawnPolygon
        // Only v2 should move; v1 and v3 stay exactly where they were.
        assertEquals(GeoPosition(0.0, 0.0), afterExecute.vertices[0].position)
        assertEquals(GeoPosition(9.0, 9.0), afterExecute.vertices[1].position)
        assertEquals(GeoPosition(2.0, 2.0), afterExecute.vertices[2].position)

        command.undo()
        val afterUndo = repository.entities.value.first() as DrawnPolygon
        assertEquals(GeoPosition(1.0, 1.0), afterUndo.vertices[1].position)
    }
}
