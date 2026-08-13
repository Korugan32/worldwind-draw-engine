package com.tcsappdev.worldwinddraw.core.history

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.history.commands.AddEntityCommand
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryManagerTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun undoRedoCycleTogglesCanUndoCanRedo() {
        val repository = EntityRepository()
        val history = HistoryManager()
        val marker = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        val command = AddEntityCommand(repository, marker)

        assertFalse(history.canUndo.value)
        history.execute(command)
        assertEquals(1, repository.entities.value.size)
        assertTrue(history.canUndo.value)
        assertFalse(history.canRedo.value)

        history.undo()
        assertEquals(0, repository.entities.value.size)
        assertFalse(history.canUndo.value)
        assertTrue(history.canRedo.value)

        history.redo()
        assertEquals(1, repository.entities.value.size)
        assertTrue(history.canUndo.value)
        assertFalse(history.canRedo.value)
    }

    @Test
    fun executingANewCommandClearsTheRedoStack() {
        // Mirrors "back, then navigate somewhere new" in a browser: the forward history
        // becomes meaningless once a new action is taken, so it must be dropped.
        val repository = EntityRepository()
        val history = HistoryManager()
        val a = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        val b = DrawnMarker(EntityId("B"), style, GeoPosition(2.0, 2.0))

        history.execute(AddEntityCommand(repository, a))
        history.undo()
        assertTrue(history.canRedo.value)

        history.execute(AddEntityCommand(repository, b))
        assertFalse(history.canRedo.value)
    }

    @Test
    fun capacityLimitDropsTheOldestUndoEntry() {
        val repository = EntityRepository()
        val history = HistoryManager(capacity = 2)
        val a = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        val b = DrawnMarker(EntityId("B"), style, GeoPosition(2.0, 2.0))
        val c = DrawnMarker(EntityId("C"), style, GeoPosition(3.0, 3.0))

        history.execute(AddEntityCommand(repository, a))
        history.execute(AddEntityCommand(repository, b))
        history.execute(AddEntityCommand(repository, c))

        history.undo()
        history.undo()
        // capacity=2 means only the last 2 commands (b, c) are undoable; "a" was dropped
        // from the undo stack and stays on the map permanently.
        assertFalse(history.canUndo.value)
        assertEquals(listOf(EntityId("A")), repository.entities.value.map { it.id })
    }
}
