package com.tcsappdev.worldwinddraw.adapter.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import earth.worldwind.render.image.ImageSource

// One image per colour combination, built on first use. Bounded so a host that animates or
// scrubs a colour (a picker, a slider) can't grow it without limit; when full it is simply
// emptied and rebuilt as needed — WWK keeps its own texture cache keyed by the ImageSource.
// The source is an ImageFactory, not ImageSource.fromBitmap: WWK recycles a fromBitmap source's Bitmap when its
// texture leaves the texture cache, after which that source can't be drawn again (see ImageSources.android.kt).
private const val MAX_CACHED_IMAGES = 24
private val imageCache = HashMap<Any, ImageSource>()

private fun cached(key: Any, draw: () -> Bitmap): ImageSource = synchronized(imageCache) {
    imageCache[key] ?: run {
        if (imageCache.size >= MAX_CACHED_IMAGES) imageCache.clear()
        val factory = object : ImageSource.ImageFactory {
            override val isRunBlocking: Boolean get() = true
            override suspend fun createBitmap(): Bitmap = draw()
        }
        ImageSource.fromImageFactory(factory).also { imageCache[key] = it }
    }
}

internal actual fun vertexHandleImage(ring: ColorRgba, fill: ColorRgba): ImageSource? =
    cached(Pair("vertex", Pair(ring, fill))) { drawVertex(ring.toArgb(), fill.toArgb()) }

internal actual fun midpointHandleImage(ring: ColorRgba, fill: ColorRgba, plus: ColorRgba): ImageSource? =
    cached(Triple(ring, fill, plus)) { drawMidpoint(ring.toArgb(), fill.toArgb(), plus.toArgb()) }

private fun ColorRgba.toArgb() = Color.argb(alpha, red, green, blue)

private const val SHADOW = 0x55000000
private const val DISC_RADIUS = (HANDLE_IMAGE_DISC_DIAMETER / 2).toFloat()

private fun paint(block: Paint.() -> Unit) = Paint(Paint.ANTI_ALIAS_FLAG).apply(block)

/** A disc with a ring and a soft drop shadow. */
private fun drawVertex(ring: Int, fill: Int): Bitmap {
    val size = 48
    val c = size / 2f
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawCircle(c, c + 1f, DISC_RADIUS, paint { color = fill; setShadowLayer(4f, 0f, 1.5f, SHADOW) })
            drawCircle(c, c, 14.5f, paint { style = Paint.Style.STROKE; strokeWidth = 3f; color = ring })
        }
    }
}

/** A disc with a thin ring and a "+" — reads as "drag me to add a point here". */
private fun drawMidpoint(ring: Int, fill: Int, plusColor: Int): Bitmap {
    val size = 48
    val c = size / 2f
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).apply {
            drawCircle(c, c + 1f, DISC_RADIUS, paint { color = fill; setShadowLayer(4f, 0f, 1.5f, SHADOW) })
            drawCircle(c, c, 15.75f, paint { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = ring })
            val plus = paint { style = Paint.Style.STROKE; strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND; color = plusColor }
            val arm = 7f
            drawLine(c - arm, c, c + arm, c, plus)
            drawLine(c, c - arm, c, c + arm, plus)
        }
    }
}
