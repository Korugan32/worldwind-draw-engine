package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.HistoryManager
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
 * Any [com.tcsappdev.worldwinddraw.core.history.Command]s the reducer produces for a given
 * event are handed to [historyManager], which both executes them (mutating the entity
 * repository) and makes them undoable.
 */
class DrawStateMachine(private val historyManager: HistoryManager) {
    private val _state = MutableStateFlow(DrawEngineState())
    val state: StateFlow<DrawEngineState> = _state.asStateFlow()

    fun onEvent(event: DrawEvent) {
        val result = DrawEngineReducer.reduce(_state.value, event)
        _state.value = result.state
        result.commands.forEach { historyManager.execute(it) }
    }
}
