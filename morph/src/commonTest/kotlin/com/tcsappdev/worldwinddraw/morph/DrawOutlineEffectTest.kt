package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawOutlineEffectTest {
    private val linear = AnimationSpec(durationMs = 1000, easing = { it })
    private val first = marker("first", 1.0, 1.0)
    private val polygon = triangle("p", 10.0)
    private val last = marker("last", 2.0, 2.0)

    private fun repository() = EntityRepository().apply { add(first); add(polygon); add(last) }

    @Test
    fun itDoesNothingUntilItIsTicked() {
        val repository = repository()
        DrawOutlineEffect(repository, polygon, linear)
        assertEquals(listOf(first, polygon, last), repository.entities.value)
    }

    @Test
    fun whilePlayingTheOtherShapesStayInPlaceAroundWhatIsDrawn() {
        val repository = repository()
        val effect = DrawOutlineEffect(repository, polygon, linear)
        effect.onTick(200)
        val shapes = repository.entities.value
        assertEquals(first, shapes.first())
        assertEquals(last, shapes.last())
        assertFalse(polygon in shapes)
        assertTrue(shapes.size > 2)
    }

    @Test
    fun whenItIsOverThePolygonIsBackWhereItWasAndItIsToldSo() {
        val repository = repository()
        var finished = 0
        val effect = DrawOutlineEffect(repository, polygon, linear) { finished++ }
        effect.onTick(600)
        assertFalse(effect.isFinished)
        effect.onTick(400)
        assertTrue(effect.isFinished)
        assertEquals(listOf(first, polygon, last), repository.entities.value)
        assertEquals(1, finished)

        repository.add(marker("later", 0.0, 0.0))
        effect.onTick(100)
        assertEquals(1, finished)
        assertEquals(4, repository.entities.value.size)
    }

    @Test
    fun aPolygonThatIsNotThereEndsItAtOnceAndChangesNothing() {
        val repository = EntityRepository().apply { add(first) }
        var finished = 0
        val effect = DrawOutlineEffect(repository, polygon, linear) { finished++ }
        effect.onTick(100)
        assertTrue(effect.isFinished)
        assertEquals(1, finished)
        assertEquals(listOf(first), repository.entities.value)
    }

    @Test
    fun theEasingShapesTheWholeEffect() {
        val repository = repository()
        val slowStart = AnimationSpec(durationMs = 1000, easing = { it * it })
        DrawOutlineEffect(repository, polygon, slowStart, DrawOutlineOptions(drawShare = 0.5f)).also { it.onTick(500) }
        // at half the time the eased progress is 0.25, so the outline is half drawn: the fill has not come yet
        assertTrue(repository.entities.value.none { it.id == polygon.id })
        assertTrue(repository.entities.value.any { it.id.id.startsWith("p#draw") })
    }

    @Test
    fun anEffectWithNoDurationOrRepeatIsRefused() {
        assertFailsWith<IllegalArgumentException> { DrawOutlineEffect(repository(), polygon, AnimationSpec(0, { it })) }
        assertFailsWith<IllegalArgumentException> { DrawOutlineEffect(repository(), polygon, AnimationSpec(1000, { it }, RepeatMode.Loop)) }
    }
}
