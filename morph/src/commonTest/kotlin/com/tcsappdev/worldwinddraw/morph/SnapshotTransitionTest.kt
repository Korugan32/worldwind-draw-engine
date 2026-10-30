package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.animation.AnimationController
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.Easing
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotTransitionTest {
    private val linear = AnimationSpec(durationMs = 1000, easing = Easing { it })
    private val from = Snapshot(listOf(marker("m", 0.0, 0.0), triangle("gone", 1.0)))
    private val to = Snapshot(listOf(marker("m", 20.0, 40.0)))

    private fun repository() = EntityRepository().apply { restore(from) }

    private fun EntityRepository.markerPosition() = (entities.value.first { it.id.id == "m" } as DrawnMarker).position

    @Test
    fun itDoesNothingUntilItIsTicked() {
        val repository = repository()
        SnapshotTransition(repository, from, to, linear)
        assertEquals(from.entities, repository.entities.value)
    }

    @Test
    fun eachTickShowsHowFarAlongItIs() {
        val repository = repository()
        val transition = SnapshotTransition(repository, from, to, linear)

        transition.onTick(250)
        assertEquals(GeoPosition(5.0, 10.0), repository.markerPosition())
        transition.onTick(250)
        assertEquals(GeoPosition(10.0, 20.0), repository.markerPosition())
        assertFalse(transition.isFinished)
    }

    @Test
    fun theEasingShapesTheMovement() {
        val repository = repository()
        val transition = SnapshotTransition(repository, from, to, AnimationSpec(1000, Easing { it * it }))
        transition.onTick(500) // halfway in time, a quarter of the way along
        assertEquals(GeoPosition(5.0, 10.0), repository.markerPosition())
    }

    @Test
    fun whenTheTimeIsUpTheDrawingIsExactlyTheSecondSnapshotAndItIsToldOnce() {
        val repository = repository()
        var finished = 0
        val transition = SnapshotTransition(repository, from, to, linear) { finished++ }

        transition.onTick(600)
        transition.onTick(600) // more than is left

        assertEquals(to.entities, repository.entities.value)
        assertTrue(transition.isFinished)
        assertEquals(1, finished)
    }

    @Test
    fun aFinishedTransitionLeavesTheDrawingAlone() {
        val repository = repository()
        var finished = 0
        val transition = SnapshotTransition(repository, from, to, linear) { finished++ }
        transition.onTick(1000)

        repository.add(marker("later", 1.0, 1.0))
        transition.onTick(500)

        assertEquals(listOf("m", "later"), repository.entities.value.map { it.id.id })
        assertEquals(1, finished)
    }

    @Test
    fun itPlaysThroughAnAnimationControllerAndMayUnregisterItselfWhenFinished() {
        val repository = repository()
        val controller = AnimationController()
        lateinit var transition: SnapshotTransition
        transition = SnapshotTransition(repository, from, to, linear) { controller.unregister(transition) }
        controller.register(transition)

        repeat(10) { controller.tick(100) }

        assertTrue(transition.isFinished)
        assertEquals(to.entities, repository.entities.value)
    }

    @Test
    fun theShapesThatGoAndComeFadeAlongTheWay() {
        val repository = repository()
        SnapshotTransition(repository, from, to, linear).apply { onTick(500) }
        val gone = repository.entities.value.first { it.id.id == "gone" }
        assertEquals(128, (gone.style as ShapeStyle.SolidColor).color.alpha) // half of 255 is 127.5, rounded up
    }

    @Test
    fun aTransitionNeedsADurationAndPlaysOnce() {
        assertFailsWith<IllegalArgumentException> { SnapshotTransition(repository(), from, to, AnimationSpec(0, Easing { it })) }
        assertFailsWith<IllegalArgumentException> { SnapshotTransition(repository(), from, to, linear.copy(repeatMode = RepeatMode.Loop)) }
    }
}
