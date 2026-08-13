package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.Command

/**
 * The result of running the reducer: the new [state], plus any [commands] that need to be
 * executed (and pushed to history) as a consequence of this event. Most transitions only
 * change [DrawMode] and produce no commands — [commands] defaults to empty for those.
 */
data class ReduceResult(
    val state: DrawEngineState,
    val commands: List<Command> = emptyList(),
)

/**
 * The pure `(state, event) -> new state` core of the draw engine. Has no side effects and
 * touches nothing outside its arguments, which is what makes every mode/event combination
 * testable in isolation (see `DrawEngineReducerTest`) with no UI or rendering engine
 * running.
 */
object DrawEngineReducer {
    fun reduce(state: DrawEngineState, event: DrawEvent): ReduceResult {
        val newState = when (event) {
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
        // No branch produces commands yet — that lands once there's a way to turn a
        // finished Draw.pendingVertices draft into a real entity (see PLAN.md Faz 3 notes).
        return ReduceResult(state = newState, commands = emptyList())
    }
}
