package com.tcsappdev.worldwinddraw.booleanops

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.CombineKind
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The events that merge and cut, with the real overlay (JTS) behind them, through the state machine and its history.
class OverlayEventsTest {
    private val red = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))
    private val blue = ShapeStyle.SolidColor(ColorRgba(0, 0, 255))

    private fun square(id: String, x: Double, y: Double, side: Double, style: ShapeStyle = red) = DrawnPolygon(
        EntityId(id), style,
        listOf(
            Vertex(VertexId("$id-sw"), GeoPosition(y, x)), Vertex(VertexId("$id-se"), GeoPosition(y, x + side)),
            Vertex(VertexId("$id-ne"), GeoPosition(y + side, x + side)), Vertex(VertexId("$id-nw"), GeoPosition(y + side, x)),
        ),
        description = "about $id", zIndex = 2, properties = mapOf("owner" to id),
    )

    private class Setup(vararg shapes: DrawnPolygon) {
        val repository = EntityRepository().apply { shapes.forEach(::add) }
        val history = HistoryManager()
        val machine = DrawStateMachine(history, repository, JtsOverlayEngine())
        fun select(id: String) = machine.onEvent(DrawEvent.ModeChange(DrawMode.Select(EntityId(id))))
        fun shape(id: String) = repository.entities.value.single { it.id.id == id } as DrawnPolygon
    }

    private fun area(shape: DrawnPolygon): Double =
        PolygonParts.split(shape.rings.map { ring -> ring.map { it.position } }).sumOf { part ->
            fun ringArea(ring: List<GeoPosition>) = abs(ring.indices.sumOf { i ->
                val p = ring[i]
                val q = ring[(i + 1) % ring.size]
                p.longitude * q.latitude - q.longitude * p.latitude
            }) / 2
            ringArea(part.first()) - part.drop(1).sumOf { ringArea(it) }
        }

    @Test
    fun mergingTwoOverlappingSquaresMakesOneShapeThatKeepsTheSelectedOnesIdAndLook() {
        val setup = Setup(square("A", 0.0, 0.0, 10.0), square("B", 5.0, 5.0, 10.0, blue))
        setup.select("A")

        setup.machine.onEvent(DrawEvent.MergeWith(EntityId("B"), "m"))

        assertEquals(listOf("A"), setup.repository.entities.value.map { it.id.id })
        val merged = setup.shape("A")
        assertEquals(175.0, area(merged), 1e-9)
        assertEquals(red, merged.style)
        assertEquals("about A", merged.description)
        assertEquals(2, merged.zIndex)
        assertEquals(mapOf("owner" to "A"), merged.properties)
    }

    @Test
    fun theNewVerticesGetIdsFromThePrefixAndTheOthersKeepTheirs() {
        val setup = Setup(square("A", 0.0, 0.0, 10.0), square("B", 5.0, 5.0, 10.0))
        setup.select("A")
        setup.machine.onEvent(DrawEvent.MergeWith(EntityId("B"), "m"))

        val ids = setup.shape("A").vertices.map { it.id.id }.toSet()
        assertEquals(setOf("A-sw", "A-se", "A-nw", "B-nw", "B-ne", "B-se", "m-1", "m-2"), ids)
    }

    @Test
    fun undoingAMergeBringsBackBothShapesExactlyAndRedoMergesAgain() {
        val setup = Setup(square("A", 0.0, 0.0, 10.0), square("B", 5.0, 5.0, 10.0, blue))
        val before = setup.repository.entities.value
        setup.select("A")
        setup.machine.onEvent(DrawEvent.MergeWith(EntityId("B"), "m"))
        val merged = setup.repository.entities.value

        setup.history.undo()
        assertEquals(before, setup.repository.entities.value)
        setup.history.redo()
        assertEquals(merged, setup.repository.entities.value)
    }

    @Test
    fun cuttingARoundHoleOutOfAShapeMakesAHoleAndLeavesTheCutterThere() {
        val setup = Setup(square("A", 0.0, 0.0, 30.0), square("B", 10.0, 10.0, 10.0, blue))
        setup.select("A")

        setup.machine.onEvent(DrawEvent.CutBy(EntityId("B"), "c"))

        assertEquals(listOf("A", "B"), setup.repository.entities.value.map { it.id.id })
        assertEquals(1, setup.shape("A").extraRings.size)
        assertEquals(800.0, area(setup.shape("A")), 1e-9)
        assertEquals(setOf("B-sw", "B-se", "B-ne", "B-nw"), setup.shape("A").extraRings.single().map { it.id.id }.toSet())
        assertEquals(DrawMode.Select(EntityId("A")), setup.machine.state.value.mode)
    }

    @Test
    fun cuttingAShapeInTwoKeepsItAsOneShapeOfTwoParts() {
        val setup = Setup(square("A", 0.0, 0.0, 30.0), DrawnPolygon(EntityId("B"), blue, listOf(
            Vertex(VertexId("B1"), GeoPosition(-5.0, 10.0)), Vertex(VertexId("B2"), GeoPosition(-5.0, 20.0)),
            Vertex(VertexId("B3"), GeoPosition(35.0, 20.0)), Vertex(VertexId("B4"), GeoPosition(35.0, 10.0)),
        )))
        setup.select("A")
        setup.machine.onEvent(DrawEvent.CutBy(EntityId("B"), "c"))

        assertEquals(2, PolygonParts.split(setup.shape("A").rings.map { ring -> ring.map { it.position } }).size)
        assertEquals(600.0, area(setup.shape("A")), 1e-9)
    }

    @Test
    fun cuttingAShapeThatIsCompletelyCoveredRemovesItAndLeavesTheSelectionAndUndoBringsItBack() {
        val setup = Setup(square("A", 5.0, 5.0, 5.0), square("B", 0.0, 0.0, 20.0, blue))
        val before = setup.repository.entities.value
        setup.machine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(EntityId("A"))))

        setup.machine.onEvent(DrawEvent.CutBy(EntityId("B"), "c"))

        assertEquals(listOf("B"), setup.repository.entities.value.map { it.id.id })
        assertEquals(DrawMode.Idle, setup.machine.state.value.mode)
        setup.history.undo()
        assertEquals(before, setup.repository.entities.value)
    }

    @Test
    fun neighboursWhoseBordersAreAHairsBreadthApartMergeIntoOneShape() {
        val setup = Setup(square("A", 0.0, 0.0, 10.0), square("B", 10.0 + 1e-9, 0.0, 10.0, blue))
        setup.select("A")
        setup.machine.onEvent(DrawEvent.MergeWith(EntityId("B"), "m"))
        assertEquals(listOf("A"), setup.repository.entities.value.map { it.id.id })
        assertEquals(1, setup.shape("A").rings.size)
    }

    @Test
    fun aShapeThatCrossesItselfMeansNothingHappensAndNothingIsRecorded() {
        val bowTie = DrawnPolygon(EntityId("bow"), red, listOf(
            Vertex(VertexId("w1"), GeoPosition(0.0, 0.0)), Vertex(VertexId("w2"), GeoPosition(10.0, 10.0)),
            Vertex(VertexId("w3"), GeoPosition(0.0, 10.0)), Vertex(VertexId("w4"), GeoPosition(10.0, 0.0)),
        ))
        val setup = Setup(bowTie, square("B", 0.0, 0.0, 10.0))
        val before = setup.repository.entities.value
        setup.select("bow")
        setup.machine.onEvent(DrawEvent.MergeWith(EntityId("B"), "m"))
        setup.machine.onEvent(DrawEvent.CutBy(EntityId("B"), "c"))
        assertEquals(before, setup.repository.entities.value)
        assertFalse(setup.history.canUndo.value)
        assertEquals(DrawMode.Select(EntityId("bow")), setup.machine.state.value.mode)
    }

    @Test
    fun theValidityCheckTheHostCanUseBeforeAskingGivesTheReason() {
        val engine = JtsOverlayEngine()
        assertNull(engine.validate(square("A", 0.0, 0.0, 10.0)))
        assertTrue(engine.validate(DrawnPolygon(EntityId("flat"), red, listOf(
            Vertex(VertexId("f1"), GeoPosition(1.0, 1.0)), Vertex(VertexId("f2"), GeoPosition(1.0, 1.0)), Vertex(VertexId("f3"), GeoPosition(1.0, 1.0)),
        ))) != null)
    }

    @Test
    fun aCoarserGridMergesNeighboursThatAreFurtherApart() {
        fun ringsAfterMerging(engine: JtsOverlayEngine): Int {
            val repository = EntityRepository().apply { add(square("A", 0.0, 0.0, 10.0)); add(square("B", 10.0 + 1e-6, 0.0, 10.0)) }
            val machine = DrawStateMachine(HistoryManager(), repository, engine)
            machine.onEvent(DrawEvent.ModeChange(DrawMode.Select(EntityId("A"))))
            machine.onEvent(DrawEvent.MergeWith(EntityId("B"), "m"))
            return (repository.entities.value.single() as DrawnPolygon).rings.size
        }
        assertEquals(2, ringsAfterMerging(JtsOverlayEngine())) // a cell of 1e-7 does not close a gap of 1e-6: two parts in one shape
        assertEquals(1, ringsAfterMerging(JtsOverlayEngine(gridSize = 1e-4)))
    }

    @Test
    fun aGridThatIsNotAGridIsRefused() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { JtsOverlayEngine(gridSize = -1.0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { JtsOverlayEngine(gridSize = Double.NaN) }
    }

    // ---- the second shape picked by a tap ----

    private fun tap(setup: Setup, hit: String?) = setup.machine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), hit?.let { EntityId(it) }))

    @Test
    fun selectingOneShapeThenTappingAnotherMergesThemWithTheModeBetween() {
        val setup = Setup(square("A", 0.0, 0.0, 10.0), square("B", 5.0, 5.0, 10.0, blue))
        tap(setup, "A")
        setup.machine.onEvent(DrawEvent.ModeChange(DrawMode.Combine(EntityId("A"), CombineKind.Merge, "m")))
        tap(setup, "B")

        assertEquals(listOf("A"), setup.repository.entities.value.map { it.id.id })
        assertEquals(175.0, area(setup.shape("A")), 1e-9)
        assertEquals(DrawMode.Select(EntityId("A")), setup.machine.state.value.mode)
        setup.history.undo()
        assertEquals(2, setup.repository.entities.value.size)
    }

    @Test
    fun theSameWithACutThroughTheMiddle() {
        val setup = Setup(square("A", 0.0, 0.0, 30.0), square("B", 10.0, 10.0, 10.0, blue))
        tap(setup, "A")
        setup.machine.onEvent(DrawEvent.ModeChange(DrawMode.Combine(EntityId("A"), CombineKind.Cut, "c")))
        tap(setup, "B")

        assertEquals(800.0, area(setup.shape("A")), 1e-9)
        assertEquals(listOf("A", "B"), setup.repository.entities.value.map { it.id.id })
    }

    @Test
    fun tappingTheMapInsteadCancelsAndChangesNothing() {
        val setup = Setup(square("A", 0.0, 0.0, 10.0), square("B", 5.0, 5.0, 10.0, blue))
        val before = setup.repository.entities.value
        tap(setup, "A")
        setup.machine.onEvent(DrawEvent.ModeChange(DrawMode.Combine(EntityId("A"), CombineKind.Merge, "m")))
        tap(setup, null)
        assertEquals(before, setup.repository.entities.value)
        assertEquals(DrawMode.Select(EntityId("A")), setup.machine.state.value.mode)
        assertFalse(setup.history.canUndo.value)
    }
}
