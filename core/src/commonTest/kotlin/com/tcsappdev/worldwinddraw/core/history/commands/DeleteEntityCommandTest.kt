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
}
