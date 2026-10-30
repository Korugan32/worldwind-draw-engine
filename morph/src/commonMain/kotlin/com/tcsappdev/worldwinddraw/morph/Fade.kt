package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.roundToInt

// Making a shape fainter: what a crossfade is made of. [factor] is how much of the opacity is kept (1 all, 0 none).

private fun ColorRgba.faded(factor: Float) = copy(alpha = (alpha * factor).roundToInt().coerceIn(0, 255))

private fun ShapeStyle.faded(factor: Float): ShapeStyle = when (this) {
    is ShapeStyle.SolidColor -> copy(color = color.faded(factor))
    is ShapeStyle.ImagePattern -> copy(opacity = opacity * factor)
    is ShapeStyle.ImageFill -> copy(opacity = opacity * factor, backgroundColor = backgroundColor?.faded(factor))
    is ShapeStyle.Hatch -> copy(color = color.faded(factor), backgroundColor = backgroundColor?.faded(factor))
}

private fun MarkerLabel.faded(factor: Float) = copy(color = color.faded(factor), outlineColor = outlineColor?.faded(factor))

internal fun DrawableEntity.faded(factor: Float): DrawableEntity {
    val f = factor.coerceIn(0f, 1f)
    return when (this) {
        is DrawnPolygon -> copy(style = style.faded(f), outlineStyle = outlineStyle?.faded(f))
        is DrawnPolyline -> copy(style = style.faded(f))
        is DrawnMarker -> copy(style = style.faded(f), label = label?.faded(f))
    }
}
