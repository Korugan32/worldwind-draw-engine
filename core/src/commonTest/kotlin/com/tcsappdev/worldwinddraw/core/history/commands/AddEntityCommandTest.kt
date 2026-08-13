package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class AddEntityCommandTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun executeAddsAndUndoRemoves() {
        val repository = EntityRepository()
        val marker = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        val command = AddEntityCommand(repository, marker)

        command.execute()
        assertEquals(listOf(marker), repository.entities.value)

        command.undo()
        assertEquals(emptyList(), repository.entities.value)
    }
}
