package com.tcsappdev.worldwinddraw.core.animation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tells a host app "something changed, redraw now" — an event, not a persisted value, so
 * it's a [SharedFlow] rather than the [kotlinx.coroutines.flow.StateFlow] used elsewhere
 * in `core` for actual state. [requestRedraw] uses [kotlinx.coroutines.flow.MutableSharedFlow.tryEmit]
 * (non-suspending) rather than `emit`, matching [com.tcsappdev.worldwinddraw.core.history.Command]'s
 * choice to keep this library's surface free of `suspend` where nothing is actually
 * asynchronous. The buffer only helps an *already-listening* collector that's momentarily
 * behind — it does not replay past signals to collectors that subscribe later.
 */
class RedrawSignal {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    fun requestRedraw() {
        _signal.tryEmit(Unit)
    }
}