package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.commands.VertexSpan
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import com.tcsappdev.worldwinddraw.core.style.StyleState

/**
 * The interaction mode the draw engine is currently in. Sealed so every place that
 * branches on mode (the reducer, and eventually the host UI) is guaranteed by the
 * compiler to handle every case — adding a new mode later surfaces every place that
 * needs updating as a compile error.
 */
sealed interface DrawMode {
    /**
     * Which [StyleState] [entityId] should be rendered in while the engine is in this mode:
     * [StyleState.EDITING] or [StyleState.SELECTED] for the one entity [Edit]/[Select] names,
     * [StyleState.DEFAULT] for every other entity and every other mode. `HOVER` and
     * `DISABLED` aren't derivable from a mode — nothing in the engine produces them yet.
     */
    fun styleStateFor(entityId: EntityId): StyleState = when {
        this is Edit && this.entityId == entityId -> StyleState.EDITING
        this is Select && this.entityId == entityId -> StyleState.SELECTED
        else -> StyleState.DEFAULT
    }

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
     *
     * The draft can be reshaped before it is finished, the same two ways an existing shape can
     * (see [Edit]): drag one of the points to move it, or drag the handle on the middle of an
     * edge to grow a new point there. [activeDrag] is non-null only between a `DragStart` and
     * its `DragEnd`. Points have no ids here (the draft is just positions, addressed by index),
     * and — unlike editing a real entity — it isn't undone by repository commands, because the
     * draft isn't an entity yet. Instead each step is recorded as a change of the whole mode (see
     * `UndoableModeChange`), so undo/redo walk through the sketching step by step.
     *
     * With [targetEntityId] set the draft is not a new shape but one more ring of that polygon — another part, or a hole in one, which
     * of the two follows from where it is drawn (see [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon]). Finishing then adds
     * the ring to it instead of creating an entity, and leaves the engine editing that polygon (like finishing any drawing, that ends
     * the drawing session and is not itself something undo can take back; the ring can be deleted again, see [DrawEvent.DeleteSelectedRing]).
     * Only meaningful for [DrawableEntityType.Polygon].
     */
    data class Draw(
        val entityType: DrawableEntityType,
        val pendingVertices: List<GeoPosition>,
        val style: ShapeStyle = DEFAULT_STYLE,
        val activeDrag: ActiveDrag? = null,
        val targetEntityId: EntityId? = null,
    ) : DrawMode {
        /** What an in-progress drag inside [Draw] is doing. */
        sealed interface ActiveDrag {
            /**
             * The point at [index] in `pendingVertices` is being repositioned (it follows the drag
             * live). [origin] is where it was when the drag began: the "before" of the one undo
             * step recorded when the drag ends.
             */
            data class MovingPoint(val index: Int, val origin: GeoPosition) : ActiveDrag

            /**
             * A midpoint handle on edge [edgeIndex] is being dragged into a new point. Like
             * [Edit.ActiveDrag.NewVertex], `pendingVertices` is NOT touched while dragging —
             * [position] only drives a live preview — and the point is inserted (after point
             * [edgeIndex]) once, at the end of the drag.
             */
            data class InsertingPoint(val edgeIndex: Int, val position: GeoPosition) : ActiveDrag
        }

        /** Whether the draft's last point connects back to the first: only a polygon with the 3 points it takes to enclose anything. */
        val isClosed: Boolean get() = entityType == DrawableEntityType.Polygon && pendingVertices.size >= 3

        /**
         * How many edges the draft currently has — each gets a midpoint handle. A closed polygon
         * has as many edges as points (the last one wraps around); a polygon with only 2 points
         * is still just a line, and a polyline has one fewer edge than points. A marker has none.
         */
        val edgeCount: Int get() = when {
            entityType == DrawableEntityType.Marker -> 0
            isClosed -> pendingVertices.size
            else -> (pendingVertices.size - 1).coerceAtLeast(0)
        }

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
        /** The vertex last tapped or grabbed, which [DrawEvent.DeleteSelectedVertex] removes; `null` when none is. */
        val selectedVertexId: VertexId? = null,
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
            data class ExistingVertex(
                val vertexId: VertexId,
                val origin: GeoPosition,
                /** Set when the hidden vertices next to this one move with it (see [VertexSpan]); `null` for a plain one-vertex drag. */
                val span: VertexSpan? = null,
            ) : ActiveDrag

            /**
             * A midpoint handle on edge [edgeIndex] (of ring [ringIndex], `0` for a shape with only one) is being dragged into a new vertex.
             * Deliberately does NOT touch [com.tcsappdev.worldwinddraw.core.repository.EntityRepository]
             * while dragging — [position] only drives a live rendering preview (see
             * `DrawLayer`). Only one [com.tcsappdev.worldwinddraw.core.history.commands.AddVertexCommand],
             * built with the final position, is ever recorded — inserting provisionally at
             * drag-start and moving it on every step would need a second, different command
             * for undo (remove) than for redo (re-insert at the right position), which is
             * exactly the kind of state a pure reducer can't safely fake.
             */
            data class NewVertex(val edgeIndex: Int, val position: GeoPosition, val ringIndex: Int = 0) : ActiveDrag

            /**
             * The marker being edited is being moved. [origin] is where it was when the drag began: the
             * "old" position of the single [com.tcsappdev.worldwinddraw.core.history.commands.MoveMarkerCommand]
             * recorded when the gesture finishes. Unlike a new vertex, the marker really exists, so it
             * follows the drag live (through preview commands) like an existing vertex does.
             */
            data class MovingMarker(val origin: GeoPosition) : ActiveDrag
        }
    }

    /** [entityId] is highlighted as selected, but not yet in edit mode. */
    data class Select(val entityId: EntityId) : DrawMode
}

