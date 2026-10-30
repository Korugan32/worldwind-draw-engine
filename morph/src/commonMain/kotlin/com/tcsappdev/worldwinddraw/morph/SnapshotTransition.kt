package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.animation.Animatable
import com.tcsappdev.worldwinddraw.core.animation.AnimatedProperty
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.RepeatMode
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Plays the drawing in [repository] from the snapshot [from] to [to]: register it with the host's
 * [AnimationController][com.tcsappdev.worldwinddraw.core.animation.AnimationController] and every tick sets the shapes to what [Morph]
 * gives for how far along it is (the curve of [spec] decides how that moves). When it is over, [repository] holds exactly [to] and
 * [onFinished] is called; a transition that has finished does nothing more, so the host can unregister it there.
 *
 * It is a show, not an edit: nothing goes into the undo history. While it plays the shapes are not the ones the drawing engine's
 * state refers to (a selected shape may be gone or under another id), so the host should leave the editing modes first, and not draw
 * until it has finished.
 *
 * @throws IllegalArgumentException if [spec] has no duration or repeats: a transition goes from one snapshot to the other once.
 */
class SnapshotTransition(
    private val repository: EntityRepository,
    private val from: Snapshot,
    private val to: Snapshot,
    spec: AnimationSpec,
    private val onFinished: () -> Unit = {},
) : Animatable {
    init {
        require(spec.durationMs > 0) { "a transition needs a duration" }
        require(spec.repeatMode == RepeatMode.Once) { "a transition plays once" }
    }

    private val progress = AnimatedProperty(0f, 1f, spec) { start, end, fraction -> start + (end - start) * fraction }

    var isFinished = false
        private set

    override fun onTick(deltaMs: Long) {
        if (isFinished) return
        progress.onTick(deltaMs)
        if (progress.isFinished) {
            repository.restore(to)
            isFinished = true
            onFinished()
        } else {
            repository.replaceAll(Morph.between(from, to, progress.value))
        }
    }
}
