package com.tcsappdev.worldwinddraw.core.repository

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun replaceAllMakesTheGivenShapesTheOnlyOnesInThatOrder() {
        val repository = EntityRepository()
        repository.add(DrawnMarker(EntityId("old"), style, GeoPosition(1.0, 1.0)))
        val b = DrawnMarker(EntityId("B"), style, GeoPosition(2.0, 2.0))
        val a = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))

        repository.replaceAll(listOf(b, a))

        assertEquals(listOf(b, a), repository.entities.value)
    }

    @Test
    fun replaceAllWithNothingClearsTheRepository() {
        val repository = EntityRepository()
        repository.add(DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0)))
        repository.replaceAll(emptyList())
        assertEquals(emptyList(), repository.entities.value)
    }

    @Test
    fun replaceAllKeepsACopyNotTheCallersList() {
        val repository = EntityRepository()
        val list = mutableListOf<DrawableEntity>(DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0)))
        repository.replaceAll(list)
        list.clear()
        assertEquals(1, repository.entities.value.size)
    }

    @Test
    fun twoShapesWithTheSameIdAreRefusedAndNothingChanges() {
        val repository = EntityRepository()
        val a = DrawnMarker(EntityId("A"), style, GeoPosition(1.0, 1.0))
        repository.add(a)

        assertFailsWith<IllegalArgumentException> {
            repository.replaceAll(listOf(DrawnMarker(EntityId("X"), style, GeoPosition(2.0, 2.0)), DrawnMarker(EntityId("X"), style, GeoPosition(3.0, 3.0))))
        }

        assertEquals(listOf(a), repository.entities.value)
    }

    // The point of replaceAll: a watcher sees the old list and the new one, not a list for every shape removed and every shape added.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aWatcherSeesOneChangeNotOnePerShape() = runTest(UnconfinedTestDispatcher()) {
        val repository = EntityRepository()
        repository.add(DrawnMarker(EntityId("old1"), style, GeoPosition(1.0, 1.0)))
        repository.add(DrawnMarker(EntityId("old2"), style, GeoPosition(1.0, 1.0)))
        val seen = mutableListOf<List<DrawableEntity>>()
        backgroundScope.launch { repository.entities.toList(seen) }

        repository.replaceAll((1..50).map { DrawnMarker(EntityId("new$it"), style, GeoPosition(1.0, 1.0)) })

        assertEquals(2, seen.size) // what was there, and the 50 new shapes
        assertEquals(50, seen.last().size)
    }
}
