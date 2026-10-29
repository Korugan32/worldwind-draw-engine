package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Merging two shapes and cutting one out of another are the same change seen from the repository: the polygon [targetId] takes the new
 * [rings] (its outline is the first, the rest are its other rings, see [DrawnPolygon]) and keeps its id, look, description, drawing order and
 * properties; and, for a merge, the polygon [absorbedId] is gone. When [rings] is empty nothing is left of the target (a cut that took all
 * of it) and it is removed too.
 *
 * The rings are worked out beforehand (by an [com.tcsappdev.worldwinddraw.core.overlay.OverlayEngine], in the reducer), so doing this again
 * after an undo is the same change and costs nothing. Everything happens as ONE change of the repository ([EntityRepository.replaceAll]).
 * Undoing puts the target back as it was and the absorbed shape back in its place in the drawing order, leaving what else changed since alone.
 * The shapes are looked up when this runs; if either is missing or isn't a polygon, nothing happens (and neither does the undo).
 */
class CombineShapesCommand(
    private val repository: EntityRepository,
    private val targetId: EntityId,
    private val rings: List<List<Vertex>>,
    private val absorbedId: EntityId? = null,
) : Command {
    private class Taken(val entity: DrawableEntity, val index: Int)

    private var target: Taken? = null
    private var absorbed: Taken? = null

    override fun execute() {
        val entities = repository.entities.value
        val targetIndex = entities.indexOfFirst { it.id == targetId }
        val targetShape = entities.getOrNull(targetIndex) as? DrawnPolygon ?: return
        var absorbedTaken: Taken? = null
        if (absorbedId != null) {
            val absorbedIndex = entities.indexOfFirst { it.id == absorbedId }
            val absorbedShape = entities.getOrNull(absorbedIndex) as? DrawnPolygon ?: return
            absorbedTaken = Taken(absorbedShape, absorbedIndex)
        }
        target = Taken(targetShape, targetIndex)
        absorbed = absorbedTaken
        val updated = if (rings.isEmpty()) null else targetShape.copy(vertices = rings.first(), extraRings = rings.drop(1))
        repository.replaceAll(entities.mapNotNull { entity ->
            when (entity.id) {
                targetId -> updated
                absorbedId -> null
                else -> entity
            }
        })
    }

    override fun undo() {
        val taken = target ?: return
        val back = listOfNotNull(taken, absorbed).sortedBy { it.index }
        val rest = repository.entities.value.filter { it.id != targetId && it.id != absorbedId }.toMutableList()
        for (shape in back) rest.add(shape.index.coerceAtMost(rest.size), shape.entity)
        repository.replaceAll(rest)
    }
}
