package com.tcsappdev.worldwinddraw.adapter.input

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.mapper.toGeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
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
 * A midpoint-handle drag needs a fresh [VertexId] only once it actually commits (at
 * [DragEnd][DrawEvent.DragEnd] — see [DrawMode.Edit.ActiveDrag.NewVertex][com.tcsappdev.worldwinddraw.core.state.DrawMode.Edit.ActiveDrag]'s
 * KDoc for why nothing is inserted before then), so [pendingNewVertexId] is generated once
 * at [DragStart][DrawEvent.DragStart] and carried across the gesture by this bridge, not
 * regenerated on every step.
 */
class PickInputBridge(private val stateMachine: DrawStateMachine) : SelectDragCallback {
    private var isDragging = false
    private var pendingNewVertexId: VertexId? = null

    /** Must be true for [onObjectPicked] to ever fire — see [SelectDragCallback]'s KDoc. */
    override fun canPickObjects(userObject: Any) = true

    override fun onObjectPicked(userObject: Any, position: Position) {
        stateMachine.onEvent(DrawEvent.Click(position.toGeoPosition(), entityIdOf(userObject)))
    }

    override fun onTerrainPicked(position: Position) {
        stateMachine.onEvent(DrawEvent.Click(position.toGeoPosition(), hitEntityId = null))
    }

    // onNothingPicked() is left as the default no-op: a tap that misses the globe entirely
    // has no geographic position to attach to a Click event.

    override fun canMoveObjects(userObject: Any): Boolean {
        if (entityIdOf(userObject) == null) return false
        return vertexIdOf(userObject) != null || edgeIndexOf(userObject) != null
    }

    override fun onObjectMoved(userObject: Any, fromPosition: Position, toPosition: Position) {
        val entityId = entityIdOf(userObject) ?: return
        if (!isDragging) {
            isDragging = true
            val vertexId = vertexIdOf(userObject)
            val edgeIndex = edgeIndexOf(userObject)
            when {
                vertexId != null ->
                    stateMachine.onEvent(DrawEvent.DragStart(fromPosition.toGeoPosition(), vertexId, entityId))
                edgeIndex != null -> {
                    pendingNewVertexId = VertexId(Random.nextLong().toString())
                    stateMachine.onEvent(
                        DrawEvent.DragStart(fromPosition.toGeoPosition(), hitVertexId = null, hitEntityId = entityId, hitEdgeIndex = edgeIndex)
                    )
                }
                else -> {
                    isDragging = false
                    return
                }
            }
        }
        stateMachine.onEvent(DrawEvent.DragMove(toPosition.toGeoPosition()))
    }

    override fun onObjectMovingFinished(userObject: Any, position: Position) {
        if (!isDragging) return
        isDragging = false
        val newVertexId = pendingNewVertexId
        pendingNewVertexId = null
        stateMachine.onEvent(DrawEvent.DragEnd(position.toGeoPosition(), newVertexId))
    }

    private fun entityIdOf(userObject: Any): EntityId? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.ENTITY_ID_KEY)

    private fun vertexIdOf(userObject: Any): VertexId? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.VERTEX_ID_KEY)

    private fun edgeIndexOf(userObject: Any): Int? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.EDGE_INDEX_KEY)
}
