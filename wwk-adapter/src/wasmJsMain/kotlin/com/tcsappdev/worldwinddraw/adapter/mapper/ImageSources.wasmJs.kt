package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import earth.worldwind.render.image.ImageSource

// Not implemented on this platform yet, so images given as bytes fall back to a placeholder fill.
// To be done when web/iOS are verified (Faz 11).
internal actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage? = null

internal actual fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage? = null

internal actual fun imageSourceFromPixels(image: PixelImage, retainPixels: Boolean): ImageSource? = null
