package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.history.Command
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
)

/**
 * The pure `(state, event) -> new state` core of the draw engine. Has no side effects
 * itself — [reduce]'s `repository` parameter is threaded through only so [Command]s can be
 * constructed referencing it, never read or mutated here — which is what makes every
 * mode/event combination testable in isolation (see `DrawEngineReducerTest`) with no UI or
 * rendering engine running.
 */
object DrawEngineReducer {
    fun reduce(state: DrawEngineState, event: DrawEvent, repository: EntityRepository): ReduceResult = when (event) {
        is DrawEvent.ModeChange -> ReduceResult(state.copy(mode = event.mode))
        is DrawEvent.Click -> reduceClick(state, event)
        is DrawEvent.DragStart -> reduceDragStart(state, event)
        is DrawEvent.DragMove -> reduceDragMove(state, event, repository)
        is DrawEvent.DragEnd -> reduceDragEnd(state, event, repository)
    }

    private fun reduceClick(state: DrawEngineState, event: DrawEvent.Click): ReduceResult {
        val currentMode = state.mode
        val newMode = when {
            currentMode is DrawMode.Draw -> currentMode.copy(pendingVertices = currentMode.pendingVertices + event.position)

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
        if (mode !is DrawMode.Edit || mode.entityId != event.hitEntityId) return ReduceResult(state)

        val drag = when {
            event.hitVertexId != null -> DrawMode.Edit.ActiveDrag.ExistingVertex(event.hitVertexId, event.position)
            event.hitEdgeIndex != null -> DrawMode.Edit.ActiveDrag.NewVertex(event.hitEdgeIndex, event.position)
            else -> return ReduceResult(state)
        }
        return ReduceResult(state.copy(mode = mode.copy(activeDrag = drag)))
    }

    private fun reduceDragMove(state: DrawEngineState, event: DrawEvent.DragMove, repository: EntityRepository): ReduceResult {
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
}
