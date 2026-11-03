package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.animation.Animatable
import com.tcsappdev.worldwinddraw.core.animation.AnimatedProperty
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Plays [RouteMotion] on [route] in [repository]: register it with the host's
 * [AnimationController][com.tcsappdev.worldwinddraw.core.animation.AnimationController] and every tick puts in the repository, besides the
 * shapes that are already there, what [RouteMotion.at] gives for how far along the trip is. When it is over, the repository holds what the
 * trip leaves ([RouteOptions.keepTrail], [RouteOptions.keepVehicle]) and [onFinished] is called; one that has finished does nothing more,
 * so the host can unregister it there.
 *
 * The vehicle goes at an even speed only if [spec]'s easing is linear: the easing reshapes the whole trip (an ease in and out starts and
 * stops slowly).
 *
 * It is a show, not an edit: nothing goes into the undo history, and the shapes it adds are made for the trip (their ids start with
 * `<id>#`). The other shapes are taken as they are when the first tick comes, so the host should not draw, edit or load until it has
 * finished. A route played again with the same [id] first takes away what it left the time before.
 *
 * @throws IllegalArgumentException if [spec] has no duration or repeats, or [route] has fewer than 2 points.
 */
public class RouteEffect(
    private val repository: EntityRepository,
    private val id: EntityId,
    private val route: List<GeoPosition>,
    spec: AnimationSpec,
    private val options: RouteOptions = RouteOptions(),
    private val onFinished: () -> Unit = {},
) : Animatable {
    init {
        require(route.size >= 2) { "a route needs at least 2 points" }
        require(spec.durationMs > 0) { "an effect needs a duration" }
        require(spec.repeatMode == RepeatMode.Once) { "an effect plays once" }
    }

    private val progress = AnimatedProperty(0f, 1f, spec) { start, end, fraction -> start + (end - start) * fraction }

    // What is on the map besides the route, taken when the first tick comes.
    private var others: List<DrawableEntity>? = null

    /** Whether the trip has played to the end (what it leaves is in the repository and `onFinished` has been called). */
    public var isFinished: Boolean = false
        private set

    override fun onTick(deltaMs: Long) {
        if (isFinished) return
        val base = others ?: repository.entities.value.filterNot { it.id.id.startsWith("${id.id}#") }.also { others = it }
        progress.onTick(deltaMs)
        if (progress.isFinished) {
            repository.replaceAll(base + RouteMotion.at(id, route, 1f, options))
            isFinished = true
            onFinished()
        } else {
            repository.replaceAll(base + RouteMotion.at(id, route, progress.value, options))
        }
    }
}
