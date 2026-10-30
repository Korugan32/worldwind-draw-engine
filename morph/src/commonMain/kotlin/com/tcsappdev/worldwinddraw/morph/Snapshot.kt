package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * The drawing at one moment: every shape, in drawing order. The shapes are immutable, so a snapshot is not a copy of anything, it is
 * the list itself, and holding one costs nothing. To keep one beyond the run of the app, write it with the `serialization` module
 * (`DrawJson`).
 *
 * Two snapshots are what [Morph] works between and what a [SnapshotTransition] plays from one to the other.
 */
data class Snapshot(val entities: List<DrawableEntity>) {
    init {
        require(entities.map { it.id }.toSet().size == entities.size) { "two shapes have the same id" }
    }
}

/** The drawing as it is now. */
fun EntityRepository.snapshot(): Snapshot = Snapshot(entities.value)

/**
 * Puts the drawing back as [snapshot] has it, in one change. This is not an edit: nothing is added to the undo history, and the
 * shapes the drawing had before are gone (take a [snapshot] first to keep them).
 */
fun EntityRepository.restore(snapshot: Snapshot) = replaceAll(snapshot.entities)
