package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * An open line defined by an ordered list of [vertices]. Requires at least 2 vertices —
 * a single point cannot describe a line.
 */
data class DrawnPolyline(
    override val id: EntityId,
    override val style: ShapeStyle,
    val vertices: List<Vertex>
) : DrawableEntity {
    init {
        require(vertices.size >= 2) { "A polyline must have at least 2 vertices" }
    }
}