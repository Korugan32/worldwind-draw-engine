package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.render.Color
import earth.worldwind.render.image.ImageSource
import earth.worldwind.shape.PlacemarkAttributes
import earth.worldwind.shape.ShapeAttributes

/**
 * Converts to WWK's [Color]. Relies on Kotlin resolving [ColorRgba]'s `Int` (0-255) fields
 * to WWK's `Color(Int, Int, Int, Int)` overload, which normalizes each channel to `0f..1f`
 * internally — not the `Color(Float, Float, Float, Float)` overload, which would treat
 * 0-255 values as already-normalized and produce out-of-range colors.
 */
fun ColorRgba.toWwkColor(): Color = Color(red, green, blue, alpha)

/**
 * Converts to [ShapeAttributes] with this style applied to the **interior** — for entities
 * that have a fillable area (currently just `DrawnPolygon`). For a line's own color, use
 * [toWwkLineAttributes] instead.
 */
fun ShapeStyle.toWwkShapeAttributes(): ShapeAttributes {
    return when (this) {
        is ShapeStyle.SolidColor -> ShapeAttributes().apply {
            interiorColor = color.toWwkColor()
        }
        is ShapeStyle.ImagePattern -> ShapeAttributes().apply {
            interiorImageSource = ImageSource.fromUrlString(imageUrl)
        }
        is ShapeStyle.AnimatedFill -> base.toWwkShapeAttributes()
    }
}

/**
 * Converts to [ShapeAttributes] with this style applied to the **outline** at the given
 * [width] (screen pixels) — for entities with no interior of their own (currently just
 * `DrawnPolyline`, whose visible "shape" IS its outline). Mirrors [toWwkShapeAttributes]
 * but writes `outlineColor`/`outlineImageSource`/`outlineWidth` instead of the `interior*`
 * properties.
 */
fun ShapeStyle.toWwkLineAttributes(width: Float): ShapeAttributes {
    return when (this) {
        is ShapeStyle.SolidColor -> ShapeAttributes().apply {
            outlineColor = color.toWwkColor()
            outlineWidth = width
        }
        is ShapeStyle.ImagePattern -> ShapeAttributes().apply {
            outlineImageSource = ImageSource.fromUrlString(imageUrl)
            outlineWidth = width
        }
        is ShapeStyle.AnimatedFill -> base.toWwkLineAttributes(width)
    }
}

/**
 * Converts to [PlacemarkAttributes] with this style applied to the marker's icon at the
 * given [size] (a square's side length in screen pixels, used when there's no image — see
 * [PlacemarkAttributes.imageSource]'s KDoc). Same interior-vs-outline reasoning as
 * [toWwkLineAttributes]: a marker has no interior, so this writes `imageColor`/
 * `imageSource`/`imageScale` rather than `interior*`.
 */
fun ShapeStyle.toWwkPlacemarkAttributes(size: Float): PlacemarkAttributes {
    return when (this) {
        is ShapeStyle.SolidColor -> PlacemarkAttributes().apply {
            imageColor = color.toWwkColor()
            imageScale = size.toDouble()
        }
        is ShapeStyle.ImagePattern -> PlacemarkAttributes.createWithImage(ImageSource.fromUrlString(imageUrl)).apply {
            imageScale = size.toDouble()
        }
        is ShapeStyle.AnimatedFill -> base.toWwkPlacemarkAttributes(size)
    }
}