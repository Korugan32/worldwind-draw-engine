package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the draw engine's live [DrawEngineState] and exposes it reactively via [state].
 *
 * [state] is deliberately exposed as [StateFlow] (via [asStateFlow]), never the
 * underlying [MutableStateFlow] — this is the only place state actually changes, and
 * only through [onEvent] routing into [DrawEngineReducer]. Exposing the mutable type
 * directly would let external code overwrite state without going through the reducer.
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
        val result = DrawEngineReducer.reduce(_state.value, event, entityRepository)
        _state.value = result.state
        result.previewCommands.forEach { it.execute() }
        result.commands.forEach { historyManager.execute(it) }
    }
}
