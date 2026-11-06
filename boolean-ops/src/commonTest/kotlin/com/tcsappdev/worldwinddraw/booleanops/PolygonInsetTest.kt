package com.tcsappdev.worldwinddraw.booleanops

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.InsetEngine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolygonInsetTest {
    // About 111 m by 111 m at the equator (0.001 degrees each way).
    private val square = DrawnPolygon(
        EntityId("sq"), ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
        listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 0.001), GeoPosition(0.001, 0.001), GeoPosition(0.001, 0.0))
            .mapIndexed { i, p -> Vertex(VertexId("c$i"), p) },
    )

    @Test
    fun aSquareGetsAnotherSquareInsideIt() {
        val rings = PolygonOverlay.inset(square, 10.0)
        assertEquals(1, rings.size)
        assertEquals(4, rings.single().size)
        // every corner moved in by about 10 m: 0.00009 degrees
        for (v in rings.single()) {
            assertTrue(v.position.latitude in 0.00008..0.00093 && v.position.longitude in 0.00008..0.00093, "${v.position}")
        }
    }

    @Test
    fun theEngineIsAnInsetEngineToo() {
        val engine: InsetEngine = JtsOverlayEngine()
        var next = 0
        assertEquals(1, engine.inset(square, 10.0) { VertexId("n${next++}") }.size)
    }

    @Test
    fun anIdMakerThatKeepsGivingTheSameIdIsRefusedInsteadOfLoopingForEver() {
        assertFailsWith<IllegalArgumentException> { PolygonOverlay.inset(square, 10.0) { VertexId("same") } }
    }

    @Test
    fun tooBigADistanceLeavesNothing() {
        assertEquals(emptyList(), PolygonOverlay.inset(square, 100.0))
    }

    @Test
    fun aNegativeOrNotANumberDistanceIsRefused() {
        assertFailsWith<IllegalArgumentException> { PolygonOverlay.inset(square, -1.0) }
        assertFailsWith<IllegalArgumentException> { PolygonOverlay.inset(square, Double.NaN) }
    }
}
