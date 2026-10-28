package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * An open line defined by an ordered list of [vertices]. Requires at least 2 vertices —
 * a single point cannot describe a line.
 *
 * A polyline has no interior, so [style] is applied to the line itself (its stroke) rather
 * than a fill — see [ShapeStyle]'s KDoc for how a mapper resolves this per entity type.
 *
 * @property lineWidth the stroke's thickness in screen pixels, must be greater than 0.
 */
data class DrawnPolyline(
    override val id: EntityId,
    override val style: ShapeStyle,
    val vertices: List<Vertex>,
    val lineWidth: Float = 1f,
    override val description: String? = null,
    override val zIndex: Int = 0,
    override val properties: Map<String, String> = emptyMap(),
) : DrawableEntity {
    init {
        require(vertices.size >= 2) { "A polyline must have at least 2 vertices" }
        require(lineWidth > 0f) { "lineWidth must be greater than 0" }
    }
}