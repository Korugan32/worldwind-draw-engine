package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.shape.Placemark

/**
 * Maps a [DrawnMarker] to the WWK renderable that actually draws it on the globe.
 *
 * `altitudeMode = CLAMP_TO_GROUND` for the same reason as in `PolygonMapper.kt`/
 * `PolylineMapper.kt`: a [Placemark] depth-tests against terrain by default
 * ([earth.worldwind.shape.PlacemarkAttributes.isDepthTest] defaults to `true`), so a marker
 * left at its literal `ABSOLUTE` altitude (0 by default) can render behind higher terrain.
 * Clamping anchors it to the terrain surface instead, avoiding the issue at the source.
 */
fun DrawnMarker.toWwkPlacemark(): Placemark =
    Placemark(position.toWwkPosition(), style.toWwkPlacemarkAttributes(size)).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
    }
