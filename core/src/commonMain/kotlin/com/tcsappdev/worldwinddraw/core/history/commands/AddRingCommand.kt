package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Adds [ring] to the polygon [entityId] as one more ring of it: another part, or a hole in one (which of the two follows from where
 * it lies, see [DrawnPolygon]). Undo takes it out again. Does nothing if the entity isn't a polygon, or when [ring] has fewer than 3
 * vertices or a vertex id the polygon already has (the polygon would refuse it).
 */
class AddRingCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val ring: List<Vertex>,
) : Command {
    private var added = false

    override fun execute() {
        val polygon = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnPolygon ?: return
        if (ring.size < 3 || polygon.rings.any { existing -> existing.any { v -> ring.any { it.id == v.id } } }) return
        repository.update(polygon.withExtraRing(ring))
        added = true
    }

    override fun undo() {
        if (!added) return
        val polygon = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnPolygon ?: return
        val index = polygon.ringIndexOf(ring.first().id)
        if (index < 0 || polygon.rings.size < 2) return
        repository.update(polygon.withoutRing(index))
        added = false
    }
}
