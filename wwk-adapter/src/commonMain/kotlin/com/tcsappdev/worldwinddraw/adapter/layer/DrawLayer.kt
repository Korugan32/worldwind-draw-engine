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

    init {
        combine(entityRepository.entities, stateMachine.state, handleStyleFlow, highlightStyleFlow) { entities, engineState, handles, highlights ->
            Inputs(entities, engineState, handles, highlights)
        }
            .onEach { (entities, engineState, handles, highlights) ->
                clearRenderables()
                highlightAlphaSetters.clear()
                val editMode = engineState.mode as? DrawMode.Edit
                val newVertexDrag = editMode?.activeDrag as? DrawMode.Edit.ActiveDrag.NewVertex

                entities.filterIsInstance<DrawnPolygon>().forEach { polygon ->
                    val renderVertices = livePreviewVertices(polygon.id, polygon.vertices, editMode, newVertexDrag)
                    val rendered = withImageFillBelow(polygon.copy(vertices = renderVertices), polygon, editMode)
                    addRenderable(rendered.toWwkPolygon().tagged(polygon.id))
                    highlights.colorFor(engineState.mode.styleStateFor(polygon.id))?.let { color ->
                        addHighlight(rendered.toWwkHighlight(color), color) { shape, alpha -> shape.attributes.outlineColor.alpha = alpha }
                    }
                }
                entities.filterIsInstance<DrawnPolyline>().forEach { polyline ->
                    val renderVertices = livePreviewVertices(polyline.id, polyline.vertices, editMode, newVertexDrag)
                    val rendered = polyline.copy(vertices = renderVertices)
                    highlights.colorFor(engineState.mode.styleStateFor(polyline.id))?.let { color ->
                        addHighlight(rendered.toWwkHighlight(color), color) { shape, alpha -> shape.attributes.outlineColor.alpha = alpha }
                    }
                    addRenderable(rendered.toWwkPolyline().tagged(polyline.id))
                }
                entities.filterIsInstance<DrawnMarker>().forEach { marker ->
                    highlights.colorFor(engineState.mode.styleStateFor(marker.id))?.let { color ->
                        addHighlight(marker.toWwkHighlight(color), color) { placemark, alpha -> placemark.attributes.imageColor.alpha = alpha }
                    }
                    // While the marker is being edited its own icon is not pickable: the move target above it
                    // (below) is what is grabbed, and two pickable placemarks on one spot could let the wrong one win.
                    val beingEdited = editMode?.entityId == marker.id
                    addRenderable(marker.toWwkPlacemark().tagged(marker.id).apply { if (beingEdited) isPickEnabled = false })
                }

                (engineState.mode as? DrawMode.Draw)?.let { draft ->
                    draftPreviewRenderables(draft, handles, highlights).forEach { addRenderable(it) }
                }

                if (editMode != null) {
                    val editedEntity = entities.firstOrNull { it.id == editMode.entityId }
                    val (vertices, isClosed) = when (editedEntity) {
                        is DrawnPolygon -> editedEntity.vertices to true
                        is DrawnPolyline -> editedEntity.vertices to false
                        is DrawnMarker, null -> null to false
                    }
                    if (editedEntity is DrawnMarker) {
                        addRenderable(
                            markerMoveTarget(editedEntity.position, editedEntity.size, handles) {
                                putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                                putUserProperty(MARKER_MOVE_KEY, true)
                            }
                        )
                    }
                    if (vertices != null) {
                        vertices.forEach { vertex ->
                            vertexHandleRenderables(vertex.position, handles) {
                                putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                                putUserProperty(VERTEX_ID_KEY, vertex.id)
                            }.forEach { addRenderable(it) }
                        }
                        val skipEdgeIndex = newVertexDrag?.edgeIndex
                        edgeMidpoints(vertices.map { it.position }, isClosed).forEach { (edgeIndex, midpoint) ->
                            if (edgeIndex != skipEdgeIndex) {
                                midpointHandleRenderables(midpoint, handles) {
                                    putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                                    putUserProperty(EDGE_INDEX_KEY, edgeIndex)
                                }.forEach { addRenderable(it) }
                            }
                        }
                    }
                }
                onContentChanged()
            }
            .launchIn(scope)
    }

    /**
     * For a polygon with a [ShapeStyle.ImageFill], adds the picture (clipped to the shape) below it and
     * returns [rendered] unchanged; if the picture can't be produced, returns [rendered] with a grey
     * placeholder fill in its place so the shape isn't invisible. Any other polygon is returned as is.
     *
     * While a vertex of this very polygon is being dragged the picture is left out: it is cut to the
     * shape, so it would have to be produced again on every step of the drag (tens of milliseconds
     * each); it comes back as soon as the drag ends and the shape settles.
     */
    private fun withImageFillBelow(rendered: DrawnPolygon, original: DrawnPolygon, editMode: DrawMode.Edit?): DrawnPolygon {
        val fill = original.style as? ShapeStyle.ImageFill ?: return rendered
        if (editMode?.entityId == original.id && editMode.activeDrag != null) return rendered
        val surface = imageFillSurface(original, fill)
        if (surface == null) return rendered.copy(style = IMAGE_FILL_PLACEHOLDER)
        addRenderable(surface)
        return rendered
    }

    /**
     * Adds [highlight] and remembers how to set its alpha, so [onTick] can pulse it without
     * rebuilding the layer. The pulse scales the colour's own opacity ([color]'s alpha) rather
     * than replacing it, so a deliberately translucent highlight stays translucent. Starts at
     * the pulse's current alpha, so a highlight that appears mid-cycle doesn't flash back to
     * full opacity for a frame.
     */
    private fun <T : Renderable> addHighlight(highlight: T, color: ColorRgba, setAlpha: (T, Float) -> Unit) {
        addRenderable(highlight)
        val baseAlpha = color.alpha / 255f
        setAlpha(highlight, baseAlpha * pulse.value)
        highlightAlphaSetters += { pulseAlpha -> setAlpha(highlight, baseAlpha * pulseAlpha) }
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

private data class Inputs(
    val entities: List<DrawableEntity>,
    val engineState: DrawEngineState,
    val handles: HandleStyle,
    val highlights: HighlightStyle,
)
