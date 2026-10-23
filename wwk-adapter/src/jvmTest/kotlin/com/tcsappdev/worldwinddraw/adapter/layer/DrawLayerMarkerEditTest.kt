package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.shape.Placemark
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// jvmTest: building the marker's Placemark can't be done in Android's plain host tests (see StyleAttributesMapperTest).
class DrawLayerMarkerEditTest {
    private val id = EntityId("M")

    private fun setup(scope: kotlinx.coroutines.CoroutineScope, markerSize: Float = 20f, handleStyle: HandleStyle = HandleStyle()): Pair<DrawLayer, DrawStateMachine> {
        val repository = EntityRepository()
        repository.add(DrawnMarker(id, ShapeStyle.SolidColor(ColorRgba(0, 255, 0)), GeoPosition(10.0, 20.0), size = markerSize))
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        return DrawLayer(repository, stateMachine, scope, handleStyle = handleStyle) to stateMachine
    }

    private fun DrawLayer.placemarks() = filterIsInstance<Placemark>()
    private fun DrawLayer.moveTargets() = placemarks().filter { it.getUserProperty<Boolean>(DrawLayer.MARKER_MOVE_KEY) == true }
    private fun DrawLayer.markerIcon() = placemarks().single { it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) && !it.hasUserProperty(DrawLayer.MARKER_MOVE_KEY) && it.attributes.imageColor.alpha > 0f }

    @Test
    fun aMarkerThatIsOnlySelectedHasNoMoveTargetAndStaysPickable() = runTest {
        val (layer, stateMachine) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        delay(50)

        assertTrue(layer.moveTargets().isEmpty())
        assertTrue(layer.markerIcon().isPickEnabled)
    }

    @Test
    fun theEditedMarkerGetsATransparentFingerSizedMoveTargetAndItsOwnIconStopsBeingPickable() = runTest {
        val (layer, stateMachine) = setup(backgroundScope, markerSize = 20f, handleStyle = HandleStyle(touchTargetDiameter = 48f))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        delay(50)

        val target = layer.moveTargets().single()
        assertTrue(target.isPickEnabled)
        assertEquals(0f, target.attributes.imageColor.alpha, "draws nothing: the marker's own icon is what is dragged")
        assertEquals(48.0, target.attributes.imageScale, "a 20px marker still gets a 48px target")
        assertEquals(id, target.getUserProperty<EntityId>(DrawLayer.ENTITY_ID_KEY))
        assertFalse(layer.markerIcon().isPickEnabled, "two pickable placemarks on one spot could let the wrong one win")
    }

    @Test
    fun aBigMarkerIsGrabbedByItsWholeSizeNotJustTheMinimumTarget() = runTest {
        val (layer, stateMachine) = setup(backgroundScope, markerSize = 120f)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        delay(50)

        assertEquals(120.0, layer.moveTargets().single().attributes.imageScale)
    }

    @Test
    fun leavingEditModeRemovesTheMoveTarget() = runTest {
        val (layer, stateMachine) = setup(backgroundScope)
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(10.0, 20.0), id))
        delay(50)
        assertEquals(1, layer.moveTargets().size)

        stateMachine.onEvent(DrawEvent.Click(GeoPosition(0.0, 0.0), null))
        delay(50)

        assertTrue(layer.moveTargets().isEmpty())
        assertNull(layer.moveTargets().firstOrNull())
    }
}
