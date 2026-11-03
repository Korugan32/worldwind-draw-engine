package com.tcsappdev.worldwinddraw.core.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The undo/redo stack. [execute] runs a [Command] and makes it undoable; [undo]/[redo]
 * walk the history in either direction. [canUndo]/[canRedo] are exposed as [StateFlow] so
 * a host UI can enable/disable undo/redo buttons reactively without polling.
 *
 * Executing a new command after undoing clears the redo stack — the same behavior as a
 * browser dropping its forward history once you navigate somewhere new after going back.
 * [capacity] caps how many steps stay undoable; the oldest entry is dropped once exceeded.
 */
public class HistoryManager(private val capacity: Int = 100) {
    private val undoStack = mutableListOf<Command>()
    private val redoStack = mutableListOf<Command>()

    private val _canUndo = MutableStateFlow(false)
    /** Whether there is a step to [undo]; collect it to enable or disable an undo button. */
    public val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    /** Whether there is a step to [redo]; collect it to enable or disable a redo button. */
    public val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    internal fun execute(command: Command) {
        command.execute()
        undoStack.add(command)
        if (undoStack.size > capacity) {
            // removeAt(0), not removeFirst(): on Android, `List.removeFirst()` can resolve
            // to a real JVM/platform method that only exists from API 35 onward, well above
            // this library's minSdk. removeAt(0) has no such ambiguity.
            undoStack.removeAt(0)
        }
        redoStack.clear()
        updateFlags()
    }

    /** Takes back the last step; it can be redone. Nothing happens if there is none. */
    public fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeAt(undoStack.lastIndex)
            command.undo()
            redoStack.add(command)
            updateFlags()
        }
    }

    /** Does again the step that was taken back last. Nothing happens if there is none (a new step clears what could be redone). */
    public fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeAt(redoStack.lastIndex)
            command.execute()
            undoStack.add(command)
            updateFlags()
        }
    }

    /** Forgets every step, undoable and redoable. */
    public fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateFlags()
    }

    private fun updateFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}