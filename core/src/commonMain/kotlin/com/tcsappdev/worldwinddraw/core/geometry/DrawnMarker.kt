package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/** A single point on the globe (e.g. a pin), with no vertex list of its own. */
data class DrawnMarker(
    override val id: EntityId,
    override val style: ShapeStyle,
    val position: GeoPosition
) : DrawableEntity