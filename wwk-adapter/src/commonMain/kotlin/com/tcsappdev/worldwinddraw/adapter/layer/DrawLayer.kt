package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.toGeoPosition
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPlacemark
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.Position
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.render.Color
import earth.worldwind.render.Renderable
import earth.worldwind.shape.PathType
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.PlacemarkAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps a WWK [RenderableLayer] in sync with [entityRepository]'s live entity list, so a
 * host app can add one [DrawLayer] to its `WorldWindow` and never touch WWK renderables
 * directly.
 *
 * Rebuilds the whole layer on every emission rather than diffing old vs. new state. That's
 * the simplest correct thing, and entity lists here are small (shapes a user drew, not bulk
 * data), so the cost is negligible. It does mean a live vertex drag rebuilds the entire
 * layer on every `DragMove` (every entity re-mapped, not just the one being edited) — fine
 * at this scale, but the first thing to revisit if dragging ever feels janky on very large
 * entity lists.
 *
 * Every renderable is tagged with its originating [EntityId] via [ENTITY_ID_KEY] (see
 * [Renderable.putUserProperty]) — this is how `adapter.input.PickInputBridge` maps a WWK
 * pick result back to a [com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity].
 *
 * While [stateMachine] reports [DrawMode.Edit] for a polygon/polyline, this layer also adds:
 * - one small, individually pickable+[Movable][earth.worldwind.shape.Movable] "vertex
 *   handle" [Placemark] per existing vertex, tagged with both [ENTITY_ID_KEY] and
 *   [VERTEX_ID_KEY] — dragging one moves that vertex (`PickInputBridge` resolves exactly
 *   which one from WWK's pick result, no geometry heuristic needed).
 * - one smaller, dimmer "midpoint handle" per edge, tagged with [ENTITY_ID_KEY] and
 *   [EDGE_INDEX_KEY] (not [VERTEX_ID_KEY] — it isn't a real vertex yet). Dragging one grows
 *   the shape a new vertex there, the same interaction Mapbox GL Draw/Google My Maps use.
 *   While one is actively being dragged ([DrawMode.Edit.ActiveDrag.NewVertex]), the shape's
 *   outline is rendered with that live position spliced in (so it visibly reshapes as you
 *   drag) and the midpoint handle for that specific edge is suppressed — the handle WWK is
 *   already dragging is standing in for it until `DragEnd` actually commits the insert.
 *
 * [onContentChanged] must be wired to the host `WorldWindow`'s own redraw request (e.g.
 * `worldWindow::requestRedraw`). WWK's Android surface renders on demand
 * (`RENDERMODE_WHEN_DIRTY`) and only redraws itself automatically in response to its own
 * gesture pipeline (pan/zoom/an active drag). A change that lands outside of that — e.g.
 * undo/redo, or any other host-triggered repository mutation — updates this layer's
 * renderables correctly but leaves a stale frame on screen until something else happens to
 * ask for a redraw. This was a real bug caught by visual verification, not something WWK
 * documents: dragging a vertex looked correct (the drag gesture itself keeps requesting
 * redraws), but hitting Undo right after silently changed the data with no visible effect
 * until an unrelated touch forced a redraw.
 */
class DrawLayer(
    entityRepository: EntityRepository,
    stateMachine: DrawStateMachine,
    scope: CoroutineScope,
    private val onContentChanged: () -> Unit = {},
) : RenderableLayer("Draw Layer") {
    init {
        combine(entityRepository.entities, stateMachine.state) { entities, engineState -> entities to engineState }
            .onEach { (entities, engineState) ->
                clearRenderables()
                val editMode = engineState.mode as? DrawMode.Edit
                val newVertexDrag = editMode?.activeDrag as? DrawMode.Edit.ActiveDrag.NewVertex

                entities.filterIsInstance<DrawnPolygon>().forEach { polygon ->
                    val renderVertices = livePreviewVertices(polygon.id, polygon.vertices, editMode, newVertexDrag)
                    addRenderable(polygon.copy(vertices = renderVertices).toWwkPolygon().tagged(polygon.id))
                }
                entities.filterIsInstance<DrawnPolyline>().forEach { polyline ->
                    val renderVertices = livePreviewVertices(polyline.id, polyline.vertices, editMode, newVertexDrag)
                    addRenderable(polyline.copy(vertices = renderVertices).toWwkPolyline().tagged(polyline.id))
                }
                entities.filterIsInstance<DrawnMarker>().forEach { addRenderable(it.toWwkPlacemark().tagged(it.id)) }

                if (editMode != null) {
                    val editedEntity = entities.firstOrNull { it.id == editMode.entityId }
                    val (vertices, isClosed) = when (editedEntity) {
                        is DrawnPolygon -> editedEntity.vertices to true
                        is DrawnPolyline -> editedEntity.vertices to false
                        is DrawnMarker, null -> null to false
                    }
                    if (vertices != null) {
                        vertices.forEach { vertex -> addRenderable(vertexHandle(editMode.entityId, vertex)) }
                        val skipEdgeIndex = newVertexDrag?.edgeIndex
                        edgeMidpoints(vertices, isClosed).forEach { (edgeIndex, midpoint) ->
                            if (edgeIndex != skipEdgeIndex) addRenderable(midpointHandle(editMode.entityId, edgeIndex, midpoint))
                        }
                    }
                }
                onContentChanged()
            }
            .launchIn(scope)
    }

    /**
     * [vertices] with [drag]'s live position spliced in at its edge — but only when [drag]
     * belongs to [entityId] (an in-progress midpoint drag on a *different* entity must not
     * leak a phantom point into this one). Returns [vertices] unchanged otherwise.
     */
    private fun livePreviewVertices(
        entityId: EntityId,
        vertices: List<Vertex>,
        editMode: DrawMode.Edit?,
        drag: DrawMode.Edit.ActiveDrag.NewVertex?,
    ): List<Vertex> {
        if (drag == null || editMode == null || editMode.entityId != entityId) return vertices
        val phantom = Vertex(PHANTOM_VERTEX_ID, drag.position)
        return vertices.toMutableList().apply { add(drag.edgeIndex + 1, phantom) }
    }

    /**
     * Every edge's index paired with its midpoint. [isClosed] adds the wraparound edge (last
     * vertex back to the first) for a polygon; a polyline has one fewer edge than vertices.
     *
     * Uses WWK's own [Position.interpolateAlongPath] (with [PathType.GREAT_CIRCLE] — the
     * default `pathType` [PolygonMapper][com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon]/
     * [PolylineMapper][com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline] leave every
     * shape at) rather than a plain lat/lon average: WWK renders an edge as a great-circle
     * arc, which is *not* the same curve a naive planar midpoint sits on — over a long edge
     * the two visibly diverge, so a `core`-side pure-math midpoint would float off the
     * actual rendered line. This is why the computation lives here in `wwk-adapter` and not
     * in `core.geometry.GeometryOps`: it only stays correct by depending on the exact same
     * WWK math the shape itself is drawn with.
     */
    private fun edgeMidpoints(vertices: List<Vertex>, isClosed: Boolean): List<Pair<Int, GeoPosition>> {
        val edgeCount = if (isClosed) vertices.size else vertices.size - 1
        val scratch = Position()
        return (0 until edgeCount).map { i ->
            val start = vertices[i].position.toWwkPosition()
            val end = vertices[(i + 1) % vertices.size].position.toWwkPosition()
            start.interpolateAlongPath(end, PathType.GREAT_CIRCLE, 0.5, scratch)
            i to scratch.toGeoPosition()
        }
    }

    private fun <T : Renderable> T.tagged(entityId: EntityId): T = apply { putUserProperty(ENTITY_ID_KEY, entityId) }

    private fun vertexHandle(entityId: EntityId, vertex: Vertex): Placemark {
        val attributes = PlacemarkAttributes().apply {
            imageColor = VERTEX_HANDLE_COLOR
            imageScale = VERTEX_HANDLE_SIZE
        }
        return Placemark(vertex.position.toWwkPosition(), attributes).apply {
            altitudeMode = AltitudeMode.CLAMP_TO_GROUND
            putUserProperty(ENTITY_ID_KEY, entityId)
            putUserProperty(VERTEX_ID_KEY, vertex.id)
        }
    }

    private fun midpointHandle(entityId: EntityId, edgeIndex: Int, position: GeoPosition): Placemark {
        val attributes = PlacemarkAttributes().apply {
            imageColor = MIDPOINT_HANDLE_COLOR
            imageScale = MIDPOINT_HANDLE_SIZE
        }
        return Placemark(position.toWwkPosition(), attributes).apply {
            altitudeMode = AltitudeMode.CLAMP_TO_GROUND
            putUserProperty(ENTITY_ID_KEY, entityId)
            putUserProperty(EDGE_INDEX_KEY, edgeIndex)
        }
    }

    companion object {
        /** [Renderable.putUserProperty] key every renderable this layer creates is tagged with. */
        const val ENTITY_ID_KEY = "com.tcsappdev.worldwinddraw.entityId"

        /** [Renderable.putUserProperty] key a vertex-handle [Placemark] is additionally tagged with. */
        const val VERTEX_ID_KEY = "com.tcsappdev.worldwinddraw.vertexId"

        /** [Renderable.putUserProperty] key a midpoint-handle [Placemark] is additionally tagged with. */
        const val EDGE_INDEX_KEY = "com.tcsappdev.worldwinddraw.edgeIndex"

        /** Placeholder id for the rendering-only phantom vertex spliced in during a live [DrawMode.Edit.ActiveDrag.NewVertex] preview — never persisted, discarded on the next rebuild. */
        private val PHANTOM_VERTEX_ID = VertexId("__phantom__")

        private val VERTEX_HANDLE_COLOR = Color(1f, 1f, 1f, 1f)
        private const val VERTEX_HANDLE_SIZE = 14.0
        private val MIDPOINT_HANDLE_COLOR = Color(1f, 1f, 1f, 0.5f)
        private const val MIDPOINT_HANDLE_SIZE = 9.0
    }
}
