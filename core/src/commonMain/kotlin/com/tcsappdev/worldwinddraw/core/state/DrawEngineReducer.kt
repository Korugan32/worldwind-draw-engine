package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.history.commands.AddEntityCommand
import com.tcsappdev.worldwinddraw.core.history.commands.AddVertexCommand
import com.tcsappdev.worldwinddraw.core.history.commands.MoveVertexCommand
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * The result of running the reducer: the new [state], plus what needs to happen as a
 * consequence of this event.
 *
 * [commands] are executed AND recorded on the undo stack — most transitions only change
 * [DrawMode] and produce none, defaulting to empty. [previewCommands] are executed
 * immediately but never recorded: a live vertex drag needs the shape to visually track the
 * finger on every `DragMove` (so it must actually mutate the entity, the same way a real
 * command would), but recording every intermediate step would flood undo with one entry
 * per pixel of movement. Only the final position, at `DragEnd`, becomes a real, undoable
 * [commands] entry.
 */
data class ReduceResult(
    val state: DrawEngineState,
    val commands: List<Command> = emptyList(),
    val previewCommands: List<Command> = emptyList(),
    val modeChange: UndoableModeChange? = null,
)

/**
 * A change of [DrawMode] that belongs on the undo stack: the [before] and [after] modes, with
 * any in-progress drag cleared from both. The reducer only *describes* it (it stays pure); the
 * `DrawStateMachine` turns it into a command.
 *
 * Only steps of sketching a new shape are described this way — placing, moving and inserting
 * points (and switching what is being drawn) — because a draft lives in the mode, not in the
 * repository, so there is no entity command that could undo it. Starting and stopping drawing are
 * described too, but they are session boundaries, which the `DrawStateMachine` does not record
 * (see its KDoc). Switching between select/edit is navigation, not an edit, and is not described.
 */
data class UndoableModeChange(val before: DrawMode, val after: DrawMode)

/**
 * The pure `(state, event) -> new state` core of the draw engine. Has no side effects
 * itself — [reduce]'s `repository` parameter is threaded through only so [Command]s can be
 * constructed referencing it, never read or mutated here — which is what makes every
 * mode/event combination testable in isolation (see `DrawEngineReducerTest`) with no UI or
 * rendering engine running.
 */
object DrawEngineReducer {
    fun reduce(state: DrawEngineState, event: DrawEvent, repository: EntityRepository): ReduceResult = when (event) {
        is DrawEvent.ModeChange -> ReduceResult(state.copy(mode = event.mode), modeChange = recordedIfDraft(state.mode, event.mode))
        is DrawEvent.Click -> reduceClick(state, event)
        is DrawEvent.DragStart -> reduceDragStart(state, event)
        is DrawEvent.DragMove -> reduceDragMove(state, event, repository)
        is DrawEvent.DragEnd -> reduceDragEnd(state, event, repository)
        is DrawEvent.FinishDrawing -> reduceFinishDrawing(state, event, repository)
    }

    private fun reduceClick(state: DrawEngineState, event: DrawEvent.Click): ReduceResult {
        val currentMode = state.mode
        if (currentMode is DrawMode.Draw) {
            val added = currentMode.copy(pendingVertices = currentMode.pendingVertices + event.position)
            return ReduceResult(state.copy(mode = added), modeChange = recordedIfDraft(currentMode, added))
        }
        val newMode = when {

            currentMode is DrawMode.Idle && event.hitEntityId != null -> DrawMode.Select(event.hitEntityId)

            currentMode is DrawMode.Select -> when {
                event.hitEntityId == null -> DrawMode.Idle
                event.hitEntityId == currentMode.entityId -> DrawMode.Edit(event.hitEntityId)
                else -> DrawMode.Select(event.hitEntityId)
            }

            // Clicking the entity already being edited (its body, or a vertex/midpoint
            // handle) is a no-op — inserting a vertex is done by dragging a midpoint
            // handle (see reduceDragStart/reduceDragEnd), not by tapping.
            currentMode is DrawMode.Edit && event.hitEntityId == null -> DrawMode.Idle
            currentMode is DrawMode.Edit && event.hitEntityId != currentMode.entityId -> DrawMode.Select(event.hitEntityId!!)

            else -> currentMode
        }
        return ReduceResult(state.copy(mode = newMode))
    }

