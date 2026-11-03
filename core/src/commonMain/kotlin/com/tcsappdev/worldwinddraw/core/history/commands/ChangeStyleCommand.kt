package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.*
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * Changes [entityId]'s style between [oldStyle] and [newStyle]. Unlike the vertex
 * commands, this applies to all three [DrawableEntity] subtypes — `style` is part of the
 * shared [DrawableEntity] contract, so no type has to be skipped here.
 */
internal class ChangeStyleCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val oldStyle: ShapeStyle,
    private val newStyle: ShapeStyle,
) : Command {
    override fun execute() = changeStyleTo(newStyle)
    override fun undo() = changeStyleTo(oldStyle)

    private fun changeStyleTo(style: ShapeStyle) {
        val entity = repository.entities.value.first { it.id == entityId }
        val updatedEntity = when (entity) {
            is DrawnPolygon -> entity.copy(style = style)
            is DrawnPolyline -> entity.copy(style = style)
            is DrawnMarker -> entity.copy(style = style)
        }
        repository.update(updatedEntity)
    }
}