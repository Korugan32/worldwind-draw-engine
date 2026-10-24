package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.shape.Polygon

/**
 * Maps a [DrawnPolygon] to the WWK renderable that actually draws it on the globe.
 *
 * [Polygon.altitudeMode] and [Polygon.isFollowTerrain] are set together deliberately: WWK
 * only treats a shape as a terrain-draped "surface shape" (rendered flat against the
 * ground, ignoring elevation) when *both* are set this way. With either left at its
 * default, the polygon is placed at literal sea-level height and normal terrain (e.g.
 * mountains) depth-tests it away — invisible even though it "exists".
 *
 * Long edges get their great-circle points from [densifiedWwkPositions] rather than from WWK, which would set aside memory for
 * 63 of them on every edge (see there).
 *
 * [outlineStyle]/[outlineWidth] reuse [toWwkLineAttributes] (the same conversion
 * [DrawnPolyline] uses for its stroke) and get merged onto the interior attributes
 * [toWwkShapeAttributes] already built — a `Polygon`'s [Polygon.attributes] is one object
 * carrying both interior and outline settings, unlike `core`'s model where they're two
 * separate styles (see [DrawnPolygon]'s KDoc for why).
 */
fun DrawnPolygon.toWwkPolygon(): Polygon =
    Polygon(densifiedWwkPositions(vertices.map { it.position }, isClosed = true), style.toWwkShapeAttributes()).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isFollowTerrain = true
        disableEdgeSubdivision(this)
        val outline = outlineStyle
        if (outline != null) {
            val lineAttributes = outline.toWwkLineAttributes(outlineWidth)
            attributes.isDrawOutline = true
            attributes.outlineColor = lineAttributes.outlineColor
            attributes.outlineImageSource = lineAttributes.outlineImageSource
            attributes.outlineWidth = lineAttributes.outlineWidth
        } else {
            attributes.isDrawOutline = false
        }
    }
