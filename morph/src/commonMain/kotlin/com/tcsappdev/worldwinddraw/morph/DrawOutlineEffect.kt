package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.animation.Animatable
import com.tcsappdev.worldwinddraw.core.animation.AnimatedProperty
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Plays [DrawOutline] on the polygon with the id of [polygon] in [repository]: register it with the host's
 * [AnimationController][com.tcsappdev.worldwinddraw.core.animation.AnimationController] and every tick puts in the repository what
 * [DrawOutline.at] gives for how far along it is, in the place of the polygon (the other shapes stay as they are). When it is over, the
 * repository holds [polygon] again and [onFinished] is called; one that has finished does nothing more, so the host can unregister it there.
 *
 * The pen goes at an even speed only if [spec]'s easing is linear: the easing reshapes the whole effect, the outline and the fill.
 *
 * It is a show, not an edit: nothing goes into the undo history. While it plays the polygon is not in the repository under its own id, so
 * the host should leave the editing modes first, and not draw, edit or load until it has finished. A polygon that is not in the repository
 * when the first tick comes ends the effect at once.
 *
 * @throws IllegalArgumentException if [spec] has no duration or repeats.
 */
public class DrawOutlineEffect(
    private val repository: EntityRepository,
    private val polygon: DrawnPolygon,
    spec: AnimationSpec,
    private val options: DrawOutlineOptions = DrawOutlineOptions(),
    private val onFinished: () -> Unit = {},
) : Animatable {
    init {
        require(spec.durationMs > 0) { "an effect needs a duration" }
        require(spec.repeatMode == RepeatMode.Once) { "an effect plays once" }
    }

    private val progress = AnimatedProperty(0f, 1f, spec) { start, end, fraction -> start + (end - start) * fraction }

    // What is on the map before and after the polygon, taken when the first tick comes (so the polygon keeps its place in the order).
    private var before: List<DrawableEntity>? = null
    private var after: List<DrawableEntity> = emptyList()

    /** Whether the effect has played to the end (the polygon is back as it was and `onFinished` has been called). */
    public var isFinished: Boolean = false
        private set

    override fun onTick(deltaMs: Long) {
        if (isFinished) return
        if (before == null) {
            val all = repository.entities.value
            val at = all.indexOfFirst { it.id == polygon.id }
            if (at < 0) return finish()
            before = all.take(at)
            after = all.drop(at + 1)
        }
        progress.onTick(deltaMs)
        if (progress.isFinished) {
            repository.replaceAll(before.orEmpty() + polygon + after)
            finish()
        } else {
            repository.replaceAll(before.orEmpty() + DrawOutline.at(polygon, progress.value, options) + after)
        }
    }

    private fun finish() {
        isFinished = true
        onFinished()
    }
}