    /**
     * Arms a drag only when already in [DrawMode.Edit] for the same entity the drag
     * picked — a drag reported for any other entity is ignored. Exactly one of
     * [DrawEvent.DragStart.hitVertexId]/[DrawEvent.DragStart.hitEdgeIndex] decides which
     * [DrawMode.Edit.ActiveDrag] this becomes; neither set is ignored (e.g. a whole-shape
     * grab, which this library doesn't act on).
     */
    private fun reduceDragStart(state: DrawEngineState, event: DrawEvent.DragStart): ReduceResult {
        val mode = state.mode
        if (mode is DrawMode.Draw) return reduceDraftDragStart(state, mode, event)
        if (mode !is DrawMode.Edit || mode.entityId != event.hitEntityId) return ReduceResult(state)

        val drag = when {
            event.hitVertexId != null -> DrawMode.Edit.ActiveDrag.ExistingVertex(event.hitVertexId, event.position)
            event.hitEdgeIndex != null -> DrawMode.Edit.ActiveDrag.NewVertex(event.hitEdgeIndex, event.position)
            else -> return ReduceResult(state)
        }
        return ReduceResult(state.copy(mode = mode.copy(activeDrag = drag)))
    }

    private fun reduceDragMove(state: DrawEngineState, event: DrawEvent.DragMove, repository: EntityRepository): ReduceResult {
        (state.mode as? DrawMode.Draw)?.let { return reduceDraftDragMove(state, it, event) }
        val mode = state.mode as? DrawMode.Edit ?: return ReduceResult(state)
        return when (val drag = mode.activeDrag) {
            null -> ReduceResult(state)

            is DrawMode.Edit.ActiveDrag.ExistingVertex -> {
                val command = MoveVertexCommand(repository, mode.entityId, drag.vertexId, drag.origin, event.position)
                ReduceResult(state, previewCommands = listOf(command))
            }

            // No repository write here on purpose: this vertex doesn't exist yet, so there's
            // nothing for a preview Command to mutate. DrawLayer renders the in-progress
            // shape straight from this live position instead — see DrawMode.Edit.ActiveDrag's
            // KDoc for why deferring the actual insert to DragEnd is the correct tradeoff.
            is DrawMode.Edit.ActiveDrag.NewVertex -> {
                ReduceResult(state.copy(mode = mode.copy(activeDrag = drag.copy(position = event.position))))
            }
        }
    }

    private fun reduceDragEnd(state: DrawEngineState, event: DrawEvent.DragEnd, repository: EntityRepository): ReduceResult {
        (state.mode as? DrawMode.Draw)?.let { return reduceDraftDragEnd(state, it, event) }
        val mode = state.mode as? DrawMode.Edit ?: return ReduceResult(state)
        val drag = mode.activeDrag ?: return ReduceResult(state)
        val disarmed = state.copy(mode = mode.copy(activeDrag = null))

        val command: Command = when (drag) {
            is DrawMode.Edit.ActiveDrag.ExistingVertex ->
                MoveVertexCommand(repository, mode.entityId, drag.vertexId, drag.origin, event.position)

            is DrawMode.Edit.ActiveDrag.NewVertex -> {
                val newVertexId = event.newVertexId ?: return ReduceResult(disarmed)
                AddVertexCommand(repository, mode.entityId, drag.edgeIndex, Vertex(newVertexId, event.position))
            }
        }
        return ReduceResult(disarmed, commands = listOf(command))
    }

    // -- Reshaping a draft ([DrawMode.Draw]). Pure state changes: the draft isn't an entity, so
    // there is no repository to write to and nothing to record in the undo history.

    private fun reduceDraftDragStart(state: DrawEngineState, mode: DrawMode.Draw, event: DrawEvent.DragStart): ReduceResult {
        val drag = when {
            event.hitDraftPointIndex != null && event.hitDraftPointIndex in mode.pendingVertices.indices ->
                DrawMode.Draw.ActiveDrag.MovingPoint(event.hitDraftPointIndex, origin = mode.pendingVertices[event.hitDraftPointIndex])
            event.hitDraftEdgeIndex != null && event.hitDraftEdgeIndex in 0 until mode.edgeCount ->
                DrawMode.Draw.ActiveDrag.InsertingPoint(event.hitDraftEdgeIndex, event.position)
            else -> return ReduceResult(state)
        }
        return ReduceResult(state.copy(mode = mode.copy(activeDrag = drag)))
    }

