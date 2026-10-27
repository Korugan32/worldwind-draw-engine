package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.raster.HatchTile
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageRef
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
        is ShapeStyle.ImagePattern -> imageInterior(image, opacity)
        is ShapeStyle.Hatch -> hatchInterior(this)
        // The picture is drawn by a separate surface image under the polygon (see DrawLayer), so
        // the polygon's own interior is left invisible — but still there, so it can be picked.
        is ShapeStyle.ImageFill -> ShapeAttributes().apply { interiorColor = Color(1f, 1f, 1f, 0f) }
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
        is ShapeStyle.ImagePattern -> imageOutline(image, opacity, width)
        // A line has no interior to hatch: it is drawn in the hatching's colour.
        is ShapeStyle.Hatch -> ShapeAttributes().apply {
            outlineColor = color.toWwkColor()
            outlineWidth = width
        }
        is ShapeStyle.ImageFill -> imageOutline(image, opacity, width)
        is ShapeStyle.AnimatedFill -> base.toWwkLineAttributes(width)
    }
}

/**
 * Converts to [PlacemarkAttributes] with this style applied to the marker's icon at the
 * given [size] (a square's side length in screen pixels; for an image, the length of its longest side
 * when the image's size is known — see the comment on the image case below). Same interior-vs-outline reasoning as
 * [toWwkLineAttributes]: a marker has no interior, so this writes `imageColor`/
 * `imageSource`/`imageScale` rather than `interior*`.
 */
fun ShapeStyle.toWwkPlacemarkAttributes(size: Float): PlacemarkAttributes {
    return when (this) {
        is ShapeStyle.SolidColor -> PlacemarkAttributes().apply {
            imageColor = color.toWwkColor()
            imageScale = size.toDouble()
        }
        is ShapeStyle.ImagePattern -> imageIcon(image, opacity, size)
        is ShapeStyle.Hatch -> PlacemarkAttributes().apply {
            imageColor = color.toWwkColor()
            imageScale = size.toDouble()
        }
        is ShapeStyle.ImageFill -> imageIcon(image, opacity, size)
        is ShapeStyle.AnimatedFill -> base.toWwkPlacemarkAttributes(size)
    }
}

/** Drawn instead of an image that could not be produced (undecodable bytes, or a platform with no decoder yet). */
private val PLACEHOLDER_COLOR = Color(0.5f, 0.5f, 0.5f, 0.5f)

private fun imageInterior(image: ImageRef, opacity: Float): ShapeAttributes = ShapeAttributes().apply {
    val loaded = image.toLoadedImage()
    if (loaded == null) {
        interiorColor = PLACEHOLDER_COLOR
    } else {
        interiorImageSource = loaded.source
        // WWK multiplies each image pixel by the interior colour: white leaves the pixels
        // untinted and its alpha is the pattern's opacity.
        interiorColor = Color(1f, 1f, 1f, opacity)
    }
}

/**
 * A hatching is a small generated tile drawn as WWK draws an image pattern (tiled in screen pixels). The tile is made once per look
 * and kept: WWK compares an in-memory image by reference, so the same look must hand it the same instance, or its texture cache
 * would be defeated and every shape with that look would carry a texture of its own.
 */
private fun hatchInterior(hatch: ShapeStyle.Hatch): ShapeAttributes = ShapeAttributes().apply {
    val source = HatchSources.get(hatch)
    if (source == null) {
        interiorColor = PLACEHOLDER_COLOR
    } else {
        interiorImageSource = source
        // White leaves the tile's pixels untinted; the lines' own alpha is in the pixels.
        interiorColor = Color(1f, 1f, 1f, 1f)
    }
}

private object HatchSources {
    // Bounded like the other image caches: a host that keeps trying new looks can't grow this without limit.
    private const val MAX_ENTRIES = 16
    private val cache = LinkedHashMap<ShapeStyle.Hatch, ImageSource>()

    fun get(hatch: ShapeStyle.Hatch): ImageSource? {
        cache[hatch]?.let { return it }
        val tile = HatchTile.render(hatch.pattern, hatch.color, hatch.backgroundColor, hatch.spacing, hatch.thickness)
        val source = imageSourceFromPixels(tile) ?: return null
        if (cache.size >= MAX_ENTRIES) cache.remove(cache.keys.first())
        cache[hatch] = source
        return source
    }
}

private fun imageOutline(image: ImageRef, opacity: Float, width: Float): ShapeAttributes = ShapeAttributes().apply {
    outlineWidth = width
    val loaded = image.toLoadedImage()
    if (loaded == null) {
        outlineColor = PLACEHOLDER_COLOR
    } else {
        outlineImageSource = loaded.source
        outlineColor = Color(1f, 1f, 1f, opacity)
    }
}

private fun imageIcon(image: ImageRef, opacity: Float, size: Float): PlacemarkAttributes {
    val loaded = image.toLoadedImage()
    if (loaded == null) {
        return PlacemarkAttributes().apply {
            imageColor = PLACEHOLDER_COLOR
            imageScale = size.toDouble()
        }
    }
    return PlacemarkAttributes.createWithImage(loaded.source).apply {
        imageColor = Color(1f, 1f, 1f, opacity)
        // With an image, WWK's scale is a MULTIPLIER of the image's own pixels (without one it is the
        // square's side in pixels). So `size` can only be honoured as pixels when the image's size is
        // known — decoded bytes; a URL is only downloaded later, so it is drawn at its natural size.
        imageScale = if (loaded.width > 0) size.toDouble() / maxOf(loaded.width, loaded.height) else 1.0
    }
}
