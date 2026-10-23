package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.GreatCircle
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** An [ImageFillRasterizer] result: the picture, and the geographic box ([minLat]..[maxLat], [minLon]..[maxLon]) its edges correspond to. */
class RasterizedFill(
    val image: PixelImage,
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
)

/**
 * Turns "this picture, fitted this way into this shape" into pixels: the picture placed in the
 * shape's bounding box and cut to the shape (anti-aliased), everything outside it transparent. The
 * result is meant to be laid on the globe over exactly that box, where latitude and longitude
 * are linear in the pixel grid. Pure Kotlin so it behaves the same on every target and is testable
 * without a map.
 */
object ImageFillRasterizer {
    /** Vertical samples per pixel row when measuring how much of a pixel the shape covers. */
    private const val SUBSAMPLES = 4

    /** Edges are followed along the great circle at about this step, so the cut matches how the shape's edges are drawn. */
    private const val EDGE_STEP_DEGREES = 0.25

    /**
     * [rings] are the shape's outlines, filled with the even-odd rule — a ring inside another is
     * a hole, so this handles multi-part shapes and shapes with holes. Returns `null` if there is
     * nothing to draw: no area, or a longitude span over 180 degrees (a shape across the
     * antimeridian would need its box split).
     *
     * The result's longer side is [maxDimension] pixels. Its pixel grid is proportioned like the
     * *ground* (a degree of longitude is shorter than a degree of latitude away from the equator),
     * so the picture isn't stretched sideways when it is laid back over the box.
     */
    fun render(
        source: PixelImage,
        rings: List<List<GeoPosition>>,
        fit: ImageFit,
        scale: Float,
        opacity: Float,
        maxDimension: Int,
    ): RasterizedFill? {
        val points = rings.flatten()
        if (points.size < 3) return null
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = maxLat - minLat
        val lonSpan = maxLon - minLon
        if (latSpan <= 0.0 || lonSpan <= 0.0 || lonSpan > 180.0) return null

        val centerLatRadians = (minLat + maxLat) / 2 * PI / 180.0
        val groundWidth = lonSpan * max(cos(centerLatRadians), 0.05)
        val longest = max(groundWidth, latSpan)
        val width = max(2, (maxDimension * groundWidth / longest).roundToInt())
        val height = max(2, (maxDimension * latSpan / longest).roundToInt())

        val coverage = coverage(rings, minLat, maxLat, minLon, lonSpan, latSpan, width, height)
        val placement = placement(source, fit, scale, width, height)
        val out = IntArray(width * height)
        val opacityScale = opacity.coerceIn(0f, 1f)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val cover = coverage[y * width + x]
                if (cover <= 0f) continue
                val sample = sample(source, placement, x + 0.5f, y + 0.5f)
                val alpha = (sample ushr 24) * cover * opacityScale
                if (alpha <= 0f) continue
                out[y * width + x] = (alpha.roundToInt().coerceIn(0, 255) shl 24) or (sample and 0x00FFFFFF)
            }
        }
        return RasterizedFill(PixelImage(width, height, out), minLat, minLon, maxLat, maxLon)
    }

    // -- where the picture sits

    private class Placement(val left: Float, val top: Float, val width: Float, val height: Float)

    private fun placement(source: PixelImage, fit: ImageFit, scale: Float, destWidth: Int, destHeight: Int): Placement {
        val (w, h) = when (fit) {
            ImageFit.Stretch -> destWidth.toFloat() to destHeight.toFloat()
            ImageFit.Contain, ImageFit.Cover -> {
                val widthRatio = destWidth.toFloat() / source.width
                val heightRatio = destHeight.toFloat() / source.height
                val factor = if (fit == ImageFit.Contain) min(widthRatio, heightRatio) else max(widthRatio, heightRatio)
                source.width * factor to source.height * factor
            }
        }
        val scaledWidth = w * scale
        val scaledHeight = h * scale
        return Placement((destWidth - scaledWidth) / 2f, (destHeight - scaledHeight) / 2f, scaledWidth, scaledHeight)
    }

    /** The picture's colour at destination point ([x], [y]), bilinearly filtered, or `0` (transparent) outside the picture. */
    private fun sample(source: PixelImage, p: Placement, x: Float, y: Float): Int {
        val u = (x - p.left) / p.width * source.width - 0.5f
        val v = (y - p.top) / p.height * source.height - 0.5f
        if (x < p.left || x >= p.left + p.width || y < p.top || y >= p.top + p.height) return 0
        val x0 = floor(u).toInt()
        val y0 = floor(v).toInt()
        val fx = u - x0
        val fy = v - y0
        var a = 0f; var r = 0f; var g = 0f; var b = 0f
        for (dy in 0..1) for (dx in 0..1) {
            val weight = (if (dx == 0) 1 - fx else fx) * (if (dy == 0) 1 - fy else fy)
            if (weight <= 0f) continue
            val px = source.argb[(y0 + dy).coerceIn(0, source.height - 1) * source.width + (x0 + dx).coerceIn(0, source.width - 1)]
            val alpha = (px ushr 24).toFloat()
            // Weighted by alpha, so a transparent neighbour doesn't bleed its (meaningless) colour in.
            a += weight * alpha
            r += weight * alpha * ((px shr 16) and 0xFF)
            g += weight * alpha * ((px shr 8) and 0xFF)
            b += weight * alpha * (px and 0xFF)
        }
        if (a <= 0f) return 0
        return (a.roundToInt().coerceIn(0, 255) shl 24) or
            ((r / a).roundToInt().coerceIn(0, 255) shl 16) or
            ((g / a).roundToInt().coerceIn(0, 255) shl 8) or
            (b / a).roundToInt().coerceIn(0, 255)
    }

    // -- the cut

    /** How much of each pixel the shape covers, 0..1, row by row. Even-odd across all [rings]. */
    private fun coverage(
        rings: List<List<GeoPosition>>,
        minLat: Double, maxLat: Double, minLon: Double,
        lonSpan: Double, latSpan: Double,
        width: Int, height: Int,
    ): FloatArray {
        // Every edge as a segment in pixel space, with great-circle detail along it.
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val edgeStarts = ArrayList<Int>()
        for (ring in rings) {
            if (ring.size < 3) continue
            val dense = ArrayList<GeoPosition>()
            for (i in ring.indices) {
                val from = ring[i]
                val to = ring[(i + 1) % ring.size]
                dense.add(from)
                dense.addAll(GreatCircle.intermediatePoints(from, to, EDGE_STEP_DEGREES))
            }
            edgeStarts.add(xs.size)
            for (p in dense) {
                xs.add(((p.longitude - minLon) / lonSpan * width).toFloat())
                ys.add(((maxLat - p.latitude) / latSpan * height).toFloat())
            }
        }
        edgeStarts.add(xs.size)

        val result = FloatArray(width * height)
        val row = FloatArray(width)
        val crossings = ArrayList<Float>()
        val weight = 1f / SUBSAMPLES
        for (y in 0 until height) {
            row.fill(0f)
            for (s in 0 until SUBSAMPLES) {
                val sy = y + (s + 0.5f) / SUBSAMPLES
                crossings.clear()
                for (ringIndex in 0 until edgeStarts.size - 1) {
                    val start = edgeStarts[ringIndex]
                    val end = edgeStarts[ringIndex + 1]
                    val count = end - start
                    for (k in 0 until count) {
                        val x0 = xs[start + k]; val y0 = ys[start + k]
                        val x1 = xs[start + (k + 1) % count]; val y1 = ys[start + (k + 1) % count]
                        // Half-open on the upper end so a vertex shared by two edges is counted once.
                        if ((y0 <= sy && y1 > sy) || (y1 <= sy && y0 > sy)) {
                            crossings.add(x0 + (sy - y0) / (y1 - y0) * (x1 - x0))
                        }
                    }
                }
                crossings.sort()
                var i = 0
                while (i + 1 < crossings.size) {
                    addSpan(row, crossings[i], crossings[i + 1], weight)
                    i += 2
                }
            }
            for (x in 0 until width) result[y * width + x] = min(1f, row[x])
        }
        return result
    }

    private fun addSpan(row: FloatArray, from: Float, to: Float, weight: Float) {
        val start = max(from, 0f)
        val end = min(to, row.size.toFloat())
        if (end <= start) return
        val firstPixel = floor(start).toInt()
        val lastPixel = min(row.size - 1, ceil(end).toInt() - 1)
        for (px in firstPixel..lastPixel) {
            row[px] += (min(end, px + 1f) - max(start, px.toFloat())) * weight
        }
    }
}
