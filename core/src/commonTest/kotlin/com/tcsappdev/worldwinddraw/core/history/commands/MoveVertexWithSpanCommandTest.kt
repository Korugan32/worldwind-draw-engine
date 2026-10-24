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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MoveVertexWithSpanCommandTest {
    private val id = EntityId("P")

    private fun repositoryWith(vararg lons: Double): EntityRepository {
        val repository = EntityRepository()
        repository.add(
            DrawnPolygon(
                id = id,
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = lons.mapIndexed { i, lon -> Vertex(VertexId("v$i"), GeoPosition(0.0, lon)) },
            )
        )
        return repository
    }

    private fun EntityRepository.positions() = (entities.value.single() as DrawnPolygon).vertices.map { it.position }

    private fun command(repository: EntityRepository, span: VertexSpan, lat: Double) =
        MoveVertexWithSpanCommand(repository, id, VertexId("v2"), GeoPosition(lat, 2.0), span)

    @Test
    fun movesTheVertexAndTheHiddenOnesBetweenItsAnchorsAndUndoPutsThemBack() {
        val repository = repositoryWith(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
        val original = repository.positions()
        val span = VertexSpan(VertexId("v0"), VertexId("v4"))

        command(repository, span, 2.0).execute()
        val moved = repository.positions()
        assertEquals(2.0, moved[2].latitude)
        assertTrue(moved[1].latitude > 0.0 && moved[3].latitude > 0.0, "the hidden ones followed")
        assertEquals(original[0], moved[0])
        assertEquals(original[5], moved[5])

        command(repository, span, 2.0).undo()
        assertEquals(original, repository.positions())
    }

    @Test
    fun runningItAgainWithAnotherPositionStartsFromTheOriginalsSoTheMovesDoNotAddUp() {
        val repository = repositoryWith(0.0, 1.0, 2.0, 3.0, 4.0)
        val span = VertexSpan(VertexId("v0"), VertexId("v4"))
        command(repository, span, 2.0).execute() // one live preview...
        command(repository, span, 2.0).execute() // ...and the same again
        val twice = repository.positions()

        val once = repositoryWith(0.0, 1.0, 2.0, 3.0, 4.0)
        command(once, VertexSpan(VertexId("v0"), VertexId("v4")), 2.0).execute()
        assertEquals(once.positions(), twice, "moving to the same spot twice is the same as once")

        command(repository, span, 4.0).execute() // the finger moved on
        assertEquals(4.0, repository.positions()[1].latitude * 2, "v1 is half of the 4 degree move of v2, not of 2 + 4")
    }

    @Test
    fun theSharedSpanRemembersTheShapeFromBeforeTheFirstMove() {
        val repository = repositoryWith(0.0, 1.0, 2.0, 3.0, 4.0)
        val span = VertexSpan(VertexId("v0"), VertexId("v4"))
        assertEquals(null, span.originalVertices)
        command(repository, span, 2.0).execute()
        assertEquals(0.0, span.originalVertices!![2].position.latitude)
        assertNotEquals(span.originalVertices, (repository.entities.value.single() as DrawnPolygon).vertices)
    }
}
