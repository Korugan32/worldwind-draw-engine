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
 *
 * @property rotation how far the icon is turned, in degrees clockwise from north (0 as it is; an icon of a plane that points up, turned to
 * 90, points east). It is turned on the globe, so it keeps its direction as the map is turned. Any number is accepted (450 is 90).
 * @property label text written next to the icon, or `null` for none (see [MarkerLabel]). For a marker that is only text, give it a
 * [style] that is fully transparent and a label whose position is [LabelPosition.Center].
 */
public data class DrawnMarker(
    override val id: EntityId,
    override val style: ShapeStyle,
    val position: GeoPosition,
    val size: Float = 16f,
    val label: MarkerLabel? = null,
    override val description: String? = null,
    override val zIndex: Int = 0,
    override val properties: Map<String, String> = emptyMap(),
    val rotation: Float = 0f,
) : DrawableEntity {
    init {
        require(size > 0f) { "size must be greater than 0" }
        require(rotation.isFinite()) { "rotation must be a number" }
    }
}