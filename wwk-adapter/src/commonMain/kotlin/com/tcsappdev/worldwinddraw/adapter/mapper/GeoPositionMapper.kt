package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import earth.worldwind.geom.Position

/** Converts to the WWK position type every renderable/camera API in `wwk-adapter` expects. */
fun GeoPosition.toWwkPosition(): Position = Position.fromDegrees(latitude, longitude, altitude)

/** Converts a WWK [Position] back to our platform-independent [GeoPosition]. */
fun Position.toGeoPosition(): GeoPosition = GeoPosition(latitude.inDegrees, longitude.inDegrees, altitude)