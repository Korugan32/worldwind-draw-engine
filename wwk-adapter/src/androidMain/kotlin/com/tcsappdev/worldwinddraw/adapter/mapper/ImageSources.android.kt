package com.tcsappdev.worldwinddraw.adapter.mapper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import earth.worldwind.render.image.ImageSource

internal actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // inSampleSize must be a power of two: this picks the smallest one that fits, so the result is
    // at most maxDimension and at least half of it (and a big photo is never fully decoded first).
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    return LoadedImage(ImageSource.fromBitmap(bitmap), bitmap.width, bitmap.height)
}

internal actual fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return PixelImage(bitmap.width, bitmap.height, pixels)
}

internal actual fun imageSourceFromPixels(image: PixelImage): ImageSource? =
    ImageSource.fromBitmap(Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888))
