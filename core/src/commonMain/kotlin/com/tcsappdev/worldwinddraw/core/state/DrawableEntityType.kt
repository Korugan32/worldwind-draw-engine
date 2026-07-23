package com.tcsappdev.worldwinddraw.core.state

/**
 * Which kind of shape is being drawn. Needed while a shape is still in progress (see
 * [DrawMode.Draw]) — before enough points exist to construct a real
 * [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon],
 * [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline], or
 * [com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker], there is no
 * [com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity] instance yet to ask.
 */
enum class DrawableEntityType {
    Polygon,
    Polyline,
    Marker
}