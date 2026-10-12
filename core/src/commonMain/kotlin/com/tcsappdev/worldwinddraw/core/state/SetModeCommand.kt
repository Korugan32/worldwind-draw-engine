package com.tcsappdev.worldwinddraw.core.state

import com.tcsappdev.worldwinddraw.core.history.Command

/**
 * An undoable change of [DrawMode]: [execute] sets the mode to [after], [undo] back to
 * [before]. This is how the shape being drawn (a draft, which lives in [DrawMode.Draw] rather
 * than in the repository) gets onto the undo stack — every step of sketching it is one of these.
 *
 * Both directions just *set* the mode, so applying one twice is harmless. That matters because
 * the reducer has already moved the live state to [after] by the time [HistoryManager.execute][com.tcsappdev.worldwinddraw.core.history.HistoryManager.execute]
 * runs `execute()` on it.
 *
 * [applyMode] is how a mode reaches the live state; it is injected so this command stays free of
 * [DrawStateMachine]'s internals.
 */
internal class SetModeCommand(
    private val applyMode: (DrawMode) -> Unit,
    private val before: DrawMode,
    private val after: DrawMode,
) : Command {
    override fun execute() = applyMode(after)

    override fun undo() = applyMode(before)
}
