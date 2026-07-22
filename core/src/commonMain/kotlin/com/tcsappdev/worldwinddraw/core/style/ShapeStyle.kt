package com.tcsappdev.worldwinddraw.core.style

/**
 * A color expressed as four 0-255 channels. `core` defines its own color type instead of
 * reusing a platform color class (e.g. Android's `Color`), since `core` must stay free of
 * any platform/UI dependency.
 */
data class ColorRgba(
    val red: Int,
    val blue: Int,
    val green: Int,
    val alpha: Int = 255,
) {
    init {
        require(red in 0..255) { "Red must be between 0 and 255" }
        require(blue in 0..255) { "Blue must be between 0 and 255" }
        require(green in 0..255) { "Green must be between 0 and 255" }
        require(alpha in 0..255) { "Alpha must be between 0 and 255" }
    }
}

/**
 * What the interior of a shape looks like. Sealed so every place that renders a
 * [ShapeStyle] (e.g. a `when` in `wwk-adapter`) is guaranteed by the compiler to handle
 * all four kinds below.
 */
sealed interface ShapeStyle {
    /** A single, flat fill color. */
    data class SolidColor(val color: ColorRgba) : ShapeStyle

    /** A blend across two or more colors at the given angle. */
    data class Gradient(val colors: List<ColorRgba>, val angleDegrees: Float = 0f) : ShapeStyle {
        init {
            require(colors.size >= 2) { "At least two colors are required for a gradient" }
        }
    }

    /** An image/texture used as the fill, at the given [scale] (`1f` = original size). */
    data class ImagePattern(val imageUrl: String, val scale: Float = 1f) : ShapeStyle

    /** A [base] style whose appearance is animated over time (e.g. the water-masking effect). */
    data class AnimatedFill(val base: ShapeStyle, val animationId: String) : ShapeStyle
}