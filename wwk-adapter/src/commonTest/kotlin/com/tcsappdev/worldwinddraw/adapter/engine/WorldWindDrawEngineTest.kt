package com.tcsappdev.worldwinddraw.adapter.engine

import com.tcsappdev.worldwinddraw.adapter.input.PickInputBridge
import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.core.animation.Animatable
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorldWindDrawEngineTest {
    private val square = DrawnPolygon(
        EntityId("sq"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0)),
        listOf(Vertex(VertexId("a"), GeoPosition(0.0, 0.0)), Vertex(VertexId("b"), GeoPosition(0.0, 1.0)), Vertex(VertexId("c"), GeoPosition(1.0, 1.0))),
    )

    private fun kotlinx.coroutines.test.TestScope.engine() = WorldWindDrawEngine(backgroundScope)

    private fun WorldWindDrawEngine.tap(lat: Double, lon: Double) = send(DrawEvent.Click(GeoPosition(lat, lon), hitEntityId = null))

    @Test
    fun itStartsIdleWithNothingAndNothingToUndo() = runTest {
        val engine = engine()
        assertEquals(DrawMode.Idle, engine.state.value.mode)
        assertEquals(emptyList(), engine.entities.value)
        assertEquals(false, engine.canUndo.value)
        assertEquals(false, engine.canRedo.value)
    }

    @Test
    fun theLayerAndTheInputAreTheOnesItMade() = runTest {
        val engine = engine()
        assertTrue(engine.input is PickInputBridge)
        assertTrue(engine.layer is DrawLayer)
    }

    @Test
    fun aShapePutInTheRepositoryIsShownByTheLayer() = runTest {
        val engine = engine()
        engine.repository.add(square)
        delay(50)
        assertEquals(1, engine.layer.count { it is Polygon && it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) })
    }

    @Test
    fun drawingAShapeTakesPointsAndFinishingItMakesAShapeWithTheIdItReturns() = runTest {
        val engine = engine()
        engine.startDrawing(DrawableEntityType.Polygon)
        assertTrue(engine.state.value.mode is DrawMode.Draw)
        engine.tap(0.0, 0.0); engine.tap(0.0, 1.0); engine.tap(1.0, 1.0)

        val id = engine.finishDrawing()
        assertNotNull(id)
        val shape = engine.entities.value.single()
        assertEquals(id, shape.id)
        assertEquals(3, (shape as DrawnPolygon).vertices.size)
        assertEquals(3, shape.vertices.map { it.id }.toSet().size, "every corner has an id of its own")
    }

    @Test
    fun finishingWithAGivenIdUsesIt() = runTest {
        val engine = engine()
        engine.startDrawing(DrawableEntityType.Polyline)
        engine.tap(0.0, 0.0); engine.tap(1.0, 1.0)
        assertEquals(EntityId("route"), engine.finishDrawing(EntityId("route")))
        assertEquals("route", engine.entities.value.single().id.id)
    }

    @Test
    fun finishingWhenNothingIsDrawnDoesNothing() = runTest {
        val engine = engine()
        assertNull(engine.finishDrawing())
        assertEquals(emptyList(), engine.entities.value)
    }

    @Test
    fun finishingWithTooFewPointsMakesNoShape() = runTest {
        val engine = engine()
        engine.startDrawing(DrawableEntityType.Polygon)
        engine.tap(0.0, 0.0); engine.tap(0.0, 1.0)
        assertNull(engine.finishDrawing())
        assertEquals(emptyList(), engine.entities.value)
    }

    @Test
    fun cancelGoesBackToIdleAndKeepsNoShape() = runTest {
        val engine = engine()
        engine.startDrawing(DrawableEntityType.Polygon)
        engine.tap(0.0, 0.0)
        engine.cancel()
        assertEquals(DrawMode.Idle, engine.state.value.mode)
        assertEquals(emptyList(), engine.entities.value)
    }

    @Test
    fun aShapeCanBeSelectedEditedAndTheEditEnded() = runTest {
        val engine = engine()
        engine.repository.add(square)
        engine.select(square.id)
        assertEquals(DrawMode.Select(square.id), engine.state.value.mode)
        engine.edit(square.id)
        assertTrue(engine.state.value.mode is DrawMode.Edit)
        engine.stopEditing()
        assertEquals(DrawMode.Select(square.id), engine.state.value.mode)
        engine.stopEditing() // not editing: nothing
        assertEquals(DrawMode.Select(square.id), engine.state.value.mode)
    }

    @Test
    fun deletingTheSelectedShapeCanBeUndone() = runTest {
        val engine = engine()
        engine.repository.add(square)
        engine.select(square.id)
        engine.deleteSelected()
        assertEquals(emptyList(), engine.entities.value)
        assertTrue(engine.canUndo.value)

        engine.undo()
        assertEquals(listOf(square), engine.entities.value)
        assertTrue(engine.canRedo.value)
        engine.redo()
        assertEquals(emptyList(), engine.entities.value)
    }

    @Test
    fun theHostIsAskedToRedrawWhenTheContentChanges() = runTest {
        val engine = engine()
        var asked = 0
        engine.onRedrawNeeded { asked++ }
        engine.repository.add(square)
        delay(50)
        assertTrue(asked > 0, "asked $asked times")
    }

    @Test
    fun tickMovesWhatIsPlayedAndStopStopsIt() = runTest {
        val engine = engine()
        var ticked = 0L
        val counter = object : Animatable { override fun onTick(deltaMs: Long) { ticked += deltaMs } }
        engine.play(counter)
        engine.tick(16); engine.tick(16)
        assertEquals(32L, ticked)
        engine.stop(counter)
        engine.tick(16)
        assertEquals(32L, ticked)
    }

    @Test
    fun theRepositoryAndHistoryAreTheOnesTheEngineUses() = runTest {
        val engine = engine()
        assertSame(engine.repository.entities, engine.entities)
        assertSame(engine.history.canUndo, engine.canUndo)
    }

    @Test
    fun withoutAnOverlayMergingChangesNothing() = runTest {
        val engine = engine()
        val other = square.copy(id = EntityId("other"))
        engine.repository.add(square); engine.repository.add(other)
        engine.select(square.id)
        engine.mergeWith(other.id)
        assertEquals(2, engine.entities.value.size)
    }
}
