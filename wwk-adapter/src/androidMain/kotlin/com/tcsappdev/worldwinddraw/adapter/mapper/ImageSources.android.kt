package com.tcsappdev.worldwinddraw.adapter.mapper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import earth.worldwind.render.image.ImageSource
import java.lang.ref.WeakReference

// An image source made with ImageSource.fromBitmap can't be kept and reused: when WWK's texture cache drops the
// picture's texture (it is stale, or memory is short) the texture's release() recycles that very Bitmap, and the
// next draw of the same source fails with "bitmap is recycled" (the shape vanishes, and every frame logs an error).
// So these sources are ImageFactory ones instead: WWK asks the factory for a fresh bitmap each time it needs the
// texture. A factory is still a single, cached object, so WWK's by-reference texture cache keeps working.
private abstract class InMemoryImageFactory : ImageSource.ImageFactory {
    // The bitmap is made from memory in a few milliseconds, so it is made on the spot rather than on another thread
    // (which would leave the shape without its picture for a frame).
    override val isRunBlocking: Boolean get() = true
}

private class EncodedImageFactory(private val bytes: ByteArray, private val sampleSize: Int) : InMemoryImageFactory() {
    override suspend fun createBitmap(): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}

private class PixelsImageFactory(image: PixelImage, retain: Boolean) : InMemoryImageFactory() {
    private val retained: PixelImage? = if (retain) image else null
    private val weak = WeakReference(image)

    // Null when the pixels were let go (nothing draws this picture any more) and WWK still asks for it.
    override suspend fun createBitmap(): Bitmap? {
        val image = retained ?: weak.get() ?: return null
        return Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
    }
}

/** The smallest power of two (what inSampleSize needs) that brings the longest side down to [maxDimension], or 0 when [bytes] isn't an image. */
private fun sampleSizeFor(bytes: ByteArray, maxDimension: Int): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    return sample
}

private fun decode(bytes: ByteArray, sampleSize: Int): Bitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })

internal actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage? {
    val sample = sampleSizeFor(bytes, maxDimension).takeIf { it > 0 } ?: return null
    // Decoded once here, only to check the bytes really are an image and to learn the size; the bitmap itself is
    // made again by the factory whenever WWK needs the texture.
    val probe = decode(bytes, sample) ?: return null
    val loaded = LoadedImage(ImageSource.fromImageFactory(EncodedImageFactory(bytes, sample)), probe.width, probe.height)
    probe.recycle()
    return loaded
}

internal actual fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage? {
    val sample = sampleSizeFor(bytes, maxDimension).takeIf { it > 0 } ?: return null
    val bitmap = decode(bytes, sample) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return PixelImage(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
}

internal actual fun imageSourceFromPixels(image: PixelImage, retainPixels: Boolean): ImageSource? =
    ImageSource.fromImageFactory(PixelsImageFactory(image, retainPixels))
