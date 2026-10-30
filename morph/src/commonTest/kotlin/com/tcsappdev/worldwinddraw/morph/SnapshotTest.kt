package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SnapshotTest {
    @Test
    fun aSnapshotHoldsTheShapesInTheirOrder() {
        val repository = EntityRepository().apply { add(triangle("a", 1.0)); add(marker("m", 1.0, 1.0)); add(triangle("b", 2.0)) }
        assertEquals(listOf("a", "m", "b"), repository.snapshot().entities.map { it.id.id })
    }

    @Test
    fun aSnapshotKeepsWhatTheDrawingHadWhenItWasTaken() {
        val repository = EntityRepository().apply { add(triangle("a", 1.0)) }
        val snapshot = repository.snapshot()

        repository.add(marker("m", 1.0, 1.0))
        repository.update(triangle("a", 5.0))
        repository.remove(EntityId("a"))

        assertEquals(listOf(triangle("a", 1.0)), snapshot.entities)
    }

    @Test
    fun restoringPutsTheDrawingBackExactly() {
        val repository = EntityRepository().apply { add(triangle("a", 1.0)); add(marker("m", 1.0, 1.0)) }
        val snapshot = repository.snapshot()
        repository.replaceAll(listOf(triangle("other", 3.0)))

        repository.restore(snapshot)

        assertEquals(snapshot.entities, repository.entities.value)
    }

    @Test
    fun twoShapesWithTheSameIdAreNoSnapshot() {
        assertFailsWith<IllegalArgumentException> { Snapshot(listOf(triangle("a", 1.0), triangle("a", 2.0))) }
    }

    @Test
    fun anEmptyDrawingIsASnapshotToo() {
        val repository = EntityRepository()
        assertEquals(emptyList(), repository.snapshot().entities)
        repository.add(marker("m", 1.0, 1.0))
        repository.restore(Snapshot(emptyList()))
        assertEquals(emptyList(), repository.entities.value)
    }
}
