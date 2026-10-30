package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.roundToInt

// The in-between of two values, `t` of the way from [a] (0) to [b] (1). What cannot have a middle (a text, an image, a list) is not
// blended: it stays [a] up to the half way point and is [b] after it, see [step].

internal fun lerp(a: Double, b: Double, t: Float): Double = a + (b - a) * t

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun <T> step(a: T, b: T, t: Float): T = if (t < 0.5f) a else b

/**
 * Longitude takes the short way round: from 170 to -170 is 20 degrees across the antimeridian, not 340 back across the whole map.
 * The answer stays within -180..180.
 */
internal fun lerpLongitude(a: Double, b: Double, t: Float): Double {
    var delta = b - a
    if (delta > 180.0) delta -= 360.0 else if (delta < -180.0) delta += 360.0
    val longitude = a + delta * t
    return when {
        longitude > 180.0 -> longitude - 360.0
        longitude < -180.0 -> longitude + 360.0
        else -> longitude
    }.coerceIn(-180.0, 180.0)
}

internal fun lerpPosition(a: GeoPosition, b: GeoPosition, t: Float) =
    GeoPosition(lerp(a.latitude, b.latitude, t), lerpLongitude(a.longitude, b.longitude, t), lerp(a.altitude, b.altitude, t))

internal fun lerpColor(a: ColorRgba, b: ColorRgba, t: Float) = ColorRgba(
    lerp(a.red.toFloat(), b.red.toFloat(), t).roundToInt().coerceIn(0, 255),
    lerp(a.green.toFloat(), b.green.toFloat(), t).roundToInt().coerceIn(0, 255),
    lerp(a.blue.toFloat(), b.blue.toFloat(), t).roundToInt().coerceIn(0, 255),
    lerp(a.alpha.toFloat(), b.alpha.toFloat(), t).roundToInt().coerceIn(0, 255),
)

/** A colour that may be missing (no background): blended when both are there, otherwise it changes at the half way point. */
private fun lerpOptionalColor(a: ColorRgba?, b: ColorRgba?, t: Float): ColorRgba? =
    if (a != null && b != null) lerpColor(a, b, t) else step(a, b, t)

/**
 * Blends two looks when they are the same kind of look and differ only in what has a middle: two colours, two opacities. A
 * look that changes kind (a colour to a picture), or to another picture or pattern, changes at the half way point.
 */
internal fun lerpStyle(a: ShapeStyle, b: ShapeStyle, t: Float): ShapeStyle = when {
    a is ShapeStyle.SolidColor && b is ShapeStyle.SolidColor -> ShapeStyle.SolidColor(lerpColor(a.color, b.color, t))
    a is ShapeStyle.ImagePattern && b is ShapeStyle.ImagePattern && a.image == b.image ->
        a.copy(opacity = lerp(a.opacity, b.opacity, t))
    a is ShapeStyle.ImageFill && b is ShapeStyle.ImageFill && a.copy(opacity = b.opacity, backgroundColor = b.backgroundColor) == b ->
        a.copy(opacity = lerp(a.opacity, b.opacity, t), backgroundColor = lerpOptionalColor(a.backgroundColor, b.backgroundColor, t))
    a is ShapeStyle.Hatch && b is ShapeStyle.Hatch && a.copy(color = b.color, backgroundColor = b.backgroundColor) == b ->
        a.copy(color = lerpColor(a.color, b.color, t), backgroundColor = lerpOptionalColor(a.backgroundColor, b.backgroundColor, t))
    else -> step(a, b, t)
}

/** The same for a look that may be missing, like a polygon's outline. */
internal fun lerpOptionalStyle(a: ShapeStyle?, b: ShapeStyle?, t: Float): ShapeStyle? =
    if (a != null && b != null) lerpStyle(a, b, t) else step(a, b, t)

/** [a] and [b] must have the same vertices, with the same ids in the same order. */
internal fun lerpVertices(a: List<Vertex>, b: List<Vertex>, t: Float): List<Vertex> =
    a.zip(b) { from, to -> Vertex(from.id, lerpPosition(from.position, to.position, t), step(from.metadata, to.metadata, t)) }

/** Two labels with the same text, place and size are blended in colour; any other pair changes at the half way point. */
internal fun lerpLabel(a: MarkerLabel?, b: MarkerLabel?, t: Float): MarkerLabel? {
    if (a == null || b == null || a.text != b.text || a.size != b.size || a.position != b.position) return step(a, b, t)
    return a.copy(color = lerpColor(a.color, b.color, t), outlineColor = lerpOptionalColor(a.outlineColor, b.outlineColor, t))
}
