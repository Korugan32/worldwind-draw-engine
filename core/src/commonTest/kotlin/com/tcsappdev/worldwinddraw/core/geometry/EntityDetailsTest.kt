package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EntityDetailsTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))
    private val ring = listOf(
        Vertex(VertexId("a"), GeoPosition(0.0, 0.0)), Vertex(VertexId("b"), GeoPosition(0.0, 1.0)), Vertex(VertexId("c"), GeoPosition(1.0, 1.0)),
    )
    private val polygon = DrawnPolygon(EntityId("P"), style, ring)
    private val polyline = DrawnPolyline(EntityId("L"), style, ring)
    private val marker = DrawnMarker(EntityId("M"), style, GeoPosition(1.0, 1.0))

    @Test
    fun everyKindOfShapeStartsWithNoDescriptionAndAtTheBottomOfTheOrder() {
        for (entity in listOf<DrawableEntity>(polygon, polyline, marker)) {
            assertNull(entity.description, "${entity.id}")
            assertEquals(0, entity.zIndex, "${entity.id}")
        }
        assertNull(marker.label)
    }

    @Test
    fun aDescriptionCanBeSetOnEveryKindAndTouchesNothingElse() {
        assertEquals(polygon.copy(description = "a lake"), polygon.withDescription("a lake"))
        assertEquals(polyline.copy(description = "a road"), polyline.withDescription("a road"))
        assertEquals(marker.copy(description = "a pin"), marker.withDescription("a pin"))
        assertNull(polygon.withDescription("x").withDescription(null).description)
    }

    @Test
    fun everyKindOfShapeStartsWithNoProperties() {
        for (entity in listOf<DrawableEntity>(polygon, polyline, marker)) assertEquals(emptyMap(), entity.properties, "${entity.id}")
    }

    @Test
    fun propertiesCanBeSetOnEveryKindAndTouchNothingElse() {
        val data = mapOf("population" to "5000000", "code" to "TR")
        assertEquals(polygon.copy(properties = data), polygon.withProperties(data))
        assertEquals(polyline.copy(properties = data), polyline.withProperties(data))
        assertEquals(marker.copy(properties = data), marker.withProperties(data))
        assertEquals(emptyMap(), polygon.withProperties(data).withProperties(emptyMap()).properties)
    }

    @Test
    fun aZIndexCanBeSetOnEveryKindAndTouchesNothingElse() {
        assertEquals(polygon.copy(zIndex = 3), polygon.withZIndex(3))
        assertEquals(polyline.copy(zIndex = -2), polyline.withZIndex(-2))
        assertEquals(marker.copy(zIndex = 7), marker.withZIndex(7))
    }

    @Test
    fun aMarkerLabelNeedsTextAndASizeThatCanBeRead() {
        assertFailsWith<IllegalArgumentException> { MarkerLabel("") }
        assertFailsWith<IllegalArgumentException> { MarkerLabel("x", size = 5) }
        assertFailsWith<IllegalArgumentException> { MarkerLabel("x", size = 97) }
        MarkerLabel("x", size = 6)
        MarkerLabel("x", size = 96)
    }

    @Test
    fun aMarkerLabelIsWhiteWithABlackOutlineAboveTheIconUnlessToldOtherwise() {
        val label = MarkerLabel("Istanbul")
        assertEquals(ColorRgba(255, 255, 255), label.color)
        assertEquals(ColorRgba(0, 0, 0), label.outlineColor)
        assertEquals(14, label.size)
        assertEquals(LabelPosition.Above, label.position)
    }
}
