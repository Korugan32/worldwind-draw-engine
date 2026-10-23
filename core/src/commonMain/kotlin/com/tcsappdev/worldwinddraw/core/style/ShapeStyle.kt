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

    /**
     * The shape is filled with [image], repeated as a pattern, drawn at [opacity].
     *
     * What WWK does with it (verified on Android): the image is tiled across the shape and sized
     * in *screen* pixels — a tile comes out about the size of the image's own pixels, not a
     * fixed geographic size. There is no scale setting: WWK doesn't offer one for patterns. The
     * image is drawn unmodified (no tint) and [opacity] fades it: `1f` is as opaque as the image
     * itself, `0f` invisible. (WWK multiplies each image pixel by the fill colour; white with this
     * alpha is what is used, so the image's own transparency still applies on top.)
     *
     * A pattern wants a small tile, ideally one that repeats seamlessly — a photo repeated as a
     * pattern shows seams and a small crop of it, not the whole picture.
     *
     * For [ImageRef.Url] the device must be able to reach it (nothing but `http`/`https` is
     * accepted), a host app on a recent Android must itself allow cleartext traffic for plain
     * `http`, and the image is fetched lazily, so the shape shows nothing until it has downloaded.
     * For [ImageRef.Bytes] there is no network involved. Where the platform can't decode bytes yet
     * (web and iOS for now), or the bytes aren't a decodable image, a neutral grey placeholder fill
     * is drawn instead of the image.
     */
    data class ImagePattern(val image: ImageRef, val opacity: Float = 1f) : ShapeStyle {
        init {
            require(opacity in 0f..1f) { "opacity must be between 0 and 1, was $opacity" }
        }

        /** A pattern from an `http`/`https` URL. */
        constructor(imageUrl: String, opacity: Float = 1f) : this(ImageRef.Url(imageUrl), opacity)
    }

    /**
     * The shape is filled with ONE copy of [image] — placed per [fit], not repeated — drawn at
     * [opacity] and clipped to the shape. This is the fill for a flag, a floor plan or a photo
     * inside a shape; use [ImagePattern] for a small tile repeated across it.
     *
     * The image is laid over the shape's bounding box, centred on it (so for a concave shape the
     * centre can fall outside the shape itself), then clipped to the shape. [fit] decides its size
     * and [scale] shrinks or grows that about the centre (`0.5f` = half the fitted size).
     *
     * Only [ImageRef.Bytes] are supported: the picture has to be in memory to be clipped to the
     * shape, and a URL is only downloaded later. A URL, an undecodable image, a platform with no
     * decoder yet (web and iOS for now) or a shape spanning more than 180 degrees of longitude
     * (e.g. across the antimeridian) all fall back to a neutral grey placeholder fill. On a line or
     * a marker this behaves like [ImagePattern] (the image is the line's or the icon's picture).
     */
    data class ImageFill(
        val image: ImageRef,
        val fit: ImageFit = ImageFit.Contain,
        val scale: Float = 1f,
        val opacity: Float = 1f,
    ) : ShapeStyle {
        init {
            require(scale > 0f) { "scale must be positive, was $scale" }
            require(opacity in 0f..1f) { "opacity must be between 0 and 1, was $opacity" }
        }
    }

    /**
     * Reserved for a fill whose appearance changes over time: [base] is what is drawn today —
     * the animation itself isn't wired to rendering yet (and the water-masking effect it was
     * first meant for is out of scope, see docs/PLAN.md).
     */
    data class AnimatedFill(val base: ShapeStyle, val animationId: String) : ShapeStyle
}

/** How an [ShapeStyle.ImageFill]'s picture is sized against the shape's bounding box. Always centred, aspect ratio kept unless noted. */
enum class ImageFit {
    /** As large as possible while still fitting entirely inside the box: nothing is cut off, the rest of the box stays empty. */
    Contain,

    /** As small as possible while still covering the whole box: the picture fills it, and what overflows is cut off. */
    Cover,

    /** Stretched to exactly the box, ignoring the aspect ratio. */
    Stretch,
}