    private fun reduceDraftDragMove(state: DrawEngineState, mode: DrawMode.Draw, event: DrawEvent.DragMove): ReduceResult {
        val newMode = when (val drag = mode.activeDrag) {
            null -> return ReduceResult(state)
            is DrawMode.Draw.ActiveDrag.MovingPoint ->
                mode.copy(pendingVertices = mode.pendingVertices.replacedAt(drag.index, event.position))
            is DrawMode.Draw.ActiveDrag.InsertingPoint -> mode.copy(activeDrag = drag.copy(position = event.position))
        }
        return ReduceResult(state.copy(mode = newMode))
    }

    private fun reduceDraftDragEnd(state: DrawEngineState, mode: DrawMode.Draw, event: DrawEvent.DragEnd): ReduceResult {
        val drag = mode.activeDrag ?: return ReduceResult(state)
        val (before, after) = when (drag) {
            is DrawMode.Draw.ActiveDrag.MovingPoint -> Pair(
                mode.copy(pendingVertices = mode.pendingVertices.replacedAt(drag.index, drag.origin), activeDrag = null),
                mode.copy(pendingVertices = mode.pendingVertices.replacedAt(drag.index, event.position), activeDrag = null),
            )
            is DrawMode.Draw.ActiveDrag.InsertingPoint -> Pair(
                mode.copy(activeDrag = null),
                mode.copy(
                    pendingVertices = mode.pendingVertices.toMutableList().apply { add(drag.edgeIndex + 1, event.position) },
                    activeDrag = null,
                ),
            )
        }
        return ReduceResult(state.copy(mode = after), modeChange = recordedIfDraft(before, after))
    }

    /** A [DrawMode] change as an undo step, if it involves a draft and actually changes something (see [UndoableModeChange]). */
    private fun recordedIfDraft(before: DrawMode, after: DrawMode): UndoableModeChange? {
        if (before !is DrawMode.Draw && after !is DrawMode.Draw) return null
        val normalizedBefore = if (before is DrawMode.Draw) before.copy(activeDrag = null) else before
        val normalizedAfter = if (after is DrawMode.Draw) after.copy(activeDrag = null) else after
        return if (normalizedBefore == normalizedAfter) null else UndoableModeChange(normalizedBefore, normalizedAfter)
    }

    private fun List<GeoPosition>.replacedAt(index: Int, position: GeoPosition): List<GeoPosition> =
        toMutableList().apply { this[index] = position }

    /**
     * Commits [DrawMode.Draw]'s `pendingVertices` as a real entity — see
     * [DrawEvent.FinishDrawing]'s KDoc for why [event] carries pre-generated ids instead of
     * the reducer minting its own, and for the no-op conditions this guards against
     * (wrong mode, id-count mismatch). Below that, each [DrawableEntityType] has its own
     * minimum: fewer points than that is *also* a no-op — the draft just stays open so the
     * user can keep adding points and try finishing again, rather than losing their work.
     */
    private fun reduceFinishDrawing(state: DrawEngineState, event: DrawEvent.FinishDrawing, repository: EntityRepository): ReduceResult {
        val mode = state.mode as? DrawMode.Draw ?: return ReduceResult(state)
        if (event.vertexIds.size != mode.pendingVertices.size) return ReduceResult(state)
        val vertices = mode.pendingVertices.zip(event.vertexIds) { position, vertexId -> Vertex(vertexId, position) }

        val entity = when (mode.entityType) {
            DrawableEntityType.Polygon ->
                if (vertices.size < 3) return ReduceResult(state)
                else DrawnPolygon(event.entityId, mode.style, vertices)

            DrawableEntityType.Polyline ->
                if (vertices.size < 2) return ReduceResult(state)
                else DrawnPolyline(event.entityId, mode.style, vertices)

            DrawableEntityType.Marker ->
                if (vertices.isEmpty()) return ReduceResult(state)
                else DrawnMarker(event.entityId, mode.style, vertices.first().position)
        }

        val command = AddEntityCommand(repository, entity)
        // Finishing ends the drawing session, so this is deliberately NOT reported as an undo step
        // (see DrawStateMachine): undo would otherwise delete the whole shape just finished.
        return ReduceResult(state.copy(mode = DrawMode.Select(event.entityId)), commands = listOf(command))
    }
}
