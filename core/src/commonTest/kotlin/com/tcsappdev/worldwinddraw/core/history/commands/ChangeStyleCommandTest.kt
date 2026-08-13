package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeStyleCommandTest {
    private val red = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))
    private val blue = ShapeStyle.SolidColor(ColorRgba(0, 0, 255))

    @Test
    fun executeChangesStyleAndUndoRestoresIt() {
        val repository = EntityRepository()
        repository.add(DrawnMarker(EntityId("m1"), red, GeoPosition(1.0, 1.0)))
        val command = ChangeStyleCommand(repository, EntityId("m1"), oldStyle = red, newStyle = blue)

        command.execute()
        assertEquals(blue, repository.entities.value.first().style)

        command.undo()
        assertEquals(red, repository.entities.value.first().style)
    }
}
