package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

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
     * rules until [DrawEvent.FinishDrawing] actually commits it as one (see
     * `DrawEngineReducer`), using [style] for the new entity.
     */
    data class Draw(
        val entityType: DrawableEntityType,
        val pendingVertices: List<GeoPosition>,
        val style: ShapeStyle = DEFAULT_STYLE,
    ) : DrawMode {
        companion object {
            private val DEFAULT_STYLE = ShapeStyle.SolidColor(ColorRgba(red = 66, green = 133, blue = 244, alpha = 180))
        }
    }

    /**
     * [entityId]'s vertices are being edited (selected, dragged, or having points inserted).
     *
     * [activeDrag] is non-null only between a `DragStart` and its matching `DragEnd` — see
     * [ActiveDrag] for the two things a drag can mean.
     */
    data class Edit(
        val entityId: EntityId,
        val activeDrag: ActiveDrag? = null,
    ) : DrawMode {
        /**
         * What an in-progress drag inside [Edit] is doing. Modeled as two distinct cases
         * (rather than a flatter handful of nullable fields) so "dragging an existing
         * vertex" and "dragging a not-yet-real new vertex into existence" can't be
         * represented at the same time or confused with each other.
         */
        sealed interface ActiveDrag {
            /**
             * [vertexId] (an existing vertex) is being repositioned. [origin] is fixed at
             * the position the drag started from — the "old" position for the single
             * [com.tcsappdev.worldwinddraw.core.history.commands.MoveVertexCommand] that
             * gets recorded once the gesture finishes (see `DrawEngineReducer`).
             */
            data class ExistingVertex(val vertexId: VertexId, val origin: GeoPosition) : ActiveDrag

            /**
             * A midpoint handle on edge [edgeIndex] is being dragged into a new vertex.
             * Deliberately does NOT touch [com.tcsappdev.worldwinddraw.core.repository.EntityRepository]
             * while dragging — [position] only drives a live rendering preview (see
             * `DrawLayer`). Only one [com.tcsappdev.worldwinddraw.core.history.commands.AddVertexCommand],
             * built with the final position, is ever recorded — inserting provisionally at
             * drag-start and moving it on every step would need a second, different command
             * for undo (remove) than for redo (re-insert at the right position), which is
             * exactly the kind of state a pure reducer can't safely fake.
             */
            data class NewVertex(val edgeIndex: Int, val position: GeoPosition) : ActiveDrag
        }
    }

    /** [entityId] is highlighted as selected, but not yet in edit mode. */
    data class Select(val entityId: EntityId) : DrawMode
}

