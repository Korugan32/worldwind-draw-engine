package com.tcsappdev.worldwinddraw.adapter.input

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.mapper.toGeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.geom.Position
import earth.worldwind.gesture.SelectDragCallback
import earth.worldwind.render.Renderable
import kotlin.random.Random

/**
 * Translates WWK's [SelectDragCallback] gestures into [DrawEvent]s for [stateMachine] — the
 * single entry point real user input reaches the draw engine through.
 *
 * [SelectDragCallback] is defined once in `commonMain` by WWK itself and backed by a
 * platform-specific gesture detector on every target (Android/JVM/JS/iOS), so this whole
 * bridge is ordinary shared code — a host wires it up per-platform with one line:
 * `worldWindow.selectDragDetector.callback = pickInputBridge`.
 *
 * [canMoveObjects] arms WWK's drag machinery for two kinds of handle placemarks (see
 * [DrawLayer]'s KDoc): a vertex handle (tagged with [DrawLayer.VERTEX_ID_KEY]) and a
 * midpoint handle (tagged with [DrawLayer.EDGE_INDEX_KEY]) — never for a whole
 * polygon/polyline/marker. Dragging either still moves the placemark via WWK's own
 * `Movable.moveTo` (harmless — [DrawLayer] rebuilds and replaces it right after, driven by
 * the effect [onObjectMoved] has through [stateMachine]), but
 * [onObjectMoved]/[onObjectMovingFinished] are what actually drive the draw engine,
 * converting each step into [DrawEvent.DragStart]/[DrawEvent.DragMove]/[DrawEvent.DragEnd].
 *
 * The same two handle kinds exist on a shape that is still being drawn (see [DrawLayer.DRAFT_POINT_INDEX_KEY]):
 * they carry an index into the draft instead of an entity/vertex id, and dragging them reshapes the
 * draft ([DrawEvent.DragStart.hitDraftPointIndex]/[DrawEvent.DragStart.hitDraftEdgeIndex]).
 *
 * The marker being edited has a move target too (see [DrawLayer.MARKER_MOVE_KEY]): dragging it moves the
 * marker ([DrawEvent.DragStart.hitMarker]).
 *
 * **Grab offset (midpoint and marker drags).** WWK drags a ground-clamped handle by snapping it straight
 * to the cursor (`toPosition` is the terrain under the finger), keeping no memory of where on
 * the handle it was grabbed, and a drag only starts once the finger has moved past the touch
 * slop. So the first `toPosition` is already some distance — the grab point's offset from the
 * handle's centre plus the slop — away from the handle. Passed through as-is, the new vertex
 * would leap that far on the first move, and because it's a brand-new spike on the outline the
 * whole shape visibly "grows" at once. Instead the offset between the handle
 * (`fromPosition`, WWK's reference position for a `Movable`) and that first cursor position is
 * remembered, and every later position is shifted back by it: the new vertex starts exactly
 * where the handle was and then follows the finger, staying at the same distance from it.
 * Vertex-handle drags are unchanged (they already move an existing point, not grow the shape).
 *
 * A midpoint-handle drag needs a fresh [VertexId] only once it actually commits (at
 * [DragEnd][DrawEvent.DragEnd] — see [DrawMode.Edit.ActiveDrag.NewVertex][com.tcsappdev.worldwinddraw.core.state.DrawMode.Edit.ActiveDrag]'s
 * KDoc for why nothing is inserted before then), so [pendingNewVertexId] is generated once
 * at [DragStart][DrawEvent.DragStart] and carried across the gesture by this bridge, not
 * regenerated on every step.
 */
class PickInputBridge(private val stateMachine: DrawStateMachine) : SelectDragCallback {
    private var isDragging = false
    private var pendingNewVertexId: VertexId? = null

    // How far (degrees) the cursor was from the midpoint handle when its drag began; see the
    // class KDoc. Zero for any other drag, and between drags.
    private var grabOffsetLatitude = 0.0
    private var grabOffsetLongitude = 0.0

    /** Must be true for [onObjectPicked] to ever fire — see [SelectDragCallback]'s KDoc. */
    override fun canPickObjects(userObject: Any) = true

    override fun onObjectPicked(userObject: Any, position: Position) {
        // A tap on a draft's own handle must not add a point under it: dragging is how a handle is
        // used, and a tap landing exactly on an existing point would otherwise just duplicate it.
        if (isDraftHandle(userObject)) return
        // A vertex handle names its vertex, which is how a tap selects it. The "+" on a stretch's middle vertex also carries a vertex id
        // (dragging it moves that vertex) but is not a handle ON a vertex, so tapping it selects nothing.
        val vertex = if ((userObject as? Renderable)?.hasUserProperty(DrawLayer.STRETCH_MIDDLE_KEY) == true) null else vertexIdOf(userObject)
        stateMachine.onEvent(DrawEvent.Click(position.toGeoPosition(), entityIdOf(userObject), hitVertexId = vertex))
    }

    override fun onTerrainPicked(position: Position) {
        stateMachine.onEvent(DrawEvent.Click(position.toGeoPosition(), hitEntityId = null))
    }

    // onNothingPicked() is left as the default no-op: a tap that misses the globe entirely
    // has no geographic position to attach to a Click event.

