package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.CompositeCommand
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val DRAWING_SESSION = Any()

/**
 * Holds the draw engine's live [DrawEngineState] and exposes it reactively via [state].
 *
 * [state] is deliberately exposed as [StateFlow] (via [asStateFlow]), never the
 * underlying [MutableStateFlow] — this is the only place state actually changes, and
 * only through [onEvent] routing into [DrawEngineReducer]. Exposing the mutable type
 * directly would let external code overwrite state without going through the reducer.
 *
 * **Undo/redo is scoped to a session**, not global: either drawing one new shape, or editing one
 * existing entity. Inside a session each step — placing, moving or inserting a point of the
 * draft ([ReduceResult.modeChange], applied through [SetModeCommand]), or moving/adding a
 * vertex of the entity being edited — is an undo step. When the session ends (finishing or
 * abandoning the drawing, leaving edit mode, or editing a different entity) the history is
 * cleared, and so is what could be undone: finishing a shape is not itself something undo can
 * take back, so undo can never delete a whole shape you just finished. Starting a session is
 * likewise not a step. A host that runs its own commands through the same [HistoryManager] loses
 * them at these boundaries too.
 *
 * [ReduceResult.commands] are handed to [historyManager], which both executes them
 * (mutating [entityRepository]) and makes them undoable. [ReduceResult.previewCommands]
 * are executed directly instead — see [ReduceResult]'s KDoc for why a live vertex drag
 * needs that distinction.
 */
class DrawStateMachine(
    private val historyManager: HistoryManager,
    private val entityRepository: EntityRepository,
) {
    private val _state = MutableStateFlow(DrawEngineState())
    val state: StateFlow<DrawEngineState> = _state.asStateFlow()

    fun onEvent(event: DrawEvent) {
        val modeBefore = _state.value.mode
        val result = DrawEngineReducer.reduce(_state.value, event, entityRepository)
        _state.value = result.state
        result.previewCommands.forEach { it.execute() }

        if (sessionOf(modeBefore) != sessionOf(result.state.mode)) {
            // A session started or ended — see the class KDoc. Its effects still have to happen
            // (finishing a drawing adds the entity), but they are not an undo step: they are the
            // boundary itself. Then the history of the finished session is forgotten.
            result.commands.forEach { it.execute() }
            historyManager.clear()
            return
        }

        val modeChange = result.modeChange
        if (modeChange == null) {
            result.commands.forEach { historyManager.execute(it) }
        } else {
            val modeCommand = SetModeCommand(::applyMode, modeChange.before, modeChange.after)
            historyManager.execute(
                if (result.commands.isEmpty()) modeCommand else CompositeCommand(result.commands + modeCommand)
            )
        }
    }

    /**
     * Which undo/redo session [mode] belongs to, or `null` if it belongs to none. Drawing a new
     * shape is one session; editing a given entity is another (per entity); everything else —
     * idle, panning, merely selecting — has no history of its own.
     */
    private fun sessionOf(mode: DrawMode): Any? = when (mode) {
        is DrawMode.Draw -> DRAWING_SESSION
        is DrawMode.Edit -> mode.entityId
        else -> null
    }

    /** Sets the live mode directly. Only [SetModeCommand] calls this, so that undo/redo can restore a draft. */
    private fun applyMode(mode: DrawMode) {
        _state.value = _state.value.copy(mode = mode)
    }
}
