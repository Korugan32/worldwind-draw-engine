package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
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
import kotlin.test.assertTrue

class RingCommandsTest {
    private val id = EntityId("P")
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))

    private fun circle(prefix: String, count: Int, radius: Double, lat: Double = 10.0, lon: Double = 10.0) = (0 until count).map {
        val angle = 2 * PI * it / count
        Vertex(VertexId("$prefix$it"), GeoPosition(lat + radius * sin(angle), lon + radius * cos(angle)))
    }

    /** An outer ring of 5 vertices, a hole of 4 and another part of 3. */
    private fun repository(): EntityRepository = EntityRepository().apply {
        add(DrawnPolygon(id, style, circle("o", 5, 5.0), extraRings = listOf(circle("h", 4, 1.0), circle("p", 3, 1.0, lon = 30.0))))
    }

    private fun EntityRepository.polygon() = entities.value.single() as DrawnPolygon
    private fun EntityRepository.ids() = polygon().rings.map { ring -> ring.map { it.id.id } }

    // -- the commands that find a vertex by id work in whichever ring it is

    @Test
    fun movingAVertexOfAnExtraRingMovesItAndUndoPutsItBack() {
        val repository = repository()
        val before = repository.polygon()
        val target = before.rings[1][2]
        val command = MoveVertexCommand(repository, id, target.id, target.position, GeoPosition(50.0, 50.0))

        command.execute()
        assertEquals(GeoPosition(50.0, 50.0), repository.polygon().rings[1][2].position)
        assertEquals(before.rings[0], repository.polygon().rings[0], "the other rings are untouched")
        command.undo()
        assertEquals(before, repository.polygon())
    }

    @Test
    fun addingAVertexToAnExtraRingGoesIntoThatRingOnly() {
        val repository = repository()
        val command = AddVertexCommand(repository, id, afterEdgeIndex = 1, newVertex = Vertex(VertexId("new"), GeoPosition(10.0, 10.0)), ringIndex = 1)

        command.execute()
        assertEquals(listOf("h0", "h1", "new", "h2", "h3"), repository.ids()[1])
        assertEquals(5, repository.ids()[0].size)
        assertEquals(3, repository.ids()[2].size)
        command.undo()
        assertEquals(listOf("h0", "h1", "h2", "h3"), repository.ids()[1])
    }

    @Test
    fun addingAVertexWithoutARingIndexStillGoesIntoTheFirstRing() {
        val repository = repository()
        AddVertexCommand(repository, id, 0, Vertex(VertexId("new"), GeoPosition(0.0, 0.0))).execute()
        assertEquals(listOf("o0", "new", "o1", "o2", "o3", "o4"), repository.ids()[0])
    }

    @Test
    fun removingAVertexOfAnExtraRingFindsItsRingAndUndoPutsItBackInPlace() {
        val repository = repository()
        val before = repository.polygon()
        val command = RemoveVertexCommand(repository, id, VertexId("h1"))

        command.execute()
        assertEquals(listOf("h0", "h2", "h3"), repository.ids()[1])
        command.undo()
        assertEquals(before, repository.polygon())
        command.execute() // redo
        assertEquals(listOf("h0", "h2", "h3"), repository.ids()[1])
    }

    @Test
    fun theOlderRemoveConstructorCanNameTheRing() {
        val repository = repository()
        val before = repository.polygon()
        val command = RemoveVertexCommand(repository, id, before.rings[1][0], removedAtIndex = 0, removedFromRing = 1)
        command.execute()
        assertEquals(listOf("h1", "h2", "h3"), repository.ids()[1])
        command.undo()
        assertEquals(before, repository.polygon())
    }

    @Test
    fun draggingAVertexOfAnExtraRingWithItsSpanMovesOnlyThatRing() {
        val repository = EntityRepository().apply {
            add(DrawnPolygon(id, style, circle("o", 5, 5.0), extraRings = listOf(circle("h", 8, 1.0))))
        }
        val before = repository.polygon()
        // h0 and h4 are handled, h1..h3 hide between them.
        val span = VertexSpan(prevId = VertexId("h0"), nextId = VertexId("h4"))
        val command = MoveVertexWithSpanCommand(repository, id, VertexId("h2"), GeoPosition(11.0, 11.0), span)

        command.execute()
        val moved = repository.polygon()
        assertEquals(GeoPosition(11.0, 11.0), moved.rings[1][2].position)
        assertEquals(before.rings[0], moved.rings[0], "the first ring did not move")
        assertTrue(moved.rings[1][1].position != before.rings[1][1].position && moved.rings[1][3].position != before.rings[1][3].position, "the hidden ones came along")
        assertEquals(before.rings[1][0], moved.rings[1][0], "the anchors stay")
        command.undo()
        assertEquals(before, repository.polygon())
    }

    // -- simplifying works ring by ring

    @Test
    fun simplifyingCutsEveryRingOnItsOwnAndKeepsAtLeastThreeInEach() {
        val repository = EntityRepository().apply {
            add(DrawnPolygon(id, style, circle("o", 40, 5.0), extraRings = listOf(circle("h", 12, 1.0), circle("p", 3, 1.0, lon = 30.0))))
        }
        val before = repository.polygon()
        val command = SimplifyShapeCommand(repository, id, keepFraction = 0.5f)

        command.execute()
        val after = repository.polygon()
        assertEquals(20, after.rings[0].size)
        assertEquals(6, after.rings[1].size)
        assertEquals(3, after.rings[2].size, "a ring of three has nothing to give")
        for (ring in 0..2) assertTrue(after.rings[ring].all { it in before.rings[ring] }, "the kept vertices are the original ones")
        command.undo()
        assertEquals(before, repository.polygon())
        command.execute() // redo
        assertEquals(after, repository.polygon())
    }

    // -- adding and removing a whole ring

    @Test
    fun addingARingAppendsItAndUndoTakesItOut() {
        val repository = repository()
        val before = repository.polygon()
        val command = AddRingCommand(repository, id, circle("n", 4, 0.5, lon = 50.0))

        command.execute()
        assertEquals(4, repository.polygon().rings.size)
        assertEquals(listOf("n0", "n1", "n2", "n3"), repository.ids()[3])
        command.undo()
        assertEquals(before, repository.polygon())
        command.execute() // redo
        assertEquals(4, repository.polygon().rings.size)
    }

    @Test
    fun aRingThatCannotBeAddedIsIgnoredAndItsUndoTooAndNothingExistingIsTouched() {
        val repository = repository()
        val before = repository.polygon()
        for (ring in listOf(circle("n", 2, 0.5), circle("o", 4, 0.5))) { // too few vertices; ids the polygon already has
            AddRingCommand(repository, id, ring).also { it.execute(); it.undo() }
        }
        AddRingCommand(repository, EntityId("missing"), circle("n", 4, 0.5)).also { it.execute(); it.undo() }
        assertEquals(before, repository.polygon())
    }

    @Test
    fun removingARingTakesTheWholeRingAndUndoPutsItBackAmongTheOthers() {
        val repository = repository()
        val before = repository.polygon()
        val command = RemoveRingCommand(repository, id, VertexId("h2"))

        command.execute()
        assertEquals(listOf(listOf("o0", "o1", "o2", "o3", "o4"), listOf("p0", "p1", "p2")), repository.ids())
        command.undo()
        assertEquals(before, repository.polygon())
        command.execute() // redo
        assertEquals(2, repository.polygon().rings.size)
    }

    @Test
    fun removingTheFirstRingMakesTheNextOneTheFirstAndUndoRestoresTheOrder() {
        val repository = repository()
        val before = repository.polygon()
        val command = RemoveRingCommand(repository, id, VertexId("o3"))

        command.execute()
        assertEquals(listOf("h0", "h1", "h2", "h3"), repository.polygon().vertices.map { it.id.id })
        command.undo()
        assertEquals(before, repository.polygon())
    }

    @Test
    fun theOnlyRingAnUnknownVertexAndAMissingEntityAreLeftAlone() {
        val single = EntityRepository().apply { add(DrawnPolygon(id, style, circle("o", 4, 1.0))) }
        RemoveRingCommand(single, id, VertexId("o1")).also { it.execute(); it.undo() }
        assertEquals(1, single.polygon().rings.size)

        val repository = repository()
        val before = repository.polygon()
        RemoveRingCommand(repository, id, VertexId("nope")).also { it.execute(); it.undo() }
        RemoveRingCommand(repository, EntityId("missing"), VertexId("h1")).also { it.execute(); it.undo() }
        assertEquals(before, repository.polygon())
    }
}
