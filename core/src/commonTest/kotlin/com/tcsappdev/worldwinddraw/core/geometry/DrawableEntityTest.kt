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
    fun polygonRejectsNonPositiveOutlineWidth() {
        val vertices = listOf(
            Vertex(VertexId("TestVertex1"), GeoPosition(0.0, 0.0)),
            Vertex(VertexId("TestVertex2"), GeoPosition(1.0, 1.0)),
            Vertex(VertexId("TestVertex3"), GeoPosition(2.0, 2.0)),
        )

        assertFailsWith<IllegalArgumentException> {
            DrawnPolygon(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                vertices = vertices,
                outlineWidth = 0f,
            )
        }
        // Default (no outlineStyle, default outlineWidth) must NOT throw.
        DrawnPolygon(id = EntityId("TestEntity2"), style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)), vertices = vertices)
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
    fun polylineRejectsNonPositiveLineWidth() {
        val vertices = listOf(
            Vertex(VertexId("TestVertex1"), GeoPosition(0.0, 0.0)),
            Vertex(VertexId("TestVertex2"), GeoPosition(1.0, 1.0)),
        )

        assertFailsWith<IllegalArgumentException> {
            DrawnPolyline(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                vertices = vertices,
                lineWidth = 0f,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DrawnPolyline(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                vertices = vertices,
                lineWidth = -1f,
            )
        }
        // Default lineWidth must NOT throw.
        DrawnPolyline(id = EntityId("TestEntity2"), style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)), vertices = vertices)
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

    @Test
    fun markerRejectsNonPositiveSize() {
        val position = GeoPosition(0.0, 0.0)

        assertFailsWith<IllegalArgumentException> {
            DrawnMarker(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                position = position,
                size = 0f,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DrawnMarker(
                id = EntityId("TestEntity1"),
                style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                position = position,
                size = -1f,
            )
        }
        // Default size must NOT throw.
        DrawnMarker(id = EntityId("TestEntity2"), style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)), position = position)
    }

    @Test
    fun aMarkerIsNotTurnedUnlessItIsAndAnyFiniteTurnIsAccepted() {
        val style = ShapeStyle.SolidColor(ColorRgba(1, 2, 3))
        assertEquals(0f, DrawnMarker(EntityId("m"), style, GeoPosition(0.0, 0.0)).rotation)
        assertEquals(450f, DrawnMarker(EntityId("m"), style, GeoPosition(0.0, 0.0), rotation = 450f).rotation)
        assertFailsWith<IllegalArgumentException> { DrawnMarker(EntityId("m"), style, GeoPosition(0.0, 0.0), rotation = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { DrawnMarker(EntityId("m"), style, GeoPosition(0.0, 0.0), rotation = Float.POSITIVE_INFINITY) }
    }
}
