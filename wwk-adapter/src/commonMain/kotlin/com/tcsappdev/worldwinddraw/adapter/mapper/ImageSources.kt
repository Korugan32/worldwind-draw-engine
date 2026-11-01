package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import earth.worldwind.render.image.ImageSource

/**
 * An image ready to hand to WWK, plus its size in pixels when that is known ([width]/[height] are
 * `0` for a URL, which is only downloaded later, when it is first drawn).
 */
internal class LoadedImage(val source: ImageSource, val width: Int, val height: Int)

/**
 * The WWK image for this reference, or `null` when it can't be produced here: the bytes aren't a
 * decodable image, or this platform has no decoder yet (see [decodeImageBytes]).
 *
 * A URL is cheap (WWK downloads and caches it, keyed by the URL string). Bytes are decoded once and
 * kept under their [ImageRef.Bytes.key] — WWK compares an in-memory image by *reference*, so decoding
 * again on every redraw would both cost time and defeat its texture cache.
 */
internal fun ImageRef.toLoadedImage(): LoadedImage? = when (this) {
    is ImageRef.Url -> LoadedImage(ImageSource.fromUrlString(url), 0, 0)
    is ImageRef.Bytes -> DecodedImageCache.getOrDecode(this)
}

/** Decoded images are shrunk so their longest side is at most this many pixels. */
internal const val MAX_IMAGE_DIMENSION = 1024

private object DecodedImageCache {
    // Bounded: a host that keeps swapping pictures can't grow this without limit. When full, the
    // oldest entry goes (and is simply decoded again if it is ever needed).
    private const val MAX_ENTRIES = 16
    private val cache = LinkedHashMap<String, LoadedImage>()

    fun getOrDecode(ref: ImageRef.Bytes): LoadedImage? {
        cache[ref.key]?.let { return it }
        val decoded = decodeImageBytes(ref.data, MAX_IMAGE_DIMENSION) ?: return null
        if (cache.size >= MAX_ENTRIES) cache.remove(cache.keys.first())
        cache[ref.key] = decoded
        return decoded
    }
}

/**
 * Decodes an encoded image (PNG, JPEG, ...), shrunk so its longest side is at most [maxDimension]
 * pixels, or `null` if the bytes aren't a decodable image. Platform-specific: WWK has no common way
 * to be given pixels. Not implemented on the web and iOS yet, where it returns `null` (so images
 * given as bytes fall back to a placeholder there until those targets are verified).
 */
internal expect fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage?

/**
 * Like [decodeImageBytes] but returns the decoded pixels themselves, for code that has to work on
 * them (cutting a picture to a shape) rather than hand them straight to WWK. `null` if the bytes
 * aren't a decodable image, or on a platform with no decoder yet.
 */
internal expect fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage?

/**
 * The WWK image for freshly computed [image] pixels, or `null` on a platform that can't make one yet.
 *
 * WWK keeps a texture under its image source for as long as its cache has room (a large share of the device's memory), and
 * the source has to be able to make the picture again, so by default it holds on to [image]. A caller that has its own,
 * smaller cache of the pixels and makes new ones all the time (a fill that changes every frame) passes `retainPixels = false`:
 * the source then holds them only weakly, and the pixels of pictures that are no longer drawn can be freed.
 */
internal expect fun imageSourceFromPixels(image: PixelImage, retainPixels: Boolean = true): ImageSource?

/**
 * Gets a picture ready to be drawn from its bytes. Needed in the browser only: there a picture can only be decoded asynchronously, so a shape
 * whose look is a picture given as [ImageRef.Bytes] shows its placeholder until this has finished for those bytes; call it (and wait for it)
 * before adding such a shape. On every other platform it does nothing, because the decoding is done when the shape is drawn.
 */
suspend fun preloadImage(bytes: ByteArray) = preloadImageBytes(bytes)

internal expect suspend fun preloadImageBytes(bytes: ByteArray)
