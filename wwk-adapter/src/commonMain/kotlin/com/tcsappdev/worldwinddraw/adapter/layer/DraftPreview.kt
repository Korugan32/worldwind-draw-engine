package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.render.Renderable

/**
 * What to draw while a shape is still being sketched ([DrawMode.Draw]): the shape so far,
 * built from the *real* entity mappers so it is exactly what [DrawEvent.FinishDrawing][com.tcsappdev.worldwinddraw.core.state.DrawEvent.FinishDrawing]
 * will produce, plus the same editing handles a finished shape gets — a dot on every point
 * (drag it to move the point) and a "+" on the middle of every edge (drag it to add a point
 * there). Without the shape the draft was invisible until "Finish" — the only feedback was a
 * point count.
 *
 * Which shape shows depends on how many points there are, because a [DrawnPolygon] needs 3 and
 * a [DrawnPolyline] 2 (their own invariants — a draft is allowed to be smaller, a real entity
 * isn't):
 * - polygon, 3+ points: the polygon itself, in the draft's [DrawMode.Draw.style] with an
 *   outline in [HighlightStyle.editingColor]; with exactly 2: just the line between them.
 * - polyline, 2+ points: the polyline itself, in the draft's style.
 * - marker, or too few points for a line: only the dots.
 *
 * While a midpoint handle is being dragged ([DrawMode.Draw.ActiveDrag.InsertingPoint]) the shape
 * is drawn with that live position spliced in, and that edge's handle is hidden — the same live
 * preview editing a finished shape gets. The points themselves are not changed until the drop.
 *
 * The visible dots and shape are not pickable and carry no entity id; only the invisible
 * handle targets are pickable (see [vertexHandleRenderables]), tagged with
 * [DrawLayer.DRAFT_POINT_INDEX_KEY]/[DrawLayer.DRAFT_EDGE_INDEX_KEY]. So a tap anywhere else still
 * reaches the terrain and adds a point, and a draft is not something you can select.
 */
internal fun draftPreviewRenderables(mode: DrawMode.Draw, handleStyle: HandleStyle, highlightStyle: HighlightStyle): List<Renderable> {
    val insertion = mode.activeDrag as? DrawMode.Draw.ActiveDrag.InsertingPoint
    val shapePoints = mode.pendingVertices.toMutableList().apply {
        if (insertion != null) add(insertion.edgeIndex + 1, insertion.position)
    }
    val vertices = shapePoints.mapIndexed { i, position -> Vertex(VertexId("__draft_$i"), position) }
    val draftOutline = ShapeStyle.SolidColor(highlightStyle.editingColor)

    val shape: Renderable? = when (mode.entityType) {
        DrawableEntityType.Polygon -> when {
            vertices.size >= 3 -> DrawnPolygon(DRAFT_ID, mode.style, vertices, outlineStyle = draftOutline, outlineWidth = DRAFT_OUTLINE_WIDTH).toWwkPolygon()
            vertices.size == 2 -> DrawnPolyline(DRAFT_ID, draftOutline, vertices, lineWidth = DRAFT_OUTLINE_WIDTH).toWwkPolyline()
            else -> null
        }
        DrawableEntityType.Polyline ->
            if (vertices.size >= 2) DrawnPolyline(DRAFT_ID, mode.style, vertices).toWwkPolyline() else null
        DrawableEntityType.Marker -> null
    }
    shape?.isPickEnabled = false

    // Handles follow the draft's REAL points, so their indices match what the reducer addresses.
    val pointHandles = mode.pendingVertices.flatMapIndexed { index, position ->
        vertexHandleRenderables(position, handleStyle) { putUserProperty(DrawLayer.DRAFT_POINT_INDEX_KEY, index) }
    }
    val skippedEdge = insertion?.edgeIndex
    val edgeHandles = edgeMidpoints(mode.pendingVertices, mode.isClosed)
        .take(mode.edgeCount) // a marker has no edges, even with several points
        .filter { (edgeIndex, _) -> edgeIndex != skippedEdge }
        .flatMap { (edgeIndex, midpoint) ->
            midpointHandleRenderables(midpoint, handleStyle) { putUserProperty(DrawLayer.DRAFT_EDGE_INDEX_KEY, edgeIndex) }
        }

    return listOfNotNull(shape) + pointHandles + edgeHandles
}

private val DRAFT_ID = EntityId("__draft__")
private const val DRAFT_OUTLINE_WIDTH = 3f
