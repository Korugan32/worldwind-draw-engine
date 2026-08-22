package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.shape.Path

/**
 * Maps a [DrawnPolyline] to the WWK renderable that actually draws it on the globe.
 *
 * `altitudeMode`/`isFollowTerrain` are set for the same reason as in `PolygonMapper.kt`:
 * without both, WWK depth-tests the line against 3D terrain and it can vanish behind
 * higher ground even though it "exists".
 */
fun DrawnPolyline.toWwkPolyline(): Path =
    Path(vertices.map { it.position.toWwkPosition() }, style.toWwkLineAttributes(lineWidth)).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isFollowTerrain = true
    }
