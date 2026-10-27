package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How a polygon of several rings is drawn and brought to a level of detail (the handles are in the jvm tests: they need Placemarks). */
class DrawLayerRingsTest {
    private val id = EntityId("M")

    private fun circle(prefix: String, count: Int, radius: Double, lat: Double, lon: Double) = List(count) { i ->
        val angle = 2 * kotlin.math.PI * i / count
        Vertex(VertexId("$prefix$i"), GeoPosition(lat + radius * kotlin.math.sin(angle), lon + radius * kotlin.math.cos(angle)))
    }

    private fun polygon(vararg rings: List<Vertex>) = DrawnPolygon(id, ShapeStyle.SolidColor(ColorRgba(0, 0, 0)), rings.first(), extraRings = rings.drop(1))

    private fun DrawLayer.boundaries() = (single { it is Polygon && it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) } as Polygon).let { p -> (0 until p.boundaryCount).map { p.getBoundary(it).size } }

    private fun around(lat: Double, lon: Double) = GeoBounds(lat - 30, lat + 30, lon - 30, lon + 30)

    @Test
    fun aPolygonOfSeveralRingsIsOneWwkPolygonWithAboundaryForEach() = runTest {
        val repository = EntityRepository()
        repository.add(polygon(circle("o", 8, 5.0, 0.0, 0.0), circle("h", 5, 1.0, 0.0, 0.0), circle("p", 6, 1.0, 20.0, 20.0)))
        val layer = DrawLayer(repository, DrawStateMachine(HistoryManager(), repository), backgroundScope)
        delay(50)

        assertEquals(1, layer.count { it is Polygon && it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) })
        assertEquals(3, layer.boundaries().size)
    }

    @Test
    fun aCoarseLevelSimplifiesEveryBigRingAndLeavesTheSmallOnesWhole() = runTest {
        val repository = EntityRepository()
        repository.add(polygon(circle("o", 200, 20.0, 0.0, 0.0), circle("h", 8, 2.0, 0.0, 0.0), circle("p", 200, 3.0, 40.0, 40.0)))
        val layer = DrawLayer(repository, DrawStateMachine(HistoryManager(), repository), backgroundScope)
        delay(50)
        assertEquals(listOf(200, 8, 200), layer.boundaries())

        layer.applyLod(LodLevel.forTolerance(300_000.0, LodLevel.NONE))
        val coarse = layer.boundaries()
        assertTrue(coarse[0] < 100 && coarse[2] < 100, "the big rings lose points, got $coarse")
        assertEquals(8, coarse[1], "a ring with few points is drawn whole")

        layer.applyLod(LodLevel.NONE)
        assertEquals(listOf(200, 8, 200), layer.boundaries())
    }

    @Test
    fun aShapeIsInViewWhenAnyOfItsRingsIs() = runTest {
        val repository = EntityRepository()
        // The first ring is in America, a second part in Europe.
        repository.add(polygon(circle("a", 200, 10.0, 40.0, -100.0), circle("e", 200, 10.0, 50.0, 10.0)))
        val layer = DrawLayer(repository, DrawStateMachine(HistoryManager(), repository), backgroundScope)
        delay(50)

        layer.applyLod(6, view = around(50.0, 10.0)) // the camera is over Europe

        val (america, europe) = layer.boundaries()
        assertTrue(america < 200 && europe < 200, "the part that is in view brings the whole shape to the level, got $america and $europe")
    }

    @Test
    fun aShapeWithNoRingInViewIsLeftAlone() = runTest {
        val repository = EntityRepository()
        repository.add(polygon(circle("a", 200, 10.0, 40.0, -100.0), circle("e", 200, 10.0, 50.0, 10.0)))
        val layer = DrawLayer(repository, DrawStateMachine(HistoryManager(), repository), backgroundScope)
        delay(50)
        val before = layer.single { it is Polygon && it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) }

        layer.applyLod(6, view = around(-30.0, 120.0)) // the camera is over Australia

        assertSame(before, layer.single { it is Polygon && it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) })
    }
}
