package com.tcsappdev.worldwinddraw.core.history.commands

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.history.Command
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository

/**
 * Gives the [DrawnMarker] [entityId] the label [newLabel] (`null` takes its label away); undoing puts back the label it had when
 * this ran. The old label is read at that moment, like [ResizeMarkerCommand] reads the old size, so the pure reducer can build the
 * command without reading the repository. Does nothing if [entityId] isn't a marker (or no longer exists).
 */
class SetMarkerLabelCommand(
    private val repository: EntityRepository,
    private val entityId: EntityId,
    private val newLabel: MarkerLabel?,
) : Command {
    private var oldLabel: MarkerLabel? = null
    private var applied = false

    override fun execute() {
        val marker = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnMarker ?: return
        oldLabel = marker.label
        applied = true
        repository.update(marker.copy(label = newLabel))
    }

    override fun undo() {
        if (!applied) return
        val marker = repository.entities.value.firstOrNull { it.id == entityId } as? DrawnMarker ?: return
        repository.update(marker.copy(label = oldLabel))
    }
}
