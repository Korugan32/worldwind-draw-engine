package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkHighlight
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPlacemark
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.animation.AnimatedProperty
import com.tcsappdev.worldwinddraw.core.animation.Animatable
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.Easing
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEngineState
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.render.Renderable
import earth.worldwind.shape.Path
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.Polygon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps a WWK [RenderableLayer] in sync with [entityRepository]'s live entity list, so a
 * host app can add one [DrawLayer] to its `WorldWindow` and never touch WWK renderables
 * directly.
 *
 * Builds a WWK renderable only for what is new or changed: on every emission each renderable is looked
 * up by what it was made from (the entity, whether it is selected or being edited, a live drag
 * preview, the handle or highlight style...) and kept when that is unchanged. So selecting a shape, going
 * into edit mode, deleting one or dragging a vertex touches only the shapes (and handles) involved, not
 * every shape on the map — a rebuilt WWK shape costs its tessellation and GPU buffers again, which
 * with hundreds of large polygons ran the heap out. The layer's content is then just the list of
 * those renderables in draw order (polygons, then polylines, then markers, then the draft, then the
 * editing handles), and it is only replaced when that list really differs.
 *
 * Every renderable is tagged with its originating [EntityId] via [ENTITY_ID_KEY] (see
 * [Renderable.putUserProperty]) — this is how `adapter.input.PickInputBridge` maps a WWK
 * pick result back to a [com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity].
 *
 * The entity [DrawMode.Select]/[DrawMode.Edit] names also gets a selection highlight (see
 * `HighlightMapper.kt`): a separate, non-pickable outline/halo renderable, so the entity's own
 * fill and style are never modified. These highlight renderables are deliberately NOT tagged
 * with [ENTITY_ID_KEY] — they are never pick results.
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
    handleStyle: HandleStyle = HandleStyle(),
    highlightStyle: HighlightStyle = HighlightStyle(),
) : RenderableLayer("Draw Layer"), Animatable {
    private val handleStyleFlow = MutableStateFlow(handleStyle)
    private val highlightStyleFlow = MutableStateFlow(highlightStyle)

    /**
     * How big and what colour the edit handles are (see [HandleStyle]). Safe to change at any
     * time — e.g. from a host app's settings screen — the layer rebuilds and redraws right away.
     */
    var handleStyle: HandleStyle
        get() = handleStyleFlow.value
        set(value) {
            handleStyleFlow.value = value
        }

    /** The colours of the selected/edited shape's outline (see [HighlightStyle]). Live-changeable, like [handleStyle]. */
    var highlightStyle: HighlightStyle
        get() = highlightStyleFlow.value
        set(value) {
            highlightStyleFlow.value = value
        }

    private val highlightAlphaSetters = mutableListOf<(Float) -> Unit>()

    private val pulse = AnimatedProperty(
        startValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        spec = AnimationSpec(PULSE_DURATION_MS, Easing { f -> f * f * (3f - 2f * f) }, RepeatMode.Reverse),
    ) { start, target, fraction -> start + (target - start) * fraction }

    // What has been built, kept from one update to the next so that only what changed is built again (see the class KDoc).
    private val bodies = Reuse<EntityId, Any, List<Renderable>>()
    private val highlights = Reuse<EntityId, Any, Highlight>()
    private val vertexHandles = Reuse<Pair<EntityId, VertexId>, Any, List<Renderable>>()
    private val midpointHandles = Reuse<Pair<EntityId, Int>, Any, List<Renderable>>()
    private var applied: List<Renderable> = emptyList()

    init {
        combine(entityRepository.entities, stateMachine.state, handleStyleFlow, highlightStyleFlow) { entities, engineState, handles, highlights ->
            Inputs(entities, engineState, handles, highlights)
        }
            .onEach { (entities, engineState, handleStyle, highlightStyle) ->
                update(entities, engineState, handleStyle, highlightStyle)
                onContentChanged()
            }
            .launchIn(scope)
    }

    /**
     * Brings the layer in line with the inputs, building a WWK renderable only for what is new or changed since
     * the last update. Every renderable is looked up by the thing it was made from (the entity, its style
     * state, the live drag preview, the handle style...) and kept if that is still the same; the layer then
     * just receives the list of renderables in their draw order, polygons below polylines below markers, then
     * the draft, then the editing handles.
     */
    private fun update(entities: List<DrawableEntity>, engineState: DrawEngineState, handleStyle: HandleStyle, highlightStyle: HighlightStyle) {
        bodies.begin()
        highlights.begin()
        vertexHandles.begin()
        midpointHandles.begin()
        highlightAlphaSetters.clear()
        val drawn = ArrayList<Renderable>()

        val mode = engineState.mode
        val editMode = mode as? DrawMode.Edit
        val newVertexDrag = editMode?.activeDrag as? DrawMode.Edit.ActiveDrag.NewVertex

        fun highlightOf(id: EntityId, shape: Any, build: (ColorRgba) -> Renderable, setAlpha: (Renderable, Float) -> Unit): Highlight? {
            val color = highlightStyle.colorFor(mode.styleStateFor(id)) ?: return null
            return highlights.get(id, Pair(shape, color)) {
                val renderable = build(color)
                // The pulse scales the colour's own opacity rather than replacing it, so a deliberately translucent highlight stays
                // translucent; it starts at the pulse's current alpha, so a highlight that appears mid-cycle doesn't flash to full.
                val baseAlpha = color.alpha / 255f
                setAlpha(renderable, baseAlpha * pulse.value)
                Highlight(renderable) { pulseAlpha -> setAlpha(renderable, baseAlpha * pulseAlpha) }
            }.also { highlightAlphaSetters += it.setAlpha }
        }

        entities.forEach { entity ->
            if (entity !is DrawnPolygon) return@forEach
            val renderVertices = livePreviewVertices(entity.id, entity.vertices, editMode, newVertexDrag)
            val shape = if (renderVertices === entity.vertices) entity else entity.copy(vertices = renderVertices)
            // While a vertex of this very polygon is being dragged the picture of an image fill is left out (see withImageFillBelow).
            val dragging = editMode?.entityId == entity.id && editMode.activeDrag != null
            drawn += bodies.get(entity.id, Pair(shape, dragging)) {
                // An image fill is a picture laid under the polygon, which then keeps its outline and stays pickable. If the
                // picture can't be produced the polygon gets a grey placeholder fill instead, so it isn't invisible.
                val fill = entity.style as? ShapeStyle.ImageFill
                val fillSurface = if (fill != null && !dragging) imageFillSurface(entity, fill) else null
                val styled = if (fill != null && !dragging && fillSurface == null) shape.copy(style = IMAGE_FILL_PLACEHOLDER) else shape
                listOfNotNull(fillSurface, styled.toWwkPolygon().tagged(entity.id))
            }
            highlightOf(entity.id, shape, { shape.toWwkHighlight(it) }) { r, a -> (r as Polygon).attributes.outlineColor.alpha = a }
                ?.let { drawn += it.renderable }
        }
        entities.forEach { entity ->
            if (entity !is DrawnPolyline) return@forEach
            val renderVertices = livePreviewVertices(entity.id, entity.vertices, editMode, newVertexDrag)
            val shape = if (renderVertices === entity.vertices) entity else entity.copy(vertices = renderVertices)
            highlightOf(entity.id, shape, { shape.toWwkHighlight(it) }) { r, a -> (r as Path).attributes.outlineColor.alpha = a }
                ?.let { drawn += it.renderable }
            drawn += bodies.get(entity.id, shape) { listOf(shape.toWwkPolyline().tagged(entity.id)) }
        }
        entities.forEach { entity ->
            if (entity !is DrawnMarker) return@forEach
            highlightOf(entity.id, entity, { entity.toWwkHighlight(it) }) { r, a -> (r as Placemark).attributes.imageColor.alpha = a }
                ?.let { drawn += it.renderable }
            // While the marker is being edited its own icon is not pickable: the move target (below) is what is grabbed,
            // and two pickable placemarks on one spot could let the wrong one win.
            val beingEdited = editMode?.entityId == entity.id
            drawn += bodies.get(entity.id, Pair(entity, beingEdited)) {
                listOf(entity.toWwkPlacemark().tagged(entity.id).apply { if (beingEdited) isPickEnabled = false })
            }
        }

        (mode as? DrawMode.Draw)?.let { draft -> drawn += draftPreviewRenderables(draft, handleStyle, highlightStyle) }

        if (editMode != null) {
            val editedEntity = entities.firstOrNull { it.id == editMode.entityId }
            if (editedEntity is DrawnMarker) {
                drawn += markerMoveTarget(editedEntity.position, editedEntity.size, handleStyle) {
                    putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                    putUserProperty(MARKER_MOVE_KEY, true)
                }
            }
            val (vertices, isClosed) = when (editedEntity) {
                is DrawnPolygon -> editedEntity.vertices to true
                is DrawnPolyline -> editedEntity.vertices to false
                is DrawnMarker, null -> null to false
            }
            if (vertices != null) {
                vertices.forEach { vertex ->
                    drawn += vertexHandles.get(editMode.entityId to vertex.id, Pair(vertex.position, handleStyle)) {
                        vertexHandleRenderables(vertex.position, handleStyle) {
                            putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                            putUserProperty(VERTEX_ID_KEY, vertex.id)
                        }
                    }
                }
                val skipEdgeIndex = newVertexDrag?.edgeIndex
                val edgeCount = if (isClosed) vertices.size else vertices.size - 1
                for (edgeIndex in 0 until edgeCount) {
                    if (edgeIndex == skipEdgeIndex) continue
                    val start = vertices[edgeIndex].position
                    val end = vertices[(edgeIndex + 1) % vertices.size].position
                    drawn += midpointHandles.get(editMode.entityId to edgeIndex, Triple(start, end, handleStyle)) {
                        midpointHandleRenderables(edgeMidpoint(start, end), handleStyle) {
                            putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                            putUserProperty(EDGE_INDEX_KEY, edgeIndex)
                        }
                    }
                }
            }
        }

        // Only touch the layer when its content really differs (the common case while something else changes, e.g. a pulse is not a change).
        val unchanged = drawn.size == applied.size && drawn.indices.all { drawn[it] === applied[it] }
        if (!unchanged) {
            clearRenderables()
            addAllRenderables(drawn)
            applied = drawn
        }
    }

    /**
     * Pulses the selection highlight's opacity (see [PULSE_DURATION_MS]/[PULSE_MIN_ALPHA]).
     * Register this layer with the host's `AnimationController` and tick that once per frame.
     * Does nothing — and, importantly, asks for no redraw — while nothing is highlighted, so an
     * idle map isn't redrawn every frame (WWK renders on demand, see [onContentChanged]).
     */
    override fun onTick(deltaMs: Long) {
        if (highlightAlphaSetters.isEmpty()) return
        pulse.onTick(deltaMs)
        highlightAlphaSetters.forEach { it(pulse.value) }
        onContentChanged()
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

    private fun <T : Renderable> T.tagged(entityId: EntityId): T = apply { putUserProperty(ENTITY_ID_KEY, entityId) }

    companion object {
        /** [Renderable.putUserProperty] key every renderable this layer creates is tagged with. */
        const val ENTITY_ID_KEY = "com.tcsappdev.worldwinddraw.entityId"

        /** [Renderable.putUserProperty] key a vertex-handle [Placemark] is additionally tagged with. */
        const val VERTEX_ID_KEY = "com.tcsappdev.worldwinddraw.vertexId"

        /** [Renderable.putUserProperty] key a midpoint-handle [Placemark] is additionally tagged with. */
        const val EDGE_INDEX_KEY = "com.tcsappdev.worldwinddraw.edgeIndex"

        /** [Renderable.putUserProperty] key the move target of the marker being edited is additionally tagged with. */
        const val MARKER_MOVE_KEY = "com.tcsappdev.worldwinddraw.markerMove"

        /**
         * Keys for the handles of a shape that is still being drawn (`DrawMode.Draw`). There is no
         * entity or vertex id yet, so these handles carry no [ENTITY_ID_KEY]; they are addressed by
         * index into the draft's points / edges instead.
         */
        const val DRAFT_POINT_INDEX_KEY = "com.tcsappdev.worldwinddraw.draftPointIndex"

        /** See [DRAFT_POINT_INDEX_KEY]. */
        const val DRAFT_EDGE_INDEX_KEY = "com.tcsappdev.worldwinddraw.draftEdgeIndex"

        /** Placeholder id for the rendering-only phantom vertex spliced in during a live [DrawMode.Edit.ActiveDrag.NewVertex] preview — never persisted, discarded on the next rebuild. */
        private val PHANTOM_VERTEX_ID = VertexId("__phantom__")

        /** Drawn in place of an image fill whose picture can't be produced (see [withImageFillBelow]). */
        private val IMAGE_FILL_PLACEHOLDER = ShapeStyle.SolidColor(ColorRgba(128, 128, 128, 128))

        private const val PULSE_DURATION_MS = 700L
        private const val PULSE_MIN_ALPHA = 0.2f

    }
}

/** A selection highlight, and how to set its alpha (see [DrawLayer.onTick]). */
private class Highlight(val renderable: Renderable, val setAlpha: (Float) -> Unit)

/**
 * Remembers what was built for each id during the last [begin]..now round and hands it back when asked again for the
 * same id with an equal [S] (the thing it was built from): [get] builds only on a miss. Whatever a round did not ask
 * for is dropped at the next [begin], so a deleted entity (or a handle that is gone) doesn't linger.
 */
private class Reuse<I, S, V> {
    private var previous = HashMap<I, Pair<S, V>>()
    private var current = HashMap<I, Pair<S, V>>()

    fun begin() {
        previous = current
        current = HashMap()
    }

    fun get(id: I, source: S, build: () -> V): V {
        val kept = previous[id]
        val value = if (kept != null && kept.first == source) kept.second else build()
        current[id] = Pair(source, value)
        return value
    }
}

private data class Inputs(
    val entities: List<DrawableEntity>,
    val engineState: DrawEngineState,
    val handles: HandleStyle,
    val highlights: HighlightStyle,
)
