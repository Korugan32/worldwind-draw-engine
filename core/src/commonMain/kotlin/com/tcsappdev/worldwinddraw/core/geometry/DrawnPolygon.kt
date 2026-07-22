package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * A closed, filled shape defined by an ordered ring of [vertices]. Requires at least 3
 * vertices — fewer points cannot describe an enclosed area.
 */
data class DrawnPolygon(
    override val id: EntityId,
    override val style: ShapeStyle,
    val vertices: List<Vertex>,
) : DrawableEntity {
    init {
        require(vertices.size >= 3) { "A polygon must have at least 3 vertices" }
    }
}