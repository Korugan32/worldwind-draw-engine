package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition

/**
 * The interaction mode the draw engine is currently in. Sealed so every place that
 * branches on mode (the reducer, and eventually the host UI) is guaranteed by the
 * compiler to handle every case — adding a new mode later surfaces every place that
 * needs updating as a compile error.
 */
sealed interface DrawMode {
    /** Nothing is happening; a tap can select a shape or fall through to panning. */
    object Idle : DrawMode

    /** The user is moving the camera; drawing/editing gestures are suspended. */
    object Pan : DrawMode

    /**
     * A new shape is being sketched. [pendingVertices] holds the points placed so far.
     * This is a draft, not yet a real entity — it isn't subject to
     * [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon]/
     * [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline]'s minimum-vertex-count
     * rules until it's actually committed as one.
     */
    data class Draw(val entityType: DrawableEntityType, val pendingVertices: List<GeoPosition>) : DrawMode

    /** [entityId]'s vertices are being edited (selected, dragged, or having points inserted). */
    data class Edit(val entityId: EntityId) : DrawMode

    /** [entityId] is highlighted as selected, but not yet in edit mode. */
    data class Select(val entityId: EntityId) : DrawMode
}

