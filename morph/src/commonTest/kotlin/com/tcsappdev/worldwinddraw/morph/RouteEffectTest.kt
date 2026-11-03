package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteEffectTest {
    private val linear = AnimationSpec(durationMs = 1000, easing = { it })
    private val id = EntityId("r")
    private val route = listOf(GeoPosition(0.0, 0.0), GeoPosition(0.0, 40.0))
    private val options = RouteOptions(vehicle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255)), glow = 0f, height = 0f, arc = 0f, lift = 0f)
    private val other = marker("other", 1.0, 1.0)

    private fun repository() = EntityRepository().apply { add(other) }

    @Test
    fun itDoesNothingUntilItIsTicked() {
        val repository = repository()
        RouteEffect(repository, id, route, linear, options)
        assertEquals(listOf(other), repository.entities.value)
    }

    @Test
    fun whilePlayingTheOtherShapesStayAndTheTripIsAddedToThem() {
        val repository = repository()
        val effect = RouteEffect(repository, id, route, linear, options)
        effect.onTick(500)
        val shapes = repository.entities.value
        assertEquals(other, shapes.first())
        val vehicle = shapes.filterIsInstance<DrawnMarker>().single { it.id.id == "r#vehicle" }
        assertTrue(vehicle.position.longitude in 19.0..21.0)
    }

    @Test
    fun whenItIsOverTheTripLeavesItsLineAndIsToldSo() {
        val repository = repository()
        var finished = 0
        val effect = RouteEffect(repository, id, route, linear, options) { finished++ }
        effect.onTick(600)
        assertFalse(effect.isFinished)
        effect.onTick(400)
        assertTrue(effect.isFinished)
        assertEquals(1, finished)
        assertEquals(listOf("other", "r#trail"), repository.entities.value.map { it.id.id })

        repository.add(marker("later", 0.0, 0.0))
        effect.onTick(100)
        assertEquals(1, finished)
        assertEquals(3, repository.entities.value.size)
    }

    @Test
    fun aTripPlayedAgainTakesAwayWhatItLeftTheTimeBefore() {
        val repository = repository()
        RouteEffect(repository, id, route, linear, options).apply { onTick(1000) }
        assertEquals(2, repository.entities.value.size)
        val again = RouteEffect(repository, id, route, linear, options)
        again.onTick(100)
        assertEquals(1, repository.entities.value.count { it.id.id == "other" })
        // the whole line of the first trip (40 degrees) is gone: there is only the beginning of the second (4 degrees)
        val reach = repository.entities.value.filterIsInstance<DrawnPolyline>().maxOf { line -> line.vertices.maxOf { it.position.longitude } }
        assertTrue(reach < 10.0, "reached $reach")
    }

    @Test
    fun theEasingShapesTheWholeTrip() {
        val repository = repository()
        val slowStart = AnimationSpec(durationMs = 1000, easing = { it * it })
        RouteEffect(repository, id, route, slowStart, options).onTick(500)
        // at half the time the eased progress is a quarter: 10 degrees of the 40
        val vehicle = repository.entities.value.filterIsInstance<DrawnMarker>().single { it.id.id == "r#vehicle" }
        assertTrue(vehicle.position.longitude in 9.0..11.0, "was ${vehicle.position.longitude}")
    }

    @Test
    fun badInputIsRefused() {
        assertFailsWith<IllegalArgumentException> { RouteEffect(repository(), id, route, AnimationSpec(0, { it })) }
        assertFailsWith<IllegalArgumentException> { RouteEffect(repository(), id, route, AnimationSpec(1000, { it }, RepeatMode.Loop)) }
        assertFailsWith<IllegalArgumentException> { RouteEffect(repository(), id, route.take(1), linear) }
    }
}
