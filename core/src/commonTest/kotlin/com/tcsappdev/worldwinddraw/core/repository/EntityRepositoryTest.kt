package com.tcsappdev.worldwinddraw.core.repository

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityRepositoryTest {
    private val style = ShapeStyle.SolidColor(ColorRgba(255, 0, 0))

    @Test
    fun addAppendsToEntities() {
        val repository = EntityRepository()
        val marker = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))

        repository.add(marker)

        assertEquals(listOf(marker), repository.entities.value)
    }

    @Test
    fun removeDropsOnlyTheMatchingEntity() {
        val repository = EntityRepository()
        val a = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        val b = DrawnMarker(EntityId("B"), style, GeoPosition(2.0, 2.0))
        repository.add(a)
        repository.add(b)

        repository.remove(EntityId("A"))

        assertEquals(listOf(b), repository.entities.value)
    }

    @Test
    fun updateReplacesOnlyTheMatchingEntity() {
        val repository = EntityRepository()
        val a = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        val b = DrawnMarker(EntityId("B"), style, GeoPosition(2.0, 2.0))
        repository.add(a)
        repository.add(b)

        val movedA = a.copy(position = GeoPosition(9.0, 9.0))
        repository.update(movedA)

        assertEquals(listOf(movedA, b), repository.entities.value)
    }
}
