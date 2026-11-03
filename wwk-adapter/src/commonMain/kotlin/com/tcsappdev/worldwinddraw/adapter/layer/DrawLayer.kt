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
import com.tcsappdev.worldwinddraw.core.geometry.PathSimplifier
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEngineState
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleDetail
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.layer.RenderableLayer
import earth.worldwind.render.RenderContext
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
public class DrawLayer(
    entityRepository: EntityRepository,
    stateMachine: DrawStateMachine,
    scope: CoroutineScope,
    private val onContentChanged: () -> Unit = {},
    handleStyle: HandleStyle = HandleStyle(),
    highlightStyle: HighlightStyle = HighlightStyle(),
    buildOnFirstFrame: Boolean = false,
) : RenderableLayer("Draw Layer"), Animatable {
    private val handleStyleFlow = MutableStateFlow(handleStyle)
    private val highlightStyleFlow = MutableStateFlow(highlightStyle)

    /**
     * How big and what colour the edit handles are (see [HandleStyle]). Safe to change at any
     * time — e.g. from a host app's settings screen — the layer rebuilds and redraws right away.
     */
    public var handleStyle: HandleStyle
        get() = handleStyleFlow.value
        set(value) {
            handleStyleFlow.value = value
        }

    /** The colours of the selected/edited shape's outline (see [HighlightStyle]). Live-changeable, like [handleStyle]. */
    public var highlightStyle: HighlightStyle
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
    // Midpoint handles are per (entity, ring, edge); the drawn and the handled vertices per (entity, ring).
    private val midpointHandles = Reuse<Triple<EntityId, Int, Int>, Any, List<Renderable>>()
    private val simplifiedVertices = Reuse<Pair<EntityId, Int>, Any, List<Vertex>>()
    private var applied: List<Renderable> = emptyList()

    // How coarsely shapes are drawn for the current camera (see [LodLevel]); chosen each frame in doRender.
    private var lodLevel = LodLevel.NONE

    // The level each shape was last built at. A shape that is off screen keeps its old level until it comes
    // into view: building it again for a camera that can't see it would be wasted work and memory churn.
    private val shapeLevels = HashMap<EntityId, Int>()
    private val shapeBounds = HashMap<EntityId, ShapeBox>()
    private var lodPending = false

    // False until the first frame when [buildOnFirstFrame]: nothing is built before the camera, and with it the level of detail, is known.
    private var ready = !buildOnFirstFrame
    private val handleAnchorIndices = Reuse<Pair<EntityId, Int>, Any, List<Int>>()

    // How coarsely the edit handles are laid out for the current camera (a LodLevel, with a much bigger tolerance than drawing).
    private var handleLevel = LodLevel.NONE

    /** Whether the last [applyLod] left shapes at their old level (over its per-call budget); the next call carries on. */
    internal val hasPendingLod: Boolean get() = lodPending
    private var latest: Inputs? = null

    init {
        combine(entityRepository.entities, stateMachine.state, handleStyleFlow, highlightStyleFlow) { entities, engineState, handles, highlights ->
            Inputs(entities, engineState, handles, highlights)
        }
            .onEach { inputs ->
                latest = inputs
                if (ready) {
                    update(inputs.entities, inputs.engineState, inputs.handles, inputs.highlights)
                    onContentChanged()
                }
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
        simplifiedVertices.begin()
        handleAnchorIndices.begin()
        highlightAlphaSetters.clear()
        trackShapes(entities)
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

        // Every shape in drawing order: by its z-index, then polygons below lines below markers, then as they were added.
        for (entity in drawingOrder(entities)) {
            when (entity) {
                is DrawnPolygon -> {
                    val shape = drawnPolygon(entity, editMode, newVertexDrag)
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
                is DrawnPolyline -> {
                    val renderVertices = livePreviewVertices(entity.id, 0, drawnVertices(entity.id, 0, entity.vertices, false, editMode), editMode, newVertexDrag)
                    val shape = if (renderVertices === entity.vertices) entity else entity.copy(vertices = renderVertices)
                    highlightOf(entity.id, shape, { shape.toWwkHighlight(it) }) { r, a -> (r as Path).attributes.outlineColor.alpha = a }
                        ?.let { drawn += it.renderable }
                    drawn += bodies.get(entity.id, shape) { listOf(shape.toWwkPolyline().tagged(entity.id)) }
                }
                is DrawnMarker -> {
                    highlightOf(entity.id, entity, { entity.toWwkHighlight(it) }) { r, a -> (r as Placemark).attributes.imageColor.alpha = a }
                        ?.let { drawn += it.renderable }
                    // While the marker is being edited its own icon is not pickable: the move target (below) is what is grabbed,
                    // and two pickable placemarks on one spot could let the wrong one win.
                    val beingEdited = editMode?.entityId == entity.id
                    drawn += bodies.get(entity.id, Pair(entity, beingEdited)) {
                        listOf(entity.toWwkPlacemark().tagged(entity.id).apply { if (beingEdited) isPickEnabled = false })
                    }
                }
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
            // A polygon has a ring of handles for each of its rings; a polyline, one line of them.
            val editedRings = when (editedEntity) {
                is DrawnPolygon -> editedEntity.rings
                is DrawnPolyline -> listOf(editedEntity.vertices)
                is DrawnMarker, null -> emptyList()
            }
            val isClosed = editedEntity is DrawnPolygon
            editedRings.forEachIndexed { ringIndex, vertices ->
                // Which vertices have a handle: all of them, or — for a shape with many points seen from far away — only
                // those that matter at the current handle level, so the user edits a coarse outline with a few handles.
                val anchors = handleAnchors(Pair(editMode.entityId, ringIndex), vertices, isClosed, handleStyle.detail)
                val n = vertices.size
                anchors.forEachIndexed { k, index ->
                    val vertex = vertices[index]
                    // The handled vertices on either side: dragging this one moves the hidden vertices in between with it.
                    val prevIndex = if (anchors.size < 2) null else if (k > 0) anchors[k - 1] else if (isClosed) anchors.last() else null
                    val nextIndex = if (anchors.size < 2) null else if (k < anchors.size - 1) anchors[k + 1] else if (isClosed) anchors.first() else null
                    val prevId = prevIndex?.takeIf { hiddenBetween(it, index, n) > 0 }?.let { vertices[it].id }
                    val nextId = nextIndex?.takeIf { hiddenBetween(index, it, n) > 0 }?.let { vertices[it].id }
                    val selected = editMode.selectedVertexId == vertex.id
                    val style = if (selected) handleStyle.copy(fillColor = handleStyle.selectedFillColor) else handleStyle
                    drawn += vertexHandles.get(editMode.entityId to vertex.id, Triple(vertex.position, style, Pair(prevId, nextId))) {
                        vertexHandleRenderables(vertex.position, style) {
                            putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                            putUserProperty(VERTEX_ID_KEY, vertex.id)
                            if (prevId != null) putUserProperty(SPAN_PREV_KEY, prevId)
                            if (nextId != null) putUserProperty(SPAN_NEXT_KEY, nextId)
                        }
                    }
                }
                val skipEdgeIndex = newVertexDrag?.takeIf { it.ringIndex == ringIndex }?.edgeIndex
                val coarseEdges = if (isClosed) anchors.size else anchors.size - 1
                for (k in 0 until coarseEdges) {
                    val from = anchors[k]
                    val to = anchors[(k + 1) % anchors.size]
                    val hidden = hiddenBetween(from, to, n)
                    if (hidden > 0) {
                        // Hidden vertices lie between these two handled ones. A new vertex dropped among them would stick out of
                        // their outline like a spike (and make them look important enough to get handles of their own), so the "+"
                        // sits on the stretch's real middle vertex instead, and dragging it moves that vertex, with its hidden
                        // neighbours following in proportion (see SpanMover).
                        val middle = middleVertexIndex(vertices, from, to, n)
                        val vertex = vertices[middle]
                        val prevId = vertices[from].id
                        val nextId = vertices[to].id
                        drawn += midpointHandles.get(Triple(editMode.entityId, ringIndex, k), Triple(vertex.position, handleStyle, Triple(vertex.id, prevId, nextId))) {
                            midpointHandleRenderables(vertex.position, handleStyle) {
                                putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                                putUserProperty(VERTEX_ID_KEY, vertex.id)
                                putUserProperty(SPAN_PREV_KEY, prevId)
                                putUserProperty(SPAN_NEXT_KEY, nextId)
                                putUserProperty(STRETCH_MIDDLE_KEY, true)
                            }
                        }
                    } else {
                        // Two vertices next to each other: an ordinary edge, and a dragged "+" adds a vertex into it.
                        if (from == skipEdgeIndex) continue
                        val start = vertices[from].position
                        val end = vertices[to].position
                        drawn += midpointHandles.get(Triple(editMode.entityId, ringIndex, k), Triple(Pair(start, end), handleStyle, from)) {
                            midpointHandleRenderables(edgeMidpoint(start, end), handleStyle) {
                                putUserProperty(ENTITY_ID_KEY, editMode.entityId)
                                putUserProperty(EDGE_INDEX_KEY, from)
                                putUserProperty(RING_INDEX_KEY, ringIndex)
                            }
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
     * [entities] in the order they are drawn, the first at the bottom: by [DrawableEntity.zIndex], then polygons below lines below
     * markers, then in the order they came in (the sort is stable). What is drawn later is drawn over, and is what a tap reaches.
     */
    private fun drawingOrder(entities: List<DrawableEntity>): List<DrawableEntity> =
        entities.sortedWith(compareBy<DrawableEntity>({ it.zIndex }, { kindRank(it) }))

    private fun kindRank(entity: DrawableEntity) = when (entity) {
        is DrawnPolygon -> 0
        is DrawnPolyline -> 1
        is DrawnMarker -> 2
    }

    /**
     * The indices of the vertices that get an edit handle, in order: every vertex of a shape with few points, otherwise those
     * that matter at the current [handleLevel] (see [PathSimplifier]). The same list is returned while the vertices and
     * the level are the same.
     */
    private fun handleAnchors(key: Pair<EntityId, Int>, vertices: List<Vertex>, isClosed: Boolean, detail: HandleDetail): List<Int> {
        if (detail == HandleDetail.All || handleLevel == LodLevel.NONE || vertices.size <= HANDLE_MIN_VERTICES) return vertices.indices.toList()
        return handleAnchorIndices.get(key, Pair(vertices, handleLevel)) {
            val tolerance = LodLevel.toleranceMeters(handleLevel)
            val positions = vertices.map { it.position }
            if (isClosed) PathSimplifier.simplifyRing(positions, tolerance) else PathSimplifier.simplifyPath(positions, tolerance)
        }
    }

    /**
     * The hidden vertex halfway along the stretch from index [from] to index [to] (going forward, past the end of the list for a ring),
     * by length along it: on a coastline with points of very different spacing the middle by count would be nowhere near the middle.
     */
    private fun middleVertexIndex(vertices: List<Vertex>, from: Int, to: Int, count: Int): Int {
        val indices = generateSequence(from) { (it + 1) % count }.takeWhile { true }.take(((to - from + count) % count).let { if (it == 0) count else it } + 1).toList()
        val lengths = DoubleArray(indices.size)
        for (i in 1 until indices.size) {
            val a = vertices[indices[i - 1]].position
            val b = vertices[indices[i]].position
            val dLat = b.latitude - a.latitude
            val dLon = (b.longitude - a.longitude) * kotlin.math.cos((a.latitude + b.latitude) / 2 * kotlin.math.PI / 180.0)
            lengths[i] = lengths[i - 1] + kotlin.math.sqrt(dLat * dLat + dLon * dLon)
        }
        val half = lengths.last() / 2
        // Of the hidden vertices (not the two ends), the one nearest to half the length.
        var best = 1
        for (i in 1 until indices.size - 1) if (kotlin.math.abs(lengths[i] - half) < kotlin.math.abs(lengths[best] - half)) best = i
        return indices[best]
    }

    /** How many vertices lie strictly between index [from] and index [to] going forward (past the end of the list for a ring). */
    private fun hiddenBetween(from: Int, to: Int, count: Int): Int = ((to - from + count) % count).let { if (it == 0) count - 1 else it - 1 }

    /** Remembers the box of every shape the level applies to (a box is recomputed only when the shape's vertices change), and forgets the removed ones. */
    private fun trackShapes(entities: List<DrawableEntity>) {
        val alive = HashSet<EntityId>()
        for (entity in entities) {
            // What a shape is known by (the same instance while it doesn't change), how many vertices it has and the box around them all.
            val (token, rings) = when (entity) {
                is DrawnPolygon -> entity.rings to entity.rings
                is DrawnPolyline -> entity.vertices to listOf(entity.vertices)
                else -> continue
            }
            val vertexCount = rings.sumOf { it.size }
            if (rings.all { it.size <= LOD_MIN_VERTICES }) continue
            alive += entity.id
            val known = shapeBounds[entity.id]
            if (known == null || known.token !== token) shapeBounds[entity.id] = ShapeBox(token, vertexCount, GeoBounds.ofRings(rings))
            shapeLevels.getOrPut(entity.id) { lodLevel } // a new shape is built at the level everything else is at, not whole
        }
        shapeBounds.keys.retainAll(alive)
        shapeLevels.keys.retainAll(alive)
    }

    /**
     * [entity] as it is drawn now: each of its rings simplified for the current level (all of them whole while it is edited, see
     * [drawnVertices]), with the live point of a "+" drag spliced into the ring it belongs to. The entity itself while nothing differs.
     */
    private fun drawnPolygon(entity: DrawnPolygon, editMode: DrawMode.Edit?, drag: DrawMode.Edit.ActiveDrag.NewVertex?): DrawnPolygon {
        val rings = entity.rings
        val drawn = rings.mapIndexed { index, ring ->
            livePreviewVertices(entity.id, index, drawnVertices(entity.id, index, ring, true, editMode), editMode, drag)
        }
        if (drawn.indices.all { drawn[it] === rings[it] }) return entity
        return entity.copy(vertices = drawn.first(), extraRings = drawn.drop(1))
    }

    /**
     * The vertices a shape is drawn with: all of them while it is being edited (its handles sit on the real vertices, and
     * an edit must be exact), otherwise only those that matter at the current [lodLevel] — a continent's border seen
     * from space doesn't need its thousands of points, and every point costs memory and drawing time. The same
     * list instance is returned while neither the shape's vertices nor its level change.
     */
    private fun drawnVertices(id: EntityId, ringIndex: Int, vertices: List<Vertex>, isClosed: Boolean, editMode: DrawMode.Edit?): List<Vertex> {
        val level = shapeLevels[id] ?: LodLevel.NONE
        if (level == LodLevel.NONE || editMode?.entityId == id || vertices.size <= LOD_MIN_VERTICES) return vertices
        return simplifiedVertices.get(Pair(id, ringIndex), Pair(vertices, level)) {
            val tolerance = LodLevel.toleranceMeters(level)
            val positions = vertices.map { it.position }
            val kept = if (isClosed) PathSimplifier.simplifyRing(positions, tolerance) else PathSimplifier.simplifyPath(positions, tolerance)
            if (kept.size == vertices.size) vertices else kept.map { vertices[it] }
        }
    }

    /**
     * Picks how coarsely to draw for what the camera shows now: one screen pixel's worth of metres at the camera's
     * distance is the tolerance, so what is dropped could not be seen anyway. Done every frame because the camera
     * moves outside of this layer's control; shapes are only built again when the level really changes.
     */
    override fun doRender(rc: RenderContext) {
        val pixelMeters = rc.pixelSizeAtDistance(rc.camera.position.altitude)
        onFrame(
            LodLevel.forTolerance(pixelMeters * LOD_PIXELS, lodLevel),
            LodLevel.forTolerance(pixelMeters * HANDLE_PIXELS, handleLevel),
            visibleBounds(rc),
        )
        super.doRender(rc)
    }

    /**
     * What each frame does, given what the camera shows. The first frame of a layer made with `buildOnFirstFrame` is where
     * it builds its shapes at all, straight away at the right level of detail — building them whole and then again, simplified,
     * would cost the memory and time of both.
     */
    internal fun onFrame(level: Int, handleLevel: Int, view: GeoBounds?) {
        if (!ready) {
            ready = true
            lodLevel = level
            this.handleLevel = handleLevel
            latest?.let {
                update(it.entities, it.engineState, it.handles, it.highlights)
                onContentChanged()
            }
            return
        }
        applyLod(level, view)
        applyHandleLevel(handleLevel)
        if (lodPending) onContentChanged() // more shapes to bring to the new level: ask for another frame
    }

    /** What the camera sees, as a box grown a little so a pan doesn't show shapes at the old level; `null` when that isn't known yet. */
    private fun visibleBounds(rc: RenderContext): GeoBounds? {
        val sector = rc.terrain.sector
        if (sector.isEmpty) return null
        return GeoBounds(sector.minLatitude.inDegrees, sector.maxLatitude.inDegrees, sector.minLongitude.inDegrees, sector.maxLongitude.inDegrees)
            .grownBy(VIEW_MARGIN)
    }

    /**
     * Makes [level] the camera's level and brings the shapes in [view] to it ([view] `null`: all of them). Shapes
     * outside it keep the level they have until they are seen (and a shape added meanwhile is built at [level] at once). At most [LOD_VERTEX_BUDGET] vertices' worth
     * are built again per call, so a zoom that changes the level for hundreds of big shapes at once doesn't
     * freeze a frame; [lodPending] says whether some are still waiting for the next call.
     */
    /**
     * Makes [level] the one the edit handles are laid out for. The handles are only built again if a shape is being edited
     * and not in the middle of a drag (they would move under the finger): then the level is simply kept until the drag ends.
     */
    internal fun applyHandleLevel(level: Int) {
        if (level == handleLevel) return
        val mode = latest?.engineState?.mode as? DrawMode.Edit
        if (mode?.activeDrag != null) return
        handleLevel = level
        if (mode != null) latest?.let { update(it.entities, it.engineState, it.handles, it.highlights) }
    }

    internal fun applyLod(level: Int, view: GeoBounds? = null) {
        lodLevel = level
        var budget = LOD_VERTEX_BUDGET
        var changed = false
        lodPending = false
        for ((id, entry) in shapeBounds) {
            if ((shapeLevels[id] ?: LodLevel.NONE) == level) continue
            if (view != null && !entry.bounds.intersects(view)) continue
            if (budget <= 0) { lodPending = true; break }
            budget -= entry.vertexCount
            shapeLevels[id] = level
            changed = true
        }
        if (changed) latest?.let { update(it.entities, it.engineState, it.handles, it.highlights) }
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
     * [vertices] (ring [ringIndex] of the shape) with [drag]'s live position spliced in at its edge — but only when [drag]
     * belongs to [entityId] and to that ring (an in-progress midpoint drag on a *different* entity or ring must not
     * leak a phantom point into this one). Returns [vertices] unchanged otherwise.
     */
    private fun livePreviewVertices(
        entityId: EntityId,
        ringIndex: Int,
        vertices: List<Vertex>,
        editMode: DrawMode.Edit?,
        drag: DrawMode.Edit.ActiveDrag.NewVertex?,
    ): List<Vertex> {
        if (drag == null || editMode == null || editMode.entityId != entityId || drag.ringIndex != ringIndex) return vertices
        val phantom = Vertex(PHANTOM_VERTEX_ID, drag.position)
        return vertices.toMutableList().apply { add(drag.edgeIndex + 1, phantom) }
    }

    private fun <T : Renderable> T.tagged(entityId: EntityId): T = apply { putUserProperty(ENTITY_ID_KEY, entityId) }

    public companion object {
        /** [Renderable.putUserProperty] key every renderable this layer creates is tagged with. */
        public const val ENTITY_ID_KEY: String = "com.tcsappdev.worldwinddraw.entityId"

        /** [Renderable.putUserProperty] key a vertex-handle [Placemark] is additionally tagged with. */
        public const val VERTEX_ID_KEY: String = "com.tcsappdev.worldwinddraw.vertexId"

        /** [Renderable.putUserProperty] key a midpoint-handle [Placemark] is additionally tagged with. */
        public const val EDGE_INDEX_KEY: String = "com.tcsappdev.worldwinddraw.edgeIndex"

        /** [Renderable.putUserProperty] key a midpoint handle is tagged with, next to [EDGE_INDEX_KEY]: which ring of a polygon the edge is in. */
        public const val RING_INDEX_KEY: String = "com.tcsappdev.worldwinddraw.ringIndex"

        /** Tags on a vertex handle that has hidden vertices next to it: the handled vertex before and after it (see `DrawEvent.DragStart.hitSpanPrevId`). */
        public const val SPAN_PREV_KEY: String = "com.tcsappdev.worldwinddraw.spanPrev"

        /** Tags the "+" handle that sits on a hidden vertex in the middle of a stretch between two handled vertices (it moves that vertex, it does not add one). */
        public const val STRETCH_MIDDLE_KEY: String = "com.tcsappdev.worldwinddraw.stretchMiddle"

        /** See [SPAN_PREV_KEY]. */
        public const val SPAN_NEXT_KEY: String = "com.tcsappdev.worldwinddraw.spanNext"

        /** [Renderable.putUserProperty] key the move target of the marker being edited is additionally tagged with. */
        public const val MARKER_MOVE_KEY: String = "com.tcsappdev.worldwinddraw.markerMove"

        /**
         * Keys for the handles of a shape that is still being drawn (`DrawMode.Draw`). There is no
         * entity or vertex id yet, so these handles carry no [ENTITY_ID_KEY]; they are addressed by
         * index into the draft's points / edges instead.
         */
        public const val DRAFT_POINT_INDEX_KEY: String = "com.tcsappdev.worldwinddraw.draftPointIndex"

        /** See [DRAFT_POINT_INDEX_KEY]. */
        public const val DRAFT_EDGE_INDEX_KEY: String = "com.tcsappdev.worldwinddraw.draftEdgeIndex"

        /** Placeholder id for the rendering-only phantom vertex spliced in during a live [DrawMode.Edit.ActiveDrag.NewVertex] preview — never persisted, discarded on the next rebuild. */
        private val PHANTOM_VERTEX_ID = VertexId("__phantom__")

        /** Drawn in place of an image fill whose picture can't be produced (see `withImageFillBelow`). */
        private val IMAGE_FILL_PLACEHOLDER = ShapeStyle.SolidColor(ColorRgba(128, 128, 128, 128))

        /** Shapes are drawn simplified to about this many screen pixels of detail. */
        private const val LOD_PIXELS = 1.0

        /** A shape with this few vertices (or fewer) is always drawn whole: there's nothing worth dropping. */
        private const val LOD_MIN_VERTICES = 12

        /** Edit handles are laid out about this many screen pixels apart (as the tolerance of simplifying the outline). */
        private const val HANDLE_PIXELS = 14.0

        /** A shape with this few vertices (or fewer) gets a handle on every one. */
        private const val HANDLE_MIN_VERTICES = 12

        /**
         * How many vertices' worth of shapes are brought to a new level in one frame. Every shape built again is also tessellated by WWK
         * in the same frame, so a camera step that changes the level for a whole world at once (about 25,000 vertices for the 1914 demo)
         * made one frame several times too long on a phone (5 to 10 frames a second while zooming). Spread over frames it is not seen:
         * a shape is at the old level for a few frames at most.
         */
        private const val LOD_VERTEX_BUDGET = 4_000

        /** The camera's view is grown by this fraction on each side when deciding what is visible. */
        private const val VIEW_MARGIN = 0.25

        private const val PULSE_DURATION_MS = 700L
        private const val PULSE_MIN_ALPHA = 0.2f

    }
}

/** What the level of detail needs to know about a shape: [token] changes when its vertices do, and the [bounds] of all its rings. */
private class ShapeBox(val token: Any, val vertexCount: Int, val bounds: GeoBounds)

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
