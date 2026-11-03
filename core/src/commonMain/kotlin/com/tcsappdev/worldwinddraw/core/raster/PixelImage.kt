package com.tcsappdev.worldwinddraw.core.raster

import com.tcsappdev.worldwinddraw.core.InternalWorldWindDrawApi

/**
 * A decoded picture: [width] x [height] pixels, row by row from the top-left, each one a
 * non-premultiplied `0xAARRGGBB` int.
 */
@InternalWorldWindDrawApi
public class PixelImage(public val width: Int, public val height: Int, public val argb: IntArray) {
    init {
        require(width > 0 && height > 0) { "size must be positive, was ${width}x$height" }
        require(argb.size == width * height) { "expected ${width * height} pixels, got ${argb.size}" }
    }
}
