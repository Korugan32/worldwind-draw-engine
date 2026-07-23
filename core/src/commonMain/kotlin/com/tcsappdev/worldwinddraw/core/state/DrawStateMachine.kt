package com.tcsappdev.worldwinddraw.core.state

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
 */
class DrawStateMachine {
    private val _state = MutableStateFlow(DrawEngineState())
    val state: StateFlow<DrawEngineState> = _state.asStateFlow()

    fun onEvent(event: DrawEvent) {
        _state.value = DrawEngineReducer.reduce(_state.value, event)
    }
}