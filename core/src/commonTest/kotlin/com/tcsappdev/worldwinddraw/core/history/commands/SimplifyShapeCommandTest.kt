package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimplifyShapeCommandTest {
    private val id = EntityId("P")
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))

    private fun ringVertices(count: Int) = List(count) { i ->
        val a = 2 * PI * i / count
        Vertex(VertexId("v$i"), GeoPosition(5 * sin(a) + 0.1 * sin(11 * a), 5 * cos(a)))
    }

    private fun repositoryWithPolygon(count: Int) = EntityRepository().apply { add(DrawnPolygon(id, style, ringVertices(count))) }

    private fun EntityRepository.polygon() = entities.value.single() as DrawnPolygon

    @Test
    fun itKeepsAboutTheAskedFractionOfTheOriginalVerticesAndUndoBringsAllBackInOrder() {
        val repository = repositoryWithPolygon(100)
        val original = repository.polygon().vertices
        val command = SimplifyShapeCommand(repository, id, 0.3f)

        command.execute()
        val simplified = repository.polygon().vertices
        assertEquals(30, simplified.size)
        assertTrue(simplified.all { it in original }, "every kept vertex is an original one, ids and all")
        assertEquals(simplified.map { original.indexOf(it) }.sorted(), simplified.map { original.indexOf(it) }, "in the original order")

        command.undo()
        assertEquals(original, repository.polygon().vertices)
    }

    @Test
    fun redoGivesTheSameResultAsTheFirstRun() {
        val repository = repositoryWithPolygon(60)
        val command = SimplifyShapeCommand(repository, id, 0.5f)
        command.execute()
        val first = repository.polygon().vertices
        command.undo()
        command.execute()
        assertEquals(first, repository.polygon().vertices)
    }

    @Test
    fun aPolygonNeverGoesBelowThreeVerticesNorAPolylineBelowTwo() {
        val repository = repositoryWithPolygon(40)
        SimplifyShapeCommand(repository, id, 0.01f).execute()
        assertEquals(3, repository.polygon().vertices.size)

        val lines = EntityRepository().apply { add(DrawnPolyline(EntityId("L"), style, ringVertices(40))) }
        SimplifyShapeCommand(lines, EntityId("L"), 0.01f).execute()
        assertEquals(2, (lines.entities.value.single() as DrawnPolyline).vertices.size)
    }

    @Test
    fun aFractionOutsideZeroToOneIsRejectedAndAMissingShapeIsIgnored() {
        assertFailsWith<IllegalArgumentException> { SimplifyShapeCommand(repositoryWithPolygon(10), id, 0f) }
        assertFailsWith<IllegalArgumentException> { SimplifyShapeCommand(repositoryWithPolygon(10), id, 1.5f) }
        val repository = repositoryWithPolygon(10)
        SimplifyShapeCommand(repository, EntityId("gone"), 0.5f).also { it.execute(); it.undo() }
        assertEquals(10, repository.polygon().vertices.size)
    }
}
