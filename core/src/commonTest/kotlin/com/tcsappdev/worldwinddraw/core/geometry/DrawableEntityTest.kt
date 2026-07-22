package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DrawableEntityTest {
    @Test
    fun polygonRequiresAtLeastThreeVertices() {
        // 2 vertices: below the minimum, must throw.
        assertFailsWith<IllegalArgumentException> {
            DrawnPolygon(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                vertices = listOf(
                    Vertex(
                        id = VertexId("TestVertex1"),
                        position = GeoPosition(0.0, 0.0),
                    ),
                    Vertex(
                        id = VertexId("TestVertex2"),
                        position = GeoPosition(0.0, 0.0),
                    ),
                )
            )
        }
        // 3 vertices: exactly the minimum, must NOT throw.
        DrawnPolygon(
            id = EntityId("TestEntity2"),
            style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
            vertices = listOf(
                Vertex(
                    id = VertexId("TestVertex1"),
                    position = GeoPosition(0.0, 0.0),
                ),
                Vertex(
                    id = VertexId("TestVertex2"),
                    position = GeoPosition(1.0, 1.0),
                ),
                Vertex(
                    id = VertexId("TestVertex3"),
                    position = GeoPosition(2.0, 2.0),
                ),
            )
        )
    }

    @Test
    fun polylineRequiresAtLeastTwoVertices() {
        // 1 vertex: below the minimum, must throw.
        assertFailsWith<IllegalArgumentException> {
            DrawnPolyline(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                vertices = listOf(
                    Vertex(
                        id = VertexId("TestVertex1"),
                        position = GeoPosition(0.0, 0.0),
                    ),
                )
            )
        }
        // 2 vertices: exactly the minimum, must NOT throw.
        DrawnPolyline(
            id = EntityId("TestEntity2"),
            style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
            vertices = listOf(
                Vertex(
                    id = VertexId("TestVertex1"),
                    position = GeoPosition(0.0, 0.0),
                ),
                Vertex(
                    id = VertexId("TestVertex2"),
                    position = GeoPosition(1.0, 1.0),
                ),
            )
        )
    }

    @Test
    fun markerHoldsItsPosition() {
        val position = GeoPosition(0.0, 0.0)
        val marker = DrawnMarker(
            id = EntityId("TestEntity1"),
            style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
            position = position
        )
        assertEquals(
            position, marker.position
        )
    }
}