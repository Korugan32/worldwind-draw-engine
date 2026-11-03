package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.InternalWorldWindDrawApi

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.GreatCircle
import com.tcsappdev.worldwinddraw.core.geometry.PolygonCenter
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** An [ImageFillRasterizer] result: the picture, and the geographic box ([minLat]..[maxLat], [minLon]..[maxLon]) its edges correspond to. */
@InternalWorldWindDrawApi
public class RasterizedFill(
    public val image: PixelImage,
    public val minLatitude: Double,
    public val minLongitude: Double,
    public val maxLatitude: Double,
    public val maxLongitude: Double,
)

/**
 * Turns "this picture, fitted this way into this shape" into pixels: the picture placed in the
 * shape's bounding box and cut to the shape (anti-aliased), everything outside it transparent. The
 * result is meant to be laid on the globe over exactly that box, where latitude and longitude
 * are linear in the pixel grid. Pure Kotlin so it behaves the same on every target and is testable
 * without a map.
 */
@InternalWorldWindDrawApi
public object ImageFillRasterizer {
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
    public fun render(
        source: PixelImage,
        rings: List<List<GeoPosition>>,
        fit: ImageFit,
        scale: Float,
        opacity: Float,
        maxDimension: Int,
        background: ColorRgba? = null,
        repeat: Boolean = false,
        anchorX: Float? = null,
        anchorY: Float? = null,
        extendEdges: Boolean = false,
        edgeSoftness: Float = 0f,
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
        // The shape's whole bounding box as a grid whose longer side is maxDimension: the frame placements are worked out in.
        val boxWidth = max(2, (maxDimension * groundWidth / longest).roundToInt())
        val boxHeight = max(2, (maxDimension * latSpan / longest).roundToInt())

        // What is drawn is the picture placed in one or more pieces of the shape: the whole shape for every fit but Inscribed, which gives each
        // separate part (see PolygonParts) its own picture in its own visual centre, so the islands of an archipelago each get one.
        val pieceRings = if (fit == ImageFit.Inscribed) PolygonParts.split(rings).ifEmpty { listOf(rings) } else listOf(rings)
        val pieces = pieceRings.map { part ->
            // The piece's bounding box in pixels of the box grid.
            val partPoints = part.flatten()
            val pieceLeft = ((partPoints.minOf { it.longitude } - minLon) / lonSpan * boxWidth).toFloat()
            val pieceRight = ((partPoints.maxOf { it.longitude } - minLon) / lonSpan * boxWidth).toFloat()
            val pieceTop = ((maxLat - partPoints.maxOf { it.latitude }) / latSpan * boxHeight).toFloat()
            val pieceBottom = ((maxLat - partPoints.minOf { it.latitude }) / latSpan * boxHeight).toFloat()

            // For ImageFit.Inscribed: the visual centre of the piece (keeping away from its holes) and the biggest circle around it, in pixels of the box grid.
            val circle = if (fit == ImageFit.Inscribed) {
                val center = PolygonCenter.visualCenterOfRings(part)
                Circle(
                    x = ((center.position.longitude - minLon) / lonSpan * boxWidth).toFloat(),
                    y = ((maxLat - center.position.latitude) / latSpan * boxHeight).toFloat(),
                    // The grid is as tall as the shape is in degrees of latitude, and a degree of longitude is as many pixels as a
                    // degree of latitude after the shrinking above, so one scale turns metres into pixels.
                    radius = (center.radiusMeters / PolygonCenter.METERS_PER_DEGREE * boxHeight / latSpan).toFloat(),
                )
            } else null
            val fitted = placement(source, fit, scale, boxWidth, boxHeight, circle)
            // An anchor moves the picture's centre to that fraction of the piece's box (x from its west edge, y from its north edge).
            val placed = if (anchorX == null && anchorY == null) fitted else Placement(
                left = if (anchorX != null) pieceLeft + anchorX * (pieceRight - pieceLeft) - fitted.width / 2 else fitted.left,
                top = if (anchorY != null) pieceTop + anchorY * (pieceBottom - pieceTop) - fitted.height / 2 else fitted.top,
                width = fitted.width,
                height = fitted.height,
            )
            Piece(part, placed)
        }

        // Where nothing but the picture is drawn, only the pictures' own rectangles need pixels, so all maxDimension of them go to them
        // (and the pictures come out sharper) instead of a part of them to the empty rest of the box. A background colour, extended
        // edges or tiles fill the whole box, so those keep the whole of it.
        var windowLeft = 0f
        var windowTop = 0f
        var windowRight = boxWidth.toFloat()
        var windowBottom = boxHeight.toFloat()
        if (!repeat && !extendEdges && background == null) {
            val inBox = pieces.map { it.placement }.filter {
                it.left + it.width > 0f && it.top + it.height > 0f && it.left < boxWidth && it.top < boxHeight
            }
            if (inBox.isNotEmpty()) {
                windowLeft = max(0f, inBox.minOf { it.left })
                windowTop = max(0f, inBox.minOf { it.top })
                windowRight = min(boxWidth.toFloat(), inBox.maxOf { it.left + it.width })
                windowBottom = min(boxHeight.toFloat(), inBox.maxOf { it.top + it.height })
            }
        }
        val windowWidth = windowRight - windowLeft
        val windowHeight = windowBottom - windowTop
        val toPixels = maxDimension / max(windowWidth, windowHeight)
        val width = max(2, (windowWidth * toPixels).roundToInt())
        val height = max(2, (windowHeight * toPixels).roundToInt())
        // The window as a geographic box.
        val windowMaxLat = maxLat - windowTop / boxHeight * latSpan
        val windowMinLat = maxLat - windowBottom / boxHeight * latSpan
        val windowMinLon = minLon + windowLeft / boxWidth * lonSpan
        val windowLonSpan = windowWidth / boxWidth * lonSpan
        val windowLatSpan = windowMaxLat - windowMinLat

        val out = IntArray(width * height)
        val opacityScale = opacity.coerceIn(0f, 1f)
        for (piece in pieces) {
            // The picture's place in the window's pixels.
            val placement = Placement(
                left = (piece.placement.left - windowLeft) * toPixels,
                top = (piece.placement.top - windowTop) * toPixels,
                width = piece.placement.width * toPixels,
                height = piece.placement.height * toPixels,
            )
            // Only the pixels of the window the piece can reach are looked at: the rows and columns of its bounding box.
            val points = piece.rings.flatten()
            val x0 = floor((points.minOf { it.longitude } - windowMinLon) / windowLonSpan * width).toInt().coerceIn(0, width)
            val x1 = ceil((points.maxOf { it.longitude } - windowMinLon) / windowLonSpan * width).toInt().coerceIn(0, width)
            val y0 = floor((windowMaxLat - points.maxOf { it.latitude }) / windowLatSpan * height).toInt().coerceIn(0, height)
            val y1 = ceil((windowMaxLat - points.minOf { it.latitude }) / windowLatSpan * height).toInt().coerceIn(0, height)
            if (x1 <= x0 || y1 <= y0) continue
            val coverage = coverage(
                piece.rings,
                minLat = windowMaxLat - y1.toDouble() / height * windowLatSpan,
                maxLat = windowMaxLat - y0.toDouble() / height * windowLatSpan,
                minLon = windowMinLon + x0.toDouble() / width * windowLonSpan,
                lonSpan = (x1 - x0).toDouble() / width * windowLonSpan,
                latSpan = (y1 - y0).toDouble() / height * windowLatSpan,
                width = x1 - x0, height = y1 - y0,
            )
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val cover = coverage[(y - y0) * (x1 - x0) + (x - x0)]
                    if (cover <= 0f) continue
                    val sample = sample(source, placement, x + 0.5f, y + 0.5f, repeat, extendEdges, edgeSoftness)
                    if (background == null) {
                        val alpha = (sample ushr 24) * cover * opacityScale
                        if (alpha <= 0f) continue
                        out[y * width + x] = (alpha.roundToInt().coerceIn(0, 255) shl 24) or (sample and 0x00FFFFFF)
                    } else {
                        out[y * width + x] = over(sample, (sample ushr 24) / 255f * cover, background, cover, opacityScale)
                    }
                }
            }
        }
        return RasterizedFill(PixelImage(width, height, out), windowMinLat, windowMinLon, windowMaxLat, windowMinLon + windowLonSpan)
    }

    /**
     * The picture's colour [sample] at opacity [imageAlpha] laid over the [background] colour (itself covering [cover] of the pixel):
     * standard "over" blending, so wherever the picture is opaque it wins, where it is missing the background shows, and in between they mix.
     * The blend is made first and [groupOpacity] fades it as one: the same picture colour over the same background colour looks the
     * same inside the picture and around it, instead of the picture's part being more opaque.
     */
    private fun over(sample: Int, imageAlpha: Float, background: ColorRgba, cover: Float, groupOpacity: Float): Int {
        val backgroundAlpha = background.alpha / 255f * cover
        val alpha = imageAlpha + backgroundAlpha * (1 - imageAlpha)
        if (alpha <= 0f) return 0
        val backgroundWeight = backgroundAlpha * (1 - imageAlpha)
        fun channel(image: Int, back: Int) = ((image * imageAlpha + back * backgroundWeight) / alpha).roundToInt().coerceIn(0, 255)
        val red = channel((sample shr 16) and 0xFF, background.red)
        val green = channel((sample shr 8) and 0xFF, background.green)
        val blue = channel(sample and 0xFF, background.blue)
        return ((alpha * groupOpacity * 255).roundToInt().coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
    }

    // -- where the picture sits

    private class Placement(val left: Float, val top: Float, val width: Float, val height: Float)

    /** A part of the shape (its rings) and where its picture goes, in pixels of the box grid. */
    private class Piece(val rings: List<List<GeoPosition>>, val placement: Placement)

    /** A circle in pixels of the output grid: its centre and radius. */
    private class Circle(val x: Float, val y: Float, val radius: Float)

    private fun placement(source: PixelImage, fit: ImageFit, scale: Float, destWidth: Int, destHeight: Int, circle: Circle?): Placement {
        if (fit == ImageFit.Inscribed && circle != null) {
            // The picture's corners touch the circle: its half diagonal is the radius, so its width is 2r * a / sqrt(a^2 + 1).
            val aspect = source.width.toFloat() / source.height
            val fitted = 2 * circle.radius * aspect / sqrt(aspect * aspect + 1)
            val width = fitted * scale
            val height = fitted / aspect * scale
            return Placement(circle.x - width / 2, circle.y - height / 2, width, height)
        }
        val (w, h) = when (fit) {
            ImageFit.Stretch -> destWidth.toFloat() to destHeight.toFloat()
            ImageFit.Contain, ImageFit.Cover, ImageFit.Inscribed -> {
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

    /**
     * The picture's colour at destination point ([x], [y]), bilinearly filtered, or `0` (transparent) outside the picture. With
     * [edgeSoftness] above 0 its alpha fades towards the picture's edges (see [softEdge]).
     */
    private fun sample(source: PixelImage, p: Placement, x: Float, y: Float, repeat: Boolean, extendEdges: Boolean, edgeSoftness: Float): Int {
        var dx0 = x - p.left
        var dy0 = y - p.top
        if (repeat) {
            // Tiled: the position within whichever copy of the picture this point falls in.
            dx0 -= floor(dx0 / p.width) * p.width
            dy0 -= floor(dy0 / p.height) * p.height
        } else if (!extendEdges && (dx0 < 0 || dx0 >= p.width || dy0 < 0 || dy0 >= p.height)) return 0
        // With extendEdges a point past the picture is still sampled: the neighbours below are clamped to the picture, so what comes
        // out is its outermost row or column of pixels carried on outwards.
        val u = dx0 / p.width * source.width - 0.5f
        val v = dy0 / p.height * source.height - 0.5f
        val x0 = floor(u).toInt()
        val y0 = floor(v).toInt()
        val fx = u - x0
        val fy = v - y0
        var a = 0f; var r = 0f; var g = 0f; var b = 0f
        for (dy in 0..1) for (dx in 0..1) {
            val weight = (if (dx == 0) 1 - fx else fx) * (if (dy == 0) 1 - fy else fy)
            if (weight <= 0f) continue
            // In a tiled picture the neighbour across an edge is the other side of the picture, not a repeat of the edge pixel.
            val sx = if (repeat) (x0 + dx).mod(source.width) else (x0 + dx).coerceIn(0, source.width - 1)
            val sy = if (repeat) (y0 + dy).mod(source.height) else (y0 + dy).coerceIn(0, source.height - 1)
            val px = source.argb[sy * source.width + sx]
            val alpha = (px ushr 24).toFloat()
            // Weighted by alpha, so a transparent neighbour doesn't bleed its (meaningless) colour in.
            a += weight * alpha
            r += weight * alpha * ((px shr 16) and 0xFF)
            g += weight * alpha * ((px shr 8) and 0xFF)
            b += weight * alpha * (px and 0xFF)
        }
        if (a <= 0f) return 0
        // Extended edges have nothing under them to fade into, so they stay as they are.
        val alpha = if (edgeSoftness > 0f && !extendEdges) a * softEdge(dx0, dy0, p, edgeSoftness) else a
        return (alpha.roundToInt().coerceIn(0, 255) shl 24) or
            ((r / a).roundToInt().coerceIn(0, 255) shl 16) or
            ((g / a).roundToInt().coerceIn(0, 255) shl 8) or
            (b / a).roundToInt().coerceIn(0, 255)
    }

    /**
     * How much of the picture is left at ([dx], [dy]) inside it, 1 in the middle and 0 at the edge: a smoothstep over a band as wide
     * as [softness] times half the picture's shorter side, measured inwards from the nearest edge (so `1` fades all the way to the middle).
     */
    private fun softEdge(dx: Float, dy: Float, p: Placement, softness: Float): Float {
        val fromEdge = min(min(dx, p.width - dx), min(dy, p.height - dy))
        val band = softness * min(p.width, p.height) / 2
        val t = (fromEdge / band).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    // -- the cut

    /** How much of each pixel the shape covers, 0..1, row by row. Even-odd across all [rings]. */
    private fun coverage(
        rings: List<List<GeoPosition>>,
        minLat: Double, maxLat: Double, minLon: Double,
        lonSpan: Double, latSpan: Double,
        width: Int, height: Int,
    ): FloatArray {
        // Every edge as a segment in pixel space, with great-circle detail along it, each ring's points in turn.
        val xs = FloatBuffer()
        val ys = FloatBuffer()
        val ringStarts = ArrayList<Int>()
        for (ring in rings) {
            if (ring.size < 3) continue
            ringStarts.add(xs.size)
            for (i in ring.indices) {
                val from = ring[i]
                val to = ring[(i + 1) % ring.size]
                xs.add(((from.longitude - minLon) / lonSpan * width).toFloat())
                ys.add(((maxLat - from.latitude) / latSpan * height).toFloat())
                for (p in GreatCircle.intermediatePoints(from, to, EDGE_STEP_DEGREES)) {
                    xs.add(((p.longitude - minLon) / lonSpan * width).toFloat())
                    ys.add(((maxLat - p.latitude) / latSpan * height).toFloat())
                }
            }
        }
        ringStarts.add(xs.size)

        // The edges: point k to the next point of its ring (the last one back to the first).
        val edgeCount = xs.size
        val ex0 = FloatArray(edgeCount)
        val ey0 = FloatArray(edgeCount)
        val ex1 = FloatArray(edgeCount)
        val ey1 = FloatArray(edgeCount)
        var edge = 0
        for (r in 0 until ringStarts.size - 1) {
            val start = ringStarts[r]
            val count = ringStarts[r + 1] - start
            for (k in 0 until count) {
                val next = start + (k + 1) % count
                ex0[edge] = xs.data[start + k]; ey0[edge] = ys.data[start + k]
                ex1[edge] = xs.data[next]; ey1[edge] = ys.data[next]
                edge++
            }
        }

        // Which scanlines each edge crosses, so a scanline only looks at its own edges instead of all of them: the cost is that of the
        // edges and the pixels, not their product (a coastline of 100,000 edges over 4,000 scanlines would be 400 million looks).
        val scanlines = height * SUBSAMPLES
        val firstLine = IntArray(edgeCount)
        val lastLine = IntArray(edgeCount)
        val perLine = IntArray(scanlines + 1)
        for (e in 0 until edgeCount) {
            val low = min(ey0[e], ey1[e])
            val high = max(ey0[e], ey1[e])
            // The range the edge can cover, a line wider each way, then cut to exactly the scanlines the test below accepts.
            var first = max(0, ceil(low * SUBSAMPLES - 0.5f).toInt() - 1)
            var last = min(scanlines - 1, ceil(high * SUBSAMPLES - 0.5f).toInt() + 1)
            while (first <= last && !crosses(ey0[e], ey1[e], scanY(first))) first++
            while (last >= first && !crosses(ey0[e], ey1[e], scanY(last))) last--
            firstLine[e] = first
            lastLine[e] = last
            for (n in first..last) perLine[n + 1]++
        }
        for (n in 0 until scanlines) perLine[n + 1] += perLine[n]
        val lineEdges = IntArray(perLine[scanlines])
        val fill = perLine.copyOf(scanlines)
        for (e in 0 until edgeCount) for (n in firstLine[e]..lastLine[e]) lineEdges[fill[n]++] = e

        val result = FloatArray(width * height)
        val row = FloatArray(width)
        var crossings = FloatArray(64)
        val weight = 1f / SUBSAMPLES
        for (y in 0 until height) {
            row.fill(0f)
            for (s in 0 until SUBSAMPLES) {
                val n = y * SUBSAMPLES + s
                val sy = scanY(n)
                val from = perLine[n]
                val count = perLine[n + 1] - from
                if (count > crossings.size) crossings = FloatArray(count * 2)
                for (i in 0 until count) {
                    val e = lineEdges[from + i]
                    crossings[i] = ex0[e] + (sy - ey0[e]) / (ey1[e] - ey0[e]) * (ex1[e] - ex0[e])
                }
                crossings.sort(0, count)
                var i = 0
                while (i + 1 < count) {
                    addSpan(row, crossings[i], crossings[i + 1], weight)
                    i += 2
                }
            }
            for (x in 0 until width) result[y * width + x] = min(1f, row[x])
        }
        return result
    }

    /** The y of sample line [n] (four to a pixel row), in pixels from the top. */
    private fun scanY(n: Int): Float = (n / SUBSAMPLES) + ((n % SUBSAMPLES) + 0.5f) / SUBSAMPLES

    /** Whether an edge from height [y0] to [y1] crosses the line at [sy]: half-open on the upper end so a vertex shared by two edges is counted once. */
    private fun crosses(y0: Float, y1: Float, sy: Float) = (y0 <= sy && y1 > sy) || (y1 <= sy && y0 > sy)

    /** A growable list of floats without boxing. */
    private class FloatBuffer {
        var data = FloatArray(1024)
        var size = 0

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }
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
