package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.VertexId

/**
 * A user interaction, translated from whatever input system the host app uses (e.g. WWK
 * pick/gesture callbacks in `wwk-adapter`) into a form [DrawEngineReducer] can act on
 * without knowing anything about that platform.
 */
sealed interface DrawEvent {
    /**
     * A tap/click at [position]. [hitEntityId] is whatever the adapter's own picking
     * determined was under that point, or `null` for empty space — hit-testing happens
     * in the adapter (see the plan's `HitTestService` contract), not recomputed here.
     */
    data class Click(val position: GeoPosition, val hitEntityId: EntityId?) : DrawEvent

    /**
     * A drag gesture begins at [position]. [hitEntityId] identifies which shape (if any)
     * was grabbed; [hitVertexId] additionally identifies a specific vertex of it.
     * Both `null` means empty space; [hitEntityId] set with [hitVertexId] `null` means
     * the whole shape (e.g. a marker, which has no vertices) was grabbed.
     */
    data class DragStart(val position: GeoPosition, val hitVertexId: VertexId?, val hitEntityId: EntityId?) : DrawEvent

    /** The drag continues at [position]. What's being dragged is already known from the [DragStart] that began it. */
    data class DragMove(val position: GeoPosition) : DrawEvent

    /** The drag ends at [position]. */
    data class DragEnd(val position: GeoPosition) : DrawEvent

    /** An explicit request (e.g. a host UI button) to switch to [mode], regardless of the current mode. */
    data class ModeChange(val mode: DrawMode) : DrawEvent
}
