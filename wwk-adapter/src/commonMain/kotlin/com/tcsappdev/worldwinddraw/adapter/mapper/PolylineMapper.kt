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
 *
 * A line that has some altitude (a vertex above `0.0`) is not on the ground: its altitude is the height in meters above the ground
 * ([AltitudeMode.RELATIVE_TO_GROUND]), so a route flown by a plane can arch up into the air and back.
 */
fun DrawnPolyline.toWwkPolyline(): Path {
    val inTheAir = vertices.any { it.position.altitude > 0.0 }
    return Path(densifiedWwkPositions(vertices.map { it.position }, isClosed = false), style.toWwkLineAttributes(lineWidth)).apply {
        altitudeMode = if (inTheAir) AltitudeMode.RELATIVE_TO_GROUND else AltitudeMode.CLAMP_TO_GROUND
        isFollowTerrain = !inTheAir
        disableEdgeSubdivision(this)
    }
}
