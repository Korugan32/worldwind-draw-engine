package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.shape.Path
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.Polygon

/**
 * A separate, outline-only [Polygon] drawn *on top of* the entity's own — so the selection
 * highlight changes how the shape's border looks without ever touching its fill (a solid
 * colour or an image pattern) or the user's own [DrawnPolygon.outlineStyle]. Always wider
 * than that outline so it visibly dominates.
 *
 * Not pickable ([Polygon.isPickEnabled] is `false`): WWK skips it during the pick pass, so a
 * tap on the highlighted border still resolves to the real polygon underneath instead of
 * to an untagged renderable `PickInputBridge` wouldn't recognise.
 */
internal fun DrawnPolygon.toWwkHighlight(color: ColorRgba): Polygon {
    val attributes = ShapeStyle.SolidColor(color).toWwkLineAttributes(outlineWidth + HIGHLIGHT_EXTRA_WIDTH).apply {
        isDrawInterior = false
    }
    return Polygon(densifiedWwkPositions(vertices.map { it.position }, isClosed = true), attributes).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isFollowTerrain = true
        disableEdgeSubdivision(this)
        for (ring in extraRings) addBoundary(densifiedWwkPositions(ring.map { it.position }, isClosed = true))
        isPickEnabled = false
    }
}

/**
 * A wider [Path] meant to be added *underneath* the entity's own line, so it shows as a halo
 * on both sides of it. A polyline's style IS its stroke (see [ShapeStyle]'s KDoc), so
 * recolouring it would replace the user's own colour — a halo leaves it intact. Not
 * pickable, for the same reason as [toWwkHighlight] on a polygon.
 */
internal fun DrawnPolyline.toWwkHighlight(color: ColorRgba): Path {
    return Path(
        densifiedWwkPositions(vertices.map { it.position }, isClosed = false),
        ShapeStyle.SolidColor(color).toWwkLineAttributes(lineWidth + HIGHLIGHT_EXTRA_WIDTH * 1.5f),
    ).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isFollowTerrain = true
        disableEdgeSubdivision(this)
        isPickEnabled = false
    }
}

/**
 * A larger square [Placemark] meant to be added *underneath* the marker, so it shows as a
 * frame around it. Not pickable, for the same reason as [toWwkHighlight] on a polygon.
 *
 */
internal fun DrawnMarker.toWwkHighlight(color: ColorRgba): Placemark {
    return Placemark(
        position.toWwkPosition(),
        ShapeStyle.SolidColor(color).toWwkPlacemarkAttributes(size + HIGHLIGHT_EXTRA_WIDTH * 2.5f),
    ).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isPickEnabled = false
    }
}

private const val HIGHLIGHT_EXTRA_WIDTH = 4f

