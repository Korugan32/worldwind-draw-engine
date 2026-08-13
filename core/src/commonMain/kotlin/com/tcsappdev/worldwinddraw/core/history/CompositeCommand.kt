package com.tcsappdev.worldwinddraw.core.history

/**
 * Bundles several [Command]s into one undoable step — e.g. so a whole drag gesture (many
 * small intermediate moves) collapses into a single undo, instead of the user having to
 * press undo once per intermediate step.
 */
class CompositeCommand(private val commands: List<Command>) : Command {
    override fun execute() {
        commands.forEach { it.execute() }
    }

    override fun undo() {
        // Reversed on purpose: later commands may depend on state left by earlier ones,
        // so undoing must unwind in the opposite order they were applied in.
        commands.reversed().forEach { it.undo() }
    }
}