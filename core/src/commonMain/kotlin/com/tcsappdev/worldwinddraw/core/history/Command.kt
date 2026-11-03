package com.tcsappdev.worldwinddraw.core.history

/**
 * A single, undoable change. Concrete commands carry only the delta they need (e.g. an
 * old/new position), not a full copy of whatever they're changing — see the plan's
 * "Command kuralı" for why. [execute] and [undo] must be exact inverses: calling
 * `execute()` then `undo()` must leave the affected state bit-for-bit as it was before.
 */
internal interface Command {
    fun execute()
    fun undo()
}