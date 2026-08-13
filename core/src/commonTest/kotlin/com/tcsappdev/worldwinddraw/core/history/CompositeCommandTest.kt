package com.tcsappdev.worldwinddraw.core.history

import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeCommandTest {
    private fun loggingCommand(log: MutableList<String>, label: String) = object : Command {
        override fun execute() {
            log.add("execute$label")
        }

        override fun undo() {
            log.add("undo$label")
        }
    }

    @Test
    fun executeRunsInOrderAndUndoRunsInReverseOrder() {
        val log = mutableListOf<String>()
        val composite = CompositeCommand(
            listOf(loggingCommand(log, "1"), loggingCommand(log, "2"), loggingCommand(log, "3")),
        )

        composite.execute()
        assertEquals(listOf("execute1", "execute2", "execute3"), log)

        log.clear()
        composite.undo()
        // Reversed on purpose: later commands may depend on state left by earlier ones,
        // so undoing must unwind in the opposite order they were applied in.
        assertEquals(listOf("undo3", "undo2", "undo1"), log)
    }
}
