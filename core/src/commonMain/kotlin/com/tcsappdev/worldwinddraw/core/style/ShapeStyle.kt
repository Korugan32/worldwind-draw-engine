package com.tcsappdev.worldwinddraw.core.style

/**
 * A color expressed as four 0-255 channels. `core` defines its own color type instead of
 * reusing a platform color class (e.g. Android's `Color`), since `core` must stay free of
 * any platform/UI dependency.
 */
data class ColorRgba(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = 255,
) {
    init {
        require(red in 0..255) { "Red must be between 0 and 255" }
        require(green in 0..255) { "Green must be between 0 and 255" }
        require(blue in 0..255) { "Blue must be between 0 and 255" }
        require(alpha in 0..255) { "Alpha must be between 0 and 255" }
    }
}

/**
 * A shape's primary appearance — its interior fill for shapes that have one (e.g.
 * [com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon]), or its stroke/line color for
 * shapes that don't (e.g. [com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker]).
 * [DrawableEntity][com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity] deliberately
 * has just one `style: ShapeStyle` field shared by every entity kind (see
 * `ChangeStyleCommand`'s KDoc) — which of "fill" or "stroke" a given [ShapeStyle] resolves
 * to is a `wwk-adapter` mapper decision (interior for a polygon, outline for a polyline),
 * not something `core` encodes.
 *
 * Sealed so every place that renders a [ShapeStyle] (e.g. a `when` in `wwk-adapter`) is
 * guaranteed by the compiler to handle all kinds below.
 *
 * No gradient fill: dropped deliberately in Faz 5 — WWK's `ShapeAttributes` only has a
 * single `interiorColor`, so a real gradient would need custom shader work that isn't
 * worth it for a feature judged not important enough to invest in right now.
 */
sealed interface ShapeStyle {
    /** A single, flat color. */
    data class SolidColor(val color: ColorRgba) : ShapeStyle

    /** An image/texture, at the given [scale] (`1f` = original size). */
    data class ImagePattern(val imageUrl: String, val scale: Float = 1f) : ShapeStyle

    /** A [base] style whose appearance is animated over time (e.g. the water-masking effect). */
    data class AnimatedFill(val base: ShapeStyle, val animationId: String) : ShapeStyle
}