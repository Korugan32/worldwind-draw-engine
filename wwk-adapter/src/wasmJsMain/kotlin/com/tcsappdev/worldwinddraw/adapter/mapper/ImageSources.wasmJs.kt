package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import earth.worldwind.render.image.ArgbImage
import earth.worldwind.render.image.ImageSource
import earth.worldwind.render.image.argbImageSource
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Browser (Wasm): a picture can only be decoded asynchronously there (an <img> and a canvas), so decodePixels, which is called while a shape is
// drawn, answers from what preloadImage has decoded before. Pixels reach WWK through its own argbImageSource (WorldWind 2.1.0 and later).

private val decoded = HashMap<Long, PixelImage>()

private fun keyOf(bytes: ByteArray): Long = (bytes.contentHashCode().toLong() shl 32) or (bytes.size.toLong() and 0xFFFFFFFFL)

@OptIn(ExperimentalEncodingApi::class)
internal actual suspend fun preloadImageBytes(bytes: ByteArray) {
    val key = keyOf(bytes)
    if (key in decoded || bytes.isEmpty()) return
    // "width,height,base64 of the RGBA bytes", or an empty string when the browser cannot decode it.
    val answer = try {
        decodeImage(Base64.encode(bytes), MAX_IMAGE_DIMENSION).await<kotlin.js.JsString>().toString()
    } catch (e: Throwable) {
        return
    }
    val parts = answer.split(',', limit = 3)
    if (parts.size < 3) return
    val width = parts[0].toIntOrNull() ?: return
    val height = parts[1].toIntOrNull() ?: return
    val rgba = Base64.decode(parts[2])
    if (rgba.size != width * height * 4) return
    val argb = IntArray(width * height)
    for (i in argb.indices) {
        argb[i] = ((rgba[i * 4 + 3].toInt() and 0xFF) shl 24) or ((rgba[i * 4].toInt() and 0xFF) shl 16) or
            ((rgba[i * 4 + 1].toInt() and 0xFF) shl 8) or (rgba[i * 4 + 2].toInt() and 0xFF)
    }
    decoded[key] = PixelImage(width, height, argb)
}

internal actual fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage? = decoded[keyOf(bytes)]

internal actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage? {
    val pixels = decodePixels(bytes, maxDimension) ?: return null
    return LoadedImage(imageSourceFromPixels(pixels, retainPixels = true) ?: return null, pixels.width, pixels.height)
}

// The canvas takes plain (not premultiplied) pixels, which is what PixelImage holds. The key is the image itself, compared by reference.
internal actual fun imageSourceFromPixels(image: PixelImage, retainPixels: Boolean): ImageSource? =
    argbImageSource(image) { ArgbImage(image.width, image.height, image.argb) }

@Suppress("UNUSED_PARAMETER")
private fun decodeImage(base64: String, maxDimension: Int): kotlin.js.Promise<kotlin.js.JsString> = js(
    """(async () => {
        const image = new Image();
        image.src = 'data:image/png;base64,' + base64;
        await image.decode();
        const longest = Math.max(image.naturalWidth, image.naturalHeight);
        const scale = longest > maxDimension ? maxDimension / longest : 1;
        const width = Math.max(1, Math.round(image.naturalWidth * scale));
        const height = Math.max(1, Math.round(image.naturalHeight * scale));
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext('2d', { willReadFrequently: true });
        context.drawImage(image, 0, 0, width, height);
        const data = context.getImageData(0, 0, width, height).data;
        let binary = '';
        for (let i = 0; i < data.length; i += 32768) binary += String.fromCharCode.apply(null, data.subarray(i, i + 32768));
        return width + ',' + height + ',' + btoa(binary);
    })()"""
)
