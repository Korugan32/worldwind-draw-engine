package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.geometry.withZIndex
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

// jvmTest: a marker is a Placemark, which Android's plain host tests can't build (see StyleAttributesMapperTest).
class DrawLayerOrderTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))

    private fun ring(prefix: String) = listOf(
        Vertex(VertexId("${prefix}1"), GeoPosition(0.0, 0.0)), Vertex(VertexId("${prefix}2"), GeoPosition(0.0, 1.0)), Vertex(VertexId("${prefix}3"), GeoPosition(1.0, 1.0)),
    )

    private fun polygon(id: String, z: Int = 0) = DrawnPolygon(EntityId(id), style, ring(id), zIndex = z)
    private fun line(id: String, z: Int = 0) = DrawnPolyline(EntityId(id), style, ring(id), zIndex = z)
    private fun marker(id: String, z: Int = 0) = DrawnMarker(EntityId(id), style, GeoPosition(0.5, 0.5), zIndex = z)

    /** The shapes in the layer from the bottom up (the highlights and pictures carry no id). */
    private fun DrawLayer.order() = mapNotNull { it.getUserProperty<EntityId>(DrawLayer.ENTITY_ID_KEY)?.id }

    private fun layerWith(scope: kotlinx.coroutines.CoroutineScope, vararg entities: DrawableEntity): Pair<DrawLayer, EntityRepository> {
        val repository = EntityRepository().apply { entities.forEach(::add) }
        return DrawLayer(repository, DrawStateMachine(HistoryManager(), repository), scope) to repository
    }

    @Test
    fun withoutAnyZIndexPolygonsAreBelowLinesAndLinesBelowMarkersWhateverTheOrderTheyWereAddedIn() = runTest {
        val (layer, _) = layerWith(backgroundScope, marker("m"), line("l"), polygon("p"))
        delay(50)
        assertEquals(listOf("p", "l", "m"), layer.order())
    }

    @Test
    fun aHigherZIndexDrawsAShapeOverOneOfAnyKind() = runTest {
        val (layer, _) = layerWith(backgroundScope, polygon("p", z = 2), line("l"), marker("m"))
        delay(50)
        assertEquals(listOf("l", "m", "p"), layer.order(), "the polygon is above the line and the marker")
    }

    @Test
    fun aLowerZIndexDrawsAMarkerUnderAPolygon() = runTest {
        val (layer, _) = layerWith(backgroundScope, polygon("p"), marker("m", z = -1))
        delay(50)
        assertEquals(listOf("m", "p"), layer.order())
    }

    @Test
    fun shapesOfTheSameKindAndZIndexKeepTheOrderTheyWereAddedIn() = runTest {
        val (layer, _) = layerWith(backgroundScope, polygon("b"), polygon("a"), polygon("c"))
        delay(50)
        assertEquals(listOf("b", "a", "c"), layer.order())
    }

    @Test
    fun theZIndexRulesFirstAndTheKindOnlyBreaksTies() = runTest {
        val (layer, _) = layerWith(backgroundScope, marker("m1", z = 1), polygon("p1", z = 1), line("l0"), polygon("p2", z = 1))
        delay(50)
        assertEquals(listOf("l0", "p1", "p2", "m1"), layer.order())
    }

    @Test
    fun changingAZIndexReordersTheLayerWhileItIsRunning() = runTest {
        val (layer, repository) = layerWith(backgroundScope, polygon("a"), polygon("b"))
        delay(50)
        assertEquals(listOf("a", "b"), layer.order())

        repository.update(repository.entities.value.first { it.id.id == "a" }.withZIndex(3))
        delay(50)

        assertEquals(listOf("b", "a"), layer.order())
    }

    @Test
    fun aShapeThatDidNotChangeKeepsItsRenderableWhenOthersAreReordered() = runTest {
        val (layer, repository) = layerWith(backgroundScope, polygon("a"), polygon("b"), polygon("c"))
        delay(50)
        val cBefore = layer.single { it.getUserProperty<EntityId>(DrawLayer.ENTITY_ID_KEY)?.id == "c" }

        repository.update(repository.entities.value.first { it.id.id == "a" }.withZIndex(3))
        delay(50)

        assertSame(cBefore, layer.single { it.getUserProperty<EntityId>(DrawLayer.ENTITY_ID_KEY)?.id == "c" })
    }
}
