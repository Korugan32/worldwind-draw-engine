package com.tcsappdev.worldwinddraw.adapter.input

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.mapper.toGeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.geom.Position
import earth.worldwind.gesture.SelectDragCallback
import earth.worldwind.render.Renderable

/**
 * Translates WWK's [SelectDragCallback] gestures into [DrawEvent]s for [stateMachine] — the
 * single entry point real user input reaches the draw engine through.
 *
 * [SelectDragCallback] is defined once in `commonMain` by WWK itself and backed by a
 * platform-specific gesture detector on every target (Android/JVM/JS/iOS), so this whole
 * bridge is ordinary shared code — a host wires it up per-platform with one line:
 * `worldWindow.selectDragDetector.callback = pickInputBridge`.
 *
 * Whole-shape dragging ([SelectDragCallback.onObjectMoved]/[SelectDragCallback.canMoveObjects])
 * is deliberately left at its default (no-op) for now. WWK's built-in drag support moves an
 * entire `Movable` shape as a rigid body via `moveTo`, not a single vertex — real vertex
 * editing needs its own hit-testing (Faz 7's `VertexEditController`), so wiring drag now
 * would only support "move the whole shape", a different feature from the one this library
 * is building toward.
 */
class PickInputBridge(private val stateMachine: DrawStateMachine) : SelectDragCallback {
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

    private fun entityIdOf(userObject: Any): EntityId? =
        (userObject as? Renderable)?.getUserProperty(DrawLayer.ENTITY_ID_KEY)
}