    override fun canMoveObjects(userObject: Any): Boolean {
        if (isDraftHandle(userObject)) return true
        if (entityIdOf(userObject) == null) return false
        return vertexIdOf(userObject) != null || edgeIndexOf(userObject) != null || isMarkerMove(userObject)
    }

    override fun onObjectMoved(userObject: Any, fromPosition: Position, toPosition: Position) {
        val draftPoint = draftPointIndexOf(userObject)
        val draftEdge = draftEdgeIndexOf(userObject)
        val entityId = entityIdOf(userObject)
        if (entityId == null && draftPoint == null && draftEdge == null) return
        if (!isDragging) {
            isDragging = true
            val vertexId = vertexIdOf(userObject)
            val edgeIndex = edgeIndexOf(userObject)
            when {
                draftPoint != null ->
                    stateMachine.onEvent(DrawEvent.DragStart(fromPosition.toGeoPosition(), null, null, hitDraftPointIndex = draftPoint))
                draftEdge != null -> {
                    rememberGrabOffset(fromPosition, toPosition)
                    stateMachine.onEvent(DrawEvent.DragStart(fromPosition.toGeoPosition(), null, null, hitDraftEdgeIndex = draftEdge))
                }
                isMarkerMove(userObject) -> {
                    // Like a midpoint handle, the marker must not leap to the finger when grabbed off-centre.
                    rememberGrabOffset(fromPosition, toPosition)
                    stateMachine.onEvent(
                        DrawEvent.DragStart(fromPosition.toGeoPosition(), hitVertexId = null, hitEntityId = entityId, hitMarker = true)
                    )
                }
                vertexId != null ->
                    stateMachine.onEvent(
                        DrawEvent.DragStart(
                            fromPosition.toGeoPosition(), vertexId, entityId,
                            hitSpanPrevId = spanOf(userObject, DrawLayer.SPAN_PREV_KEY),
                            hitSpanNextId = spanOf(userObject, DrawLayer.SPAN_NEXT_KEY),
                        )
                    )
                edgeIndex != null -> {
                    pendingNewVertexId = VertexId(Random.nextLong().toString())
                    rememberGrabOffset(fromPosition, toPosition)
                    stateMachine.onEvent(
                        DrawEvent.DragStart(
                            fromPosition.toGeoPosition(), hitVertexId = null, hitEntityId = entityId, hitEdgeIndex = edgeIndex,
                            hitRingIndex = ringIndexOf(userObject) ?: 0,
                        )
                    )
                }
                else -> {
                    isDragging = false
                    return
                }
            }
        }
        stateMachine.onEvent(DrawEvent.DragMove(toPosition.withoutGrabOffset()))
    }

    override fun onObjectMovingFinished(userObject: Any, position: Position) {
        if (!isDragging) return
        isDragging = false
        val newVertexId = pendingNewVertexId
        pendingNewVertexId = null
        stateMachine.onEvent(DrawEvent.DragEnd(position.withoutGrabOffset(), newVertexId))
        grabOffsetLatitude = 0.0
        grabOffsetLongitude = 0.0
    }

    private fun rememberGrabOffset(fromPosition: Position, toPosition: Position) {
        grabOffsetLatitude = toPosition.latitude.inDegrees - fromPosition.latitude.inDegrees
        grabOffsetLongitude = shortestLongitudeDelta(toPosition.longitude.inDegrees - fromPosition.longitude.inDegrees)
    }

    private fun Position.withoutGrabOffset(): GeoPosition {
        val geo = toGeoPosition()
        if (grabOffsetLatitude == 0.0 && grabOffsetLongitude == 0.0) return geo
        return GeoPosition(
            latitude = (geo.latitude - grabOffsetLatitude).coerceIn(-90.0, 90.0),
            longitude = ((geo.longitude - grabOffsetLongitude + 540.0) % 360.0) - 180.0,
            altitude = geo.altitude,
        )
    }

    /** [delta] wrapped into -180..180, so a drag across the antimeridian doesn't produce a ~360 degree offset. */
    private fun shortestLongitudeDelta(delta: Double): Double = ((delta + 540.0) % 360.0) - 180.0

    private fun isMarkerMove(userObject: Any) =
        (userObject as? Renderable)?.getUserProperty<Boolean>(DrawLayer.MARKER_MOVE_KEY) == true

    private fun draftPointIndexOf(userObject: Any): Int? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.DRAFT_POINT_INDEX_KEY)

    private fun draftEdgeIndexOf(userObject: Any): Int? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.DRAFT_EDGE_INDEX_KEY)

    private fun isDraftHandle(userObject: Any) = draftPointIndexOf(userObject) != null || draftEdgeIndexOf(userObject) != null

    private fun entityIdOf(userObject: Any): EntityId? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.ENTITY_ID_KEY)

    private fun vertexIdOf(userObject: Any): VertexId? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.VERTEX_ID_KEY)

    private fun spanOf(userObject: Any, key: String): VertexId? =
        (userObject as? Renderable)?.getUserProperty(key)

    private fun edgeIndexOf(userObject: Any): Int? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.EDGE_INDEX_KEY)

    private fun ringIndexOf(userObject: Any): Int? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.RING_INDEX_KEY)
}
