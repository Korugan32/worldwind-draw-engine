package com.tcsappdev.worldwinddraw.core.state

/**
 * The pure `(state, event) -> new state` core of the draw engine. Has no side effects and
 * touches nothing outside its arguments, which is what makes every mode/event combination
 * testable in isolation (see `DrawEngineReducerTest`) with no UI or rendering engine
 * running.
 *
 * Doesn't yet emit commands for undo/redo — that gets added once a `Command` type exists;
 * for now this only computes the next [DrawEngineState].
 */
object DrawEngineReducer {
    fun reduce(state: DrawEngineState, event: DrawEvent): DrawEngineState {
        return when (event) {
            is DrawEvent.ModeChange -> state.copy(mode = event.mode)
            is DrawEvent.Click -> {
                val currentMode = state.mode
                when {
                    currentMode is DrawMode.Draw -> {
                        val newList = currentMode.pendingVertices + event.position

                        val newMode = currentMode.copy(pendingVertices = newList)

                        state.copy(mode = newMode)
                    }

                    currentMode is DrawMode.Idle && event.hitEntityId != null -> {
                        state.copy(mode = DrawMode.Select(event.hitEntityId))
                    }

                    currentMode is DrawMode.Select -> {
                        if (event.hitEntityId == null) {
                            state.copy(mode = DrawMode.Idle)
                        } else if (event.hitEntityId == currentMode.entityId) {
                            state.copy(mode = DrawMode.Edit(event.hitEntityId))
                        } else {
                            state.copy(mode = DrawMode.Select(event.hitEntityId))
                        }
                    }


                    else -> state
                }
            }

            else -> state
        }
    }
}