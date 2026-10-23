package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteEntityCommandTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun executeRemovesAndUndoRestores() {
        val repository = EntityRepository()
        val marker = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        repository.add(marker)
        val command = DeleteEntityCommand(repository, marker)

        command.execute()
        assertEquals(emptyList(), repository.entities.value)

        command.undo()
        assertEquals(listOf(marker), repository.entities.value)
    }

    @Test
    fun byIdTheEntityIsRememberedAtExecuteTimeIncludingLaterEdits() {
        val repository = EntityRepository()
        val original = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        repository.add(original)
        val command = DeleteEntityCommand(repository, EntityId("A"))
        // The shape changes after the command was built but before it runs.
        val edited = original.copy(position = GeoPosition(5.0, 5.0))
        repository.remove(original.id)
        repository.add(edited)

        command.execute()
        assertEquals(emptyList(), repository.entities.value)

        command.undo()
        assertEquals(listOf(edited), repository.entities.value, "restores the entity as it was when deleted, not as when the command was built")
    }

    @Test
    fun byIdDeletingAnEntityThatDoesNotExistDoesNothingInEitherDirection() {
        val repository = EntityRepository()
        val other = DrawnMarker(EntityId("B"), style, GeoPosition(2.0, 2.0))
        repository.add(other)
        val command = DeleteEntityCommand(repository, EntityId("missing"))

        command.execute()
        command.undo()

        assertEquals(listOf(other), repository.entities.value)
    }
}
