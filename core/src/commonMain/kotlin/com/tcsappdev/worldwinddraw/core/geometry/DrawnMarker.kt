package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * A single point on the globe (e.g. a pin), with no vertex list of its own.
 *
 * Like [DrawnPolyline], a marker has no interior — [style] is applied to the marker's
 * icon/dot color (see [com.tcsappdev.worldwinddraw.core.style.ShapeStyle]'s KDoc).
 *
 * @property size the icon's on-screen size in pixels, must be greater than 0. A renderer
 * without a real icon (a plain [com.tcsappdev.worldwinddraw.core.style.ShapeStyle.SolidColor])
 * draws a [size]x[size] square in that color, so this can't default to something
 * imperceptibly small.
 */
data class DrawnMarker(
    override val id: EntityId,
    override val style: ShapeStyle,
    val position: GeoPosition,
    val size: Float = 16f,
) : DrawableEntity {
    init {
        require(size > 0f) { "size must be greater than 0" }
    }
}