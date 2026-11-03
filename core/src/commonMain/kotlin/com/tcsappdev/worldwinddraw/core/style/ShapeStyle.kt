package com.tcsappdev.worldwinddraw.core.style

/**
 * A color expressed as four 0-255 channels. `core` defines its own color type instead of
 * reusing a platform color class (e.g. Android's `Color`), since `core` must stay free of
 * any platform/UI dependency.
 */
public data class ColorRgba(
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
public sealed interface ShapeStyle {
    /** A single, flat color. */
    public data class SolidColor(val color: ColorRgba) : ShapeStyle

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
    public data class ImagePattern(val image: ImageRef, val opacity: Float = 1f) : ShapeStyle {
        init {
            require(opacity in 0f..1f) { "opacity must be between 0 and 1, was $opacity" }
        }

        /** A pattern from an `http`/`https` URL. */
        public constructor(imageUrl: String, opacity: Float = 1f) : this(ImageRef.Url(imageUrl), opacity)
    }

    /**
     * The shape is filled with ONE copy of [image] — placed per [fit], not repeated — drawn at
     * [opacity] and clipped to the shape. This is the fill for a flag, a floor plan or a photo
     * inside a shape; use [ImagePattern] for a small tile repeated across it.
     *
     * The image is laid over the shape's bounding box, centred on it (so for a concave shape the
     * centre can fall outside the shape itself), then clipped to the shape. [fit] decides its size
     * and [scale] shrinks or grows that about the centre (`0.5f` = half the fitted size); with
     * [ImageFit.Inscribed] the picture goes in the shape's visual centre instead.
     *
     * [backgroundColor], when set, fills the rest of the shape under the picture: the part a picture
     * that doesn't cover the shape leaves empty (the sides a [ImageFit.Contain] picture doesn't reach,
     * all of the shape around an [ImageFit.Inscribed] one). Its own alpha is its opacity within the fill. The picture and the
     * background are blended first and [opacity] then fades the two together, so a background of the picture's main colour
     * (a flag's red) runs seamlessly into it. `null` leaves the shape empty there, so the map shows through.
     *
     * [extendEdges] fills that empty part from the picture itself instead: its outermost row or column of pixels carries on
     * outwards, so a flag's own colours continue past it (a plain red flag turns the shape red, stripes keep running) and no
     * colour has to be picked by hand. The background colour is then laid under it and only shows through a transparent picture.
     * It does nothing with [repeat], which leaves no empty part.
     *
     * [edgeSoftness] fades the picture out towards its own edges instead of cutting it off sharply: `0f` is the sharp edge, `1f` fades
     * from the edge all the way to the middle. What shows through is what is under the picture, the [backgroundColor] or the map, so
     * the picture melts into it. The fade is a smooth curve over a band of that fraction of half the picture's shorter side. With
     * [repeat] every copy fades at its own edges; with [extendEdges] there is nothing to fade into and it has no effect.
     *
     * [anchorX]/[anchorY] move the picture: where its centre goes in the shape's bounding box, as fractions of it (0 is the west
     * and north edge, 1 the east and south edge, 0.5 the middle). `null` keeps where [fit] puts it (the middle of the box, or the
     * visual centre for [ImageFit.Inscribed]); either can be set alone. With [repeat] the picture, sized by [fit] and [scale],
     * is tiled across the shape instead of placed once, one copy centred where it would have been. The tiles are laid on the ground
     * (they grow and shrink with the map and keep the same number across the shape), not on the screen like an [ImagePattern]'s.
     *
     * Only [ImageRef.Bytes] are supported: the picture has to be in memory to be clipped to the
     * shape, and a URL is only downloaded later. A URL, an undecodable image, a platform with no
     * decoder yet (web and iOS for now) or a shape spanning more than 180 degrees of longitude
     * (e.g. across the antimeridian) all fall back to a neutral grey placeholder fill. On a line or
     * a marker this behaves like [ImagePattern] (the image is the line's or the icon's picture).
     */
    public data class ImageFill(
        val image: ImageRef,
        val fit: ImageFit = ImageFit.Contain,
        val scale: Float = 1f,
        val opacity: Float = 1f,
        val backgroundColor: ColorRgba? = null,
        val repeat: Boolean = false,
        val anchorX: Float? = null,
        val anchorY: Float? = null,
        val extendEdges: Boolean = false,
        val edgeSoftness: Float = 0f,
    ) : ShapeStyle {
        init {
            require(edgeSoftness in 0f..1f) { "edgeSoftness must be between 0 and 1, was $edgeSoftness" }
            require(anchorX == null || anchorX.isFinite()) { "anchorX must be a finite number, was $anchorX" }
            require(anchorY == null || anchorY.isFinite()) { "anchorY must be a finite number, was $anchorY" }
            require(scale > 0f) { "scale must be positive, was $scale" }
            require(opacity in 0f..1f) { "opacity must be between 0 and 1, was $opacity" }
        }
    }

    /**
     * The shape is filled with a hatching: lines, a cross or dots in [color], one tile of [spacing] pixels repeated across it, with
     * [backgroundColor] (when set) behind them. The gaps stay empty otherwise, so the map shows through, which is what makes it
     * fit for marking an area (disputed, occupied, restricted) without hiding what is under it.
     *
     * Like an [ImagePattern], which is what it is drawn as, the tile is laid out in *screen* pixels: zooming the map doesn't change the
     * hatching's size or spacing, only the shape under it. So [spacing] and [thickness] are pixels of the display, not of a map
     * scale; a host that wants the same look on every screen density scales them by its density.
     *
     * [spacing] is the tile's side: along a horizontal or vertical line it is the distance to the next line, along a diagonal one the
     * distance measured along an axis (the lines themselves are `spacing / sqrt(2)` apart). [thickness] is a line's width, or the
     * diameter of a dot. On a line or a marker the hatching has nothing to fill, so [color] is used as a plain colour there.
     */
    public data class Hatch(
        val pattern: HatchPattern,
        val color: ColorRgba,
        val backgroundColor: ColorRgba? = null,
        val spacing: Int = 12,
        val thickness: Float = 2f,
    ) : ShapeStyle {
        init {
            require(spacing in 4..256) { "spacing must be between 4 and 256 pixels, was $spacing" }
            require(thickness > 0f && thickness <= spacing) { "thickness must be positive and at most the spacing ($spacing), was $thickness" }
        }
    }
}

/** How an [ShapeStyle.ImageFill]'s picture is sized against the shape's bounding box. Always centred, aspect ratio kept unless noted. */
public enum class ImageFit {
    /** As large as possible while still fitting entirely inside the box: nothing is cut off, the rest of the box stays empty. */
    Contain,

    /** As small as possible while still covering the whole box: the picture fills it, and what overflows is cut off. */
    Cover,

    /** Stretched to exactly the box, ignoring the aspect ratio. */
    Stretch,

    /**
     * One whole picture in the middle of the shape, not tied to its box: centred on the shape's visual centre
     * (the point farthest from its edges, see `PolygonCenter`) and just big enough to touch the biggest circle that fits
     * inside the shape, so it is shown entire, never cut and never repeated. A long, bent or lopsided shape (a country
     * with a long arm) gets its picture in its body rather than in the middle of its bounding box, which can lie outside
     * the shape. [ImageFill.scale] sizes it from there: 1 is the whole picture inside that circle, more makes it bigger
     * and lets the shape cut what sticks out. The first ring given is the outline it is centred in.
     */
    Inscribed,
}

/** What a [ShapeStyle.Hatch] is drawn with. */
public enum class HatchPattern {
    /** Lines rising to the right, like `/`. */
    DiagonalUp,

    /** Lines falling to the right, like `\`. */
    DiagonalDown,

    /** Horizontal lines. */
    Horizontal,

    /** Vertical lines. */
    Vertical,

    /** Both diagonals, crossing (a lattice of diamonds). */
    Cross,

    /** A dot in the middle of every tile. */
    Dots,
}
