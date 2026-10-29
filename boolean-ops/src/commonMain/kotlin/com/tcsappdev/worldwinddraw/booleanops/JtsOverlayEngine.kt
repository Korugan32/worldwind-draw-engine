package com.tcsappdev.worldwinddraw.booleanops

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.OverlayEngine

/**
 * The [OverlayEngine] that merges and cuts with JTS (see [PolygonOverlay], whose grid it uses): what a host gives the
 * `DrawStateMachine` to make `DrawEvent.MergeWith` and `DrawEvent.CutBy` work.
 *
 * ```
 * val machine = DrawStateMachine(history, repository, overlay = JtsOverlayEngine())
 * ```
 *
 * @param gridSize the size of a cell of the grid the overlay is done on, in degrees (see [PolygonOverlay]); the default closes the gaps and
 * overlaps of 1e-7 degrees (about a centimetre) that two sources of the same border leave between them.
 */
class JtsOverlayEngine(private val gridSize: Double = PolygonOverlay.DEFAULT_GRID_SIZE) : OverlayEngine {
    init {
        require(gridSize >= 0.0 && gridSize.isFinite()) { "gridSize must be 0 or more, was $gridSize" }
    }

    override fun validate(polygon: DrawnPolygon): String? = PolygonOverlay.validate(polygon)

    override fun union(a: DrawnPolygon, b: DrawnPolygon, newVertexId: () -> VertexId): List<List<Vertex>> =
        PolygonOverlay.union(a, b, gridSize, newVertexId)

    override fun difference(a: DrawnPolygon, b: DrawnPolygon, newVertexId: () -> VertexId): List<List<Vertex>> =
        PolygonOverlay.difference(a, b, gridSize, newVertexId)
}
