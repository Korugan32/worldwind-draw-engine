package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.LabelPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.Offset
import earth.worldwind.geom.OffsetMode
import earth.worldwind.render.Font
import earth.worldwind.render.FontWeight
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.TextAttributes

/**
 * Maps a [DrawnMarker] to the WWK renderable that actually draws it on the globe.
 *
 * `altitudeMode = CLAMP_TO_GROUND` for the same reason as in `PolygonMapper.kt`/
 * `PolylineMapper.kt`: a [Placemark] depth-tests against terrain by default
 * ([earth.worldwind.shape.PlacemarkAttributes.isDepthTest] defaults to `true`), so a marker
 * left at its literal `ABSOLUTE` altitude (0 by default) can render behind higher terrain.
 * Clamping anchors it to the terrain surface instead, avoiding the issue at the source.
 *
 * A [DrawnMarker.label] becomes the placemark's own label (see [applyLabel]).
 */
fun DrawnMarker.toWwkPlacemark(): Placemark {
    val markerLabel = label
    return Placemark(position.toWwkPosition(), style.toWwkPlacemarkAttributes(size)).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        if (markerLabel != null) {
            this.label = markerLabel.text
            attributes.labelAttributes.applyLabel(markerLabel, iconSize = size)
        }
    }
}

/** Space left between the icon and its label, in pixels. */
private const val LABEL_GAP = 2.0

/**
 * Makes these text attributes write [label] the way it asks: its colour and outline (the alpha of each is its opacity), its size, and its
 * place against an icon whose longest side is [iconSize] pixels and which sits centred on the marker's position. The label is never
 * hidden by relief, like the marker itself: a label behind a mountain is still wanted.
 */
internal fun TextAttributes.applyLabel(label: MarkerLabel, iconSize: Float) {
    font = Font("sans-serif", FontWeight.NORMAL, label.size)
    textColor = label.color.toWwkColor()
    val outline = label.outlineColor
    isOutlineEnabled = outline != null
    if (outline != null) outlineColor = outline.toWwkColor()
    isDepthTest = false
    textOffset = labelOffset(label.position, iconSize)
}

/**
 * Where the text's corner goes against the marker's position: WWK puts the point of the text rectangle that this offset names on the
 * position (a negative pixel offset moves the text away from it, towards the top or the right). Above and below are measured from the
 * icon's edge, so the text clears it whatever its size.
 */
internal fun labelOffset(position: LabelPosition, iconSize: Float): Offset {
    val clearance = iconSize / 2.0 + LABEL_GAP
    return when (position) {
        LabelPosition.Above -> Offset(OffsetMode.FRACTION, 0.5, OffsetMode.PIXELS, -clearance)
        LabelPosition.Below -> Offset(OffsetMode.FRACTION, 0.5, OffsetMode.INSET_PIXELS, -clearance)
        LabelPosition.Right -> Offset(OffsetMode.PIXELS, -clearance, OffsetMode.FRACTION, 0.5)
        LabelPosition.Left -> Offset(OffsetMode.INSET_PIXELS, -clearance, OffsetMode.FRACTION, 0.5)
        LabelPosition.Center -> Offset.center()
    }
}
