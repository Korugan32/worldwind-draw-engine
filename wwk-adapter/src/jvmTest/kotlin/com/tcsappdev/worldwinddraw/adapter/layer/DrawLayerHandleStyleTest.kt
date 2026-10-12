package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.shape.Placemark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// jvmTest, not commonTest: entering Edit mode builds handle Placemarks, and PlacemarkAttributes()
// can't be constructed in Android's plain host unit tests (see StyleAttributesMapperTest). The
// JVM has no handle images (see HandleImages.jvm.kt), so handles are plain squares whose side
// is exactly the requested diameter — which makes the size directly assertable here.
class DrawLayerHandleStyleTest {
    private val id = EntityId("A")

    // An edit handle is two renderables: a visible dot that is NOT pickable, and an invisible
    // pickable square (tagged for PickInputBridge) that a finger actually grabs.
    private class Setup(val layer: DrawLayer, val stateMachine: DrawStateMachine) {
        fun placemarks() = layer.filterIsInstance<Placemark>()
        fun dots() = placemarks().filter { !it.isPickEnabled }
        fun vertexTargets() = placemarks().filter { it.hasUserProperty(DrawLayer.VERTEX_ID_KEY) }
        fun midpointTargets() = placemarks().filter { it.hasUserProperty(DrawLayer.EDGE_INDEX_KEY) }
    }

    private fun editing(scope: CoroutineScope, onContentChanged: () -> Unit = {}, handleStyle: HandleStyle? = null): Setup {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        repository.add(
            DrawnPolygon(
                id = id,
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
                vertices = listOf(
                    Vertex(VertexId("v1"), GeoPosition(0.0, 0.0)),
                    Vertex(VertexId("v2"), GeoPosition(1.0, 0.0)),
                    Vertex(VertexId("v3"), GeoPosition(1.0, 1.0)),
                ),
            )
        )
        val layer = if (handleStyle != null) DrawLayer(repository, stateMachine, scope, onContentChanged, handleStyle)
        else DrawLayer(repository, stateMachine, scope, onContentChanged)
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
        return Setup(layer, stateMachine)
    }

    @Test
    fun appliesTheRequestedDiameters() = runTest {
        val setup = editing(backgroundScope, handleStyle = HandleStyle(vertexDiameter = 31f, midpointDiameter = 17f))
        delay(50)

        // 3 vertex dots + 3 midpoint dots; they differ in size, so the two kinds are told apart by it.
        assertEquals(3, setup.dots().count { it.attributes.imageScale == 31.0 })
        assertEquals(3, setup.dots().count { it.attributes.imageScale == 17.0 })
    }

    @Test
    fun usesTheDefaultStyleWhenNoneIsGiven() = runTest {
        val setup = editing(backgroundScope)
        delay(50)

        val style = HandleStyle()
        assertEquals(3, setup.dots().count { it.attributes.imageScale == style.vertexDiameter.toDouble() })
        assertEquals(3, setup.dots().count { it.attributes.imageScale == style.midpointDiameter.toDouble() })
    }

    @Test
    fun changingHandleStyleResizesTheLiveHandlesAndRequestsARedraw() = runTest {
        var redraws = 0
        val setup = editing(backgroundScope, onContentChanged = { redraws++ })
        delay(50)
        val before = redraws

        setup.layer.handleStyle = HandleStyle(vertexDiameter = 50f, midpointDiameter = 40f)
        delay(50)

        assertEquals(3, setup.dots().count { it.attributes.imageScale == 50.0 })
        assertEquals(3, setup.dots().count { it.attributes.imageScale == 40.0 })
        assertTrue(redraws > before, "a live size change must ask the host to redraw")
        assertEquals(HandleStyle(vertexDiameter = 50f, midpointDiameter = 40f), setup.layer.handleStyle)
    }

    @Test
    fun handleFillColorIsAppliedAndCanBeChangedLive() = runTest {
        val setup = editing(backgroundScope, handleStyle = HandleStyle(fillColor = ColorRgba(255, 0, 0)))
        delay(50)
        // No handle image on the JVM, so a dot is a plain square in the fill colour
        // (midpoints a little fainter — see DrawLayer.MIDPOINT_FALLBACK_OPACITY).
        val vertexDots = { setup.dots().filter { it.attributes.imageScale == HandleStyle().vertexDiameter.toDouble() } }
        val midpointDots = { setup.dots().filter { it.attributes.imageScale == HandleStyle().midpointDiameter.toDouble() } }
        assertTrue(vertexDots().all { it.attributes.imageColor.red == 1f && it.attributes.imageColor.blue == 0f })
        assertTrue(midpointDots().all { it.attributes.imageColor.red == 1f && it.attributes.imageColor.alpha < 1f })

        setup.layer.handleStyle = HandleStyle(fillColor = ColorRgba(0, 0, 255))
        delay(50)

        assertTrue(vertexDots().all { it.attributes.imageColor.blue == 1f && it.attributes.imageColor.red == 0f })
    }

    @Test
    fun everyHandleHasAnInvisiblePickableTargetBiggerThanItsDot() = runTest {
        val setup = editing(backgroundScope, handleStyle = HandleStyle(vertexDiameter = 27f, midpointDiameter = 24f, touchTargetDiameter = 60f))
        delay(50)

        // The dots are not pickable; only the tagged targets are — and they are what a finger grabs.
        assertEquals(3, setup.vertexTargets().size)
        assertEquals(3, setup.midpointTargets().size)
        assertTrue(setup.dots().none { it.isPickEnabled })
        (setup.vertexTargets() + setup.midpointTargets()).forEach { target ->
            assertTrue(target.isPickEnabled)
            assertEquals(60.0, target.attributes.imageScale)
            // Fully transparent: it must draw nothing visible.
            assertEquals(0f, target.attributes.imageColor.alpha)
        }
    }

    @Test
    fun aTargetIsNeverSmallerThanTheDotItBelongsTo() = runTest {
        val setup = editing(backgroundScope, handleStyle = HandleStyle(vertexDiameter = 40f, midpointDiameter = 20f, touchTargetDiameter = 30f))
        delay(50)

        assertTrue(setup.vertexTargets().all { it.attributes.imageScale == 40.0 }, "raised to the dot's own size")
        assertTrue(setup.midpointTargets().all { it.attributes.imageScale == 30.0 })
    }
}
