package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CombineShapesCommandTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))
    private val otherStyle = ShapeStyle.SolidColor(ColorRgba(0, 0, 255))

    private fun ring(prefix: String, x: Double) = listOf(
        Vertex(VertexId("$prefix-1"), GeoPosition(0.0, x)), Vertex(VertexId("$prefix-2"), GeoPosition(0.0, x + 1)), Vertex(VertexId("$prefix-3"), GeoPosition(1.0, x + 1)),
    )

    private val target = DrawnPolygon(
        EntityId("T"), style, ring("t", 0.0), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), 4f, description = "target", zIndex = 3, properties = mapOf("k" to "v"),
    )
    private val absorbed = DrawnPolygon(EntityId("A"), otherStyle, ring("a", 10.0), description = "absorbed")
    private val between = DrawnMarker(EntityId("M"), style, GeoPosition(5.0, 5.0))
    private val last = DrawnPolygon(EntityId("L"), style, ring("l", 20.0))

    // the drawing order: target, a marker, the absorbed one, another polygon
    private fun repository() = EntityRepository().apply { listOf<DrawableEntity>(target, between, absorbed, last).forEach(::add) }

    private val merged = listOf(
        Vertex(VertexId("n-1"), GeoPosition(0.0, 0.0)), Vertex(VertexId("n-2"), GeoPosition(0.0, 11.0)), Vertex(VertexId("n-3"), GeoPosition(1.0, 11.0)), Vertex(VertexId("n-4"), GeoPosition(1.0, 0.0)),
    )
    private val hole = listOf(
        Vertex(VertexId("h-1"), GeoPosition(0.2, 2.0)), Vertex(VertexId("h-2"), GeoPosition(0.2, 3.0)), Vertex(VertexId("h-3"), GeoPosition(0.8, 3.0)),
    )

    @Test
    fun aMergeGivesTheTargetTheNewRingsAndRemovesTheAbsorbedShape() {
        val repository = repository()
        CombineShapesCommand(repository, target.id, listOf(merged), absorbed.id).execute()

        val shapes = repository.entities.value
        assertEquals(listOf("T", "M", "L"), shapes.map { it.id.id })
        assertEquals(target.copy(vertices = merged), shapes[0]) // id, look, outline, description, order and properties are the target's
    }

    @Test
    fun theFirstRingIsTheOutlineAndTheOthersAreItsExtraRings() {
        val repository = repository()
        CombineShapesCommand(repository, target.id, listOf(merged, hole), absorbed.id).execute()
        val result = repository.entities.value.first() as DrawnPolygon
        assertEquals(merged, result.vertices)
        assertEquals(listOf(hole), result.extraRings)
    }

    @Test
    fun aCutChangesTheTargetAndLeavesTheOtherShapeAlone() {
        val repository = repository()
        CombineShapesCommand(repository, target.id, listOf(merged), absorbedId = null).execute()
        assertEquals(listOf("T", "M", "A", "L"), repository.entities.value.map { it.id.id })
        assertEquals(absorbed, repository.entities.value[2])
    }

    @Test
    fun noRingsAtAllRemovesTheTargetToo() {
        val repository = repository()
        CombineShapesCommand(repository, target.id, emptyList(), absorbedId = null).execute()
        assertEquals(listOf("M", "A", "L"), repository.entities.value.map { it.id.id })
    }

    @Test
    fun undoingAMergePutsBothShapesBackWhereTheyWere() {
        val repository = repository()
        val before = repository.entities.value
        val command = CombineShapesCommand(repository, target.id, listOf(merged), absorbed.id)
        command.execute()
        command.undo()
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun undoingACutThatTookAllOfTheTargetPutsItBackInItsPlace() {
        val repository = repository()
        val before = repository.entities.value
        val command = CombineShapesCommand(repository, absorbed.id, emptyList())
        command.execute()
        assertEquals(listOf("T", "M", "L"), repository.entities.value.map { it.id.id })
        command.undo()
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun redoIsTheSameChangeAgain() {
        val repository = repository()
        val command = CombineShapesCommand(repository, target.id, listOf(merged, hole), absorbed.id)
        command.execute()
        val once = repository.entities.value
        command.undo()
        command.execute()
        assertEquals(once, repository.entities.value)
    }

    @Test
    fun undoLeavesAloneWhatElseChangedSinceThenButPutsBackTheOrder() {
        val repository = repository()
        val command = CombineShapesCommand(repository, target.id, listOf(merged), absorbed.id)
        command.execute()
        repository.update(last.copy(description = "edited since"))
        repository.add(DrawnMarker(EntityId("Z"), style, GeoPosition(1.0, 1.0)))

        command.undo()

        assertEquals(listOf("T", "M", "A", "L", "Z"), repository.entities.value.map { it.id.id })
        assertEquals(target, repository.entities.value[0])
        assertEquals("edited since", repository.entities.value[3].description)
    }

    @Test
    fun aMissingTargetOrAbsorbedShapeOrOneThatIsNotAPolygonMeansNothingHappens() {
        for ((targetId, absorbedId) in listOf(
            EntityId("nope") to EntityId("A"),
            EntityId("T") to EntityId("nope"),
            EntityId("M") to EntityId("A"), // a marker is no polygon
            EntityId("T") to EntityId("M"),
        )) {
            val repository = repository()
            val before = repository.entities.value
            val command = CombineShapesCommand(repository, targetId, listOf(merged), absorbedId)
            command.execute()
            command.undo()
            assertEquals(before, repository.entities.value, "$targetId $absorbedId")
        }
    }

    @Test
    fun aMissingAbsorbedShapeLeavesTheTargetUntouchedToo() {
        val repository = repository()
        val before = repository.entities.value
        CombineShapesCommand(repository, target.id, listOf(merged), EntityId("nope")).execute()
        assertEquals(before, repository.entities.value)
    }

    @Test
    fun undoingBeforeAnythingWasDoneChangesNothing() {
        val repository = repository()
        val before = repository.entities.value
        CombineShapesCommand(repository, target.id, listOf(merged), absorbed.id).undo()
        assertEquals(before, repository.entities.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theWholeChangeIsOneChangeOfTheRepository() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        val seen = mutableListOf<List<DrawableEntity>>()
        backgroundScope.launch { repository.entities.toList(seen) }
        val command = CombineShapesCommand(repository, target.id, listOf(merged), absorbed.id)
        command.execute()
        assertEquals(2, seen.size) // before and after, nothing in between
        command.undo()
        assertEquals(3, seen.size)
    }
}
