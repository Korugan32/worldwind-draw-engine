package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * A closed, filled shape defined by an ordered ring of [vertices]. Requires at least 3
 * vertices — fewer points cannot describe an enclosed area.
 *
 * Unlike [DrawnPolyline]/[DrawnMarker], a polygon has both an interior AND a border, so it
 * needs two independent styles: [style] (inherited from [DrawableEntity]) fills the
 * interior, [outlineStyle] draws the border. They're separate because either can vary on
 * its own (e.g. same fill color, thicker border when selected).
 *
 * @property outlineStyle the border's color, or `null` for no visible border at all
 * (`isDrawOutline = false` — a deliberate choice, not "unspecified", so a polygon nobody
 * asked to have a border doesn't render WWK's default one).
 * @property outlineWidth the border's thickness in screen pixels, must be greater than 0.
 * Only meaningful when [outlineStyle] is non-null.
 */
data class DrawnPolygon(
    override val id: EntityId,
    override val style: ShapeStyle,
    val vertices: List<Vertex>,
    val outlineStyle: ShapeStyle? = null,
    val outlineWidth: Float = 1f,
) : DrawableEntity {
    init {
        require(vertices.size >= 3) { "A polygon must have at least 3 vertices" }
        require(outlineWidth > 0f) { "outlineWidth must be greater than 0" }
    }
}