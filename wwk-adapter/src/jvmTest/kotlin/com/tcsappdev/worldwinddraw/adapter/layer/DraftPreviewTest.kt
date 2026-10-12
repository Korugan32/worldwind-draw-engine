package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.render.Renderable
import earth.worldwind.shape.Path
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// jvmTest, not commonTest: the dots are Placemarks, and PlacemarkAttributes() can't be built in
// Android's plain host unit tests (see StyleAttributesMapperTest).
class DraftPreviewTest {
    private fun points(n: Int) = (0 until n).map { GeoPosition(it.toDouble(), it * 2.0) }

    private fun preview(type: DrawableEntityType, pointCount: Int, style: ShapeStyle? = null) =
        draftPreviewRenderables(
            if (style != null) DrawMode.Draw(type, points(pointCount), style) else DrawMode.Draw(type, points(pointCount)),
            HandleStyle(),
            HighlightStyle(),
        )

    @Test
    fun nothingToDrawBeforeTheFirstPoint() {
        assertTrue(preview(DrawableEntityType.Polygon, 0).isEmpty())
    }

    // Each point and each edge handle is TWO placemarks: a visible dot plus an invisible target.
    private fun List<Renderable>.dots() = filterIsInstance<Placemark>().filter { !it.isPickEnabled }

    @Test
    fun polygonDraftGrowsFromADotToALineToAFilledPolygon() {
        val one = preview(DrawableEntityType.Polygon, 1)
        assertEquals(0, one.count { it is Path || it is Polygon }, "1 point: no shape yet")
        assertEquals(1, one.dots().size)

        val two = preview(DrawableEntityType.Polygon, 2)
        assertEquals(1, two.filterIsInstance<Path>().size, "2 points: the line between them")
        assertEquals(2 + 1, two.dots().size, "2 point dots + 1 midpoint dot")

        val three = preview(DrawableEntityType.Polygon, 3)
        assertEquals(1, three.filterIsInstance<Polygon>().size, "3 points: the polygon itself")
        assertEquals(0, three.filterIsInstance<Path>().size)
        assertEquals(3 + 3, three.dots().size, "3 point dots + 3 midpoint dots (the polygon closes)")
    }

    @Test
    fun polygonDraftIsFilledWithTheDraftsOwnStyle() {
        val polygon = preview(DrawableEntityType.Polygon, 3, ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 255)))
            .filterIsInstance<Polygon>().single()

        assertEquals(1f, polygon.attributes.interiorColor.red)
        assertEquals(0f, polygon.attributes.interiorColor.blue)
    }

    @Test
    fun polylineDraftNeedsTwoPointsForALineAndUsesTheDraftsStyle() {
        assertEquals(0, preview(DrawableEntityType.Polyline, 1).filterIsInstance<Path>().size)

        val path = preview(DrawableEntityType.Polyline, 2, ShapeStyle.SolidColor(ColorRgba(0, 255, 0, 255)))
            .filterIsInstance<Path>().single()
        assertEquals(1f, path.attributes.outlineColor.green)
    }

    @Test
    fun draftOutlineAndDotsFollowTheGivenStyles() {
        val mode = DrawMode.Draw(DrawableEntityType.Polygon, points(3))
        val renderables = draftPreviewRenderables(
            mode,
            HandleStyle(fillColor = ColorRgba(0, 0, 255)),
            HighlightStyle(editingColor = ColorRgba(255, 0, 255)),
        )

        val outline = renderables.filterIsInstance<Polygon>().single().attributes.outlineColor
        assertEquals(1f, outline.red)
        assertEquals(1f, outline.blue)
        assertEquals(0f, outline.green)
        // No handle image on the JVM, so a dot is a plain square in the handle's fill colour.
        val dot = renderables.filterIsInstance<Placemark>().first().attributes.imageColor
        assertEquals(1f, dot.blue)
        assertEquals(0f, dot.red)
    }

    @Test
    fun markerDraftIsOnlyPointHandlesWithNoEdges() {
        val markers = preview(DrawableEntityType.Marker, 2)

        assertTrue(markers.all { it is Placemark })
        assertEquals(2, markers.dots().size, "no edges, so no midpoint handles")
        assertEquals(2, markers.filter { it.hasUserProperty(DrawLayer.DRAFT_POINT_INDEX_KEY) }.size)
        assertEquals(0, markers.count { it.hasUserProperty(DrawLayer.DRAFT_EDGE_INDEX_KEY) })
    }

    @Test
    fun onlyTheInvisibleHandleTargetsArePickableAndTheyCarryTheDraftIndices() {
        val renderables = preview(DrawableEntityType.Polygon, 3)

        // The shape and the visible dots are never pickable: a tap there still reaches the terrain.
        val pickable = renderables.filter { it.isPickEnabled }
        assertTrue(renderables.filter { !it.isPickEnabled }.isNotEmpty())
        assertEquals(6, pickable.size, "3 point targets + 3 edge targets")
        assertEquals(listOf(0, 1, 2), pickable.mapNotNull { it.getUserProperty<Int>(DrawLayer.DRAFT_POINT_INDEX_KEY) })
        assertEquals(listOf(0, 1, 2), pickable.mapNotNull { it.getUserProperty<Int>(DrawLayer.DRAFT_EDGE_INDEX_KEY) })
        // Never mistaken for a real entity.
        assertTrue(renderables.none { it.hasUserProperty(DrawLayer.ENTITY_ID_KEY) })
        pickable.forEach { assertEquals(0f, (it as Placemark).attributes.imageColor.alpha, "transparent: draws nothing") }
    }

    @Test
    fun whileAMidpointIsBeingDraggedTheShapeGrowsAPointAndThatEdgesHandleIsHidden() {
        val pointsList = points(3)
        val dragging = DrawMode.Draw(
            DrawableEntityType.Polygon,
            pointsList,
            activeDrag = DrawMode.Draw.ActiveDrag.InsertingPoint(edgeIndex = 0, position = GeoPosition(9.0, 9.0)),
        )

        val renderables = draftPreviewRenderables(dragging, HandleStyle(), HighlightStyle())

        // Still 3 REAL points (nothing is inserted until the drop), but only 2 edge handles: edge 0's is being dragged.
        assertEquals(3, renderables.count { it.hasUserProperty(DrawLayer.DRAFT_POINT_INDEX_KEY) })
        assertEquals(listOf(1, 2), renderables.mapNotNull { it.getUserProperty<Int>(DrawLayer.DRAFT_EDGE_INDEX_KEY) })
        assertEquals(1, renderables.filterIsInstance<Polygon>().size, "the shape is drawn with the live point spliced in")
    }

    @Test
    fun layerShowsTheDraftWhileDrawingAndRemovesItWhenDrawingStops() = runTest {
        val repository = EntityRepository()
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        val layer = DrawLayer(repository, stateMachine, backgroundScope)
        delay(50)
        assertEquals(0, layer.count)

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList())))
        listOf(GeoPosition(0.0, 0.0), GeoPosition(1.0, 0.0), GeoPosition(1.0, 1.0)).forEach {
            stateMachine.onEvent(DrawEvent.Click(it, hitEntityId = null))
        }
        delay(50)
        // polygon + (3 points + 3 edges) * (dot + invisible target)
        assertEquals(1 + 12, layer.count, "the draft: its shape and its editing handles")

        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        delay(50)
        assertFalse(layer.count > 0, "an abandoned draft leaves nothing behind")
    }
}
