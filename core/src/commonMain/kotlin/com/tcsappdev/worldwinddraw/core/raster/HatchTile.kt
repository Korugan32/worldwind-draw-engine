package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The tile of a `ShapeStyle.Hatch`: a square of `spacing` pixels that, laid side by side, makes the lines, the cross or the dots
 * run on without a seam (every distance is measured to the nearest line of the *repeating* pattern, so what leaves one edge
 * comes back in at the opposite one). The lines are anti-aliased over one pixel. Pure Kotlin, like the rest of this package.
 */
object HatchTile {
    fun render(pattern: HatchPattern, color: ColorRgba, background: ColorRgba?, spacing: Int, thickness: Float): PixelImage {
        val half = thickness / 2
        val out = IntArray(spacing * spacing)
        for (y in 0 until spacing) {
            for (x in 0 until spacing) {
                // The pixel's centre, measured from the tile's centre: every line of the pattern passes through the middle of a tile.
                val dx = wrap(x + 0.5f - spacing / 2f, spacing)
                val dy = wrap(y + 0.5f - spacing / 2f, spacing)
                val distance = when (pattern) {
                    HatchPattern.Horizontal -> abs(dy)
                    HatchPattern.Vertical -> abs(dx)
                    HatchPattern.DiagonalUp -> abs(wrap(dx + dy, spacing)) / SQRT2
                    HatchPattern.DiagonalDown -> abs(wrap(dx - dy, spacing)) / SQRT2
                    HatchPattern.Cross -> minOf(abs(wrap(dx + dy, spacing)), abs(wrap(dx - dy, spacing))) / SQRT2
                    HatchPattern.Dots -> hypot(dx, dy)
                }
                // Fully covered inside the line, fading out over the pixel that holds its edge.
                val covered = (half - distance + 0.5f).coerceIn(0f, 1f)
                out[y * spacing + x] = blend(color, covered, background)
            }
        }
        return PixelImage(spacing, spacing, out)
    }

    /** [value] brought into -[period]/2..[period]/2: how far it is from the nearest multiple of [period]. */
    private fun wrap(value: Float, period: Int): Float {
        val p = period.toFloat()
        return value - p * kotlin.math.round(value / p)
    }

    /** [color] at [coverage] laid over [background] (or over nothing), as a non-premultiplied `0xAARRGGBB`. */
    private fun blend(color: ColorRgba, coverage: Float, background: ColorRgba?): Int {
        val lineAlpha = color.alpha / 255f * coverage
        val backAlpha = (background?.alpha ?: 0) / 255f
        val alpha = lineAlpha + backAlpha * (1 - lineAlpha)
        if (alpha <= 0f) return 0
        val backWeight = backAlpha * (1 - lineAlpha)
        fun channel(line: Int, back: Int) = ((line * lineAlpha + back * backWeight) / alpha).roundToInt().coerceIn(0, 255)
        val red = channel(color.red, background?.red ?: 0)
        val green = channel(color.green, background?.green ?: 0)
        val blue = channel(color.blue, background?.blue ?: 0)
        return ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private val SQRT2 = sqrt(2f)
}
