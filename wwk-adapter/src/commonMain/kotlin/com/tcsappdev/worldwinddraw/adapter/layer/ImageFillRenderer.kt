package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.MAX_IMAGE_DIMENSION
import com.tcsappdev.worldwinddraw.adapter.mapper.decodePixels
import com.tcsappdev.worldwinddraw.adapter.mapper.imageSourceFromPixels
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.raster.ImageFillRasterizer
import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.Sector
import earth.worldwind.render.Renderable
import earth.worldwind.render.image.ImageSource
import earth.worldwind.shape.SurfaceImage

/**
 * The renderable that draws a polygon's [ShapeStyle.ImageFill]: the picture, placed and cut to
 * the shape by [ImageFillRasterizer], laid on the terrain over the shape's bounding box as a
 * `SurfaceImage` (a decal, so it follows the terrain and its transparent pixels show the map).
 * It is meant to go *under* the polygon, which stays on top for its outline and for picking.
 *
 * Returns `null` when the picture can't be produced, in which case the caller draws a placeholder:
 * the image isn't in memory ([ImageRef.Url] — a URL is only downloaded later), can't be decoded,
 * the platform has no decoder yet, or the shape isn't drawable as one box (see [ImageFillRasterizer.render]).
 *
 * Producing the picture costs tens of milliseconds, so the result is kept (bounded) under the inputs
 * that determine it and is reused until the shape, its picture or its settings change.
 */
internal fun imageFillSurface(polygon: DrawnPolygon, fill: ShapeStyle.ImageFill): Renderable? {
    val bytes = fill.image as? ImageRef.Bytes ?: return null
    val ring = polygon.vertices.map { it.position }
    val key = FillKey(bytes.key, fill.fit, fill.scale, fill.opacity, fill.backgroundColor, fill.repeat, fill.anchorX, fill.anchorY, fill.extendEdges, fill.edgeSoftness, ring)

    val cached = FillCache.results[key]
    val product = cached ?: run {
        val source = FillCache.pixels(bytes) ?: return null
        val rasterized = ImageFillRasterizer.render(
            source, listOf(ring), fill.fit, fill.scale, fill.opacity, MAX_IMAGE_DIMENSION, fill.backgroundColor, fill.repeat, fill.anchorX, fill.anchorY, fill.extendEdges, fill.edgeSoftness,
        )
            ?: return null
        val imageSource = imageSourceFromPixels(rasterized.image) ?: return null
        FillProduct(
            imageSource,
            rasterized.minLatitude,
            rasterized.minLongitude,
            rasterized.maxLatitude - rasterized.minLatitude,
            rasterized.maxLongitude - rasterized.minLongitude,
        ).also { FillCache.put(key, it) }
    }
    return SurfaceImage(
        Sector.fromDegrees(product.minLatitude, product.minLongitude, product.latitudeSpan, product.longitudeSpan),
        product.imageSource,
    ).apply { isPickEnabled = false }
}

private data class FillKey(
    val imageKey: String,
    val fit: ImageFit,
    val scale: Float,
    val opacity: Float,
    val background: ColorRgba?,
    val repeat: Boolean,
    val anchorX: Float?,
    val anchorY: Float?,
    val extendEdges: Boolean,
    val edgeSoftness: Float,
    val ring: List<GeoPosition>,
)

private class FillProduct(
    val imageSource: ImageSource,
    val minLatitude: Double,
    val minLongitude: Double,
    val latitudeSpan: Double,
    val longitudeSpan: Double,
)

private object FillCache {
    // Bounded so a host that keeps editing shapes can't grow memory without limit (each result is a
    // bitmap, up to about 4 MB). When full the oldest goes and is simply produced again if needed.
    private const val MAX_RESULTS = 8
    private const val MAX_DECODED = 4

    val results = LinkedHashMap<FillKey, FillProduct>()
    private val decoded = LinkedHashMap<String, PixelImage>()

    fun put(key: FillKey, product: FillProduct) {
        if (results.size >= MAX_RESULTS) results.remove(results.keys.first())
        results[key] = product
    }

    /** The decoded pixels of [bytes], decoded once per key (the same bounded-cache reasoning as for in-memory images). */
    fun pixels(bytes: ImageRef.Bytes): PixelImage? {
        decoded[bytes.key]?.let { return it }
        val pixels = decodePixels(bytes.data, MAX_IMAGE_DIMENSION) ?: return null
        if (decoded.size >= MAX_DECODED) decoded.remove(decoded.keys.first())
        decoded[bytes.key] = pixels
        return pixels
    }
}
