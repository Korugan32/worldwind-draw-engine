@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import earth.worldwind.render.image.ArgbImage
import earth.worldwind.render.image.ImageSource
import earth.worldwind.render.image.argbImageSource
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIImage
import kotlin.math.max
import kotlin.math.roundToInt

// iOS: pictures are decoded with UIKit/Core Graphics, and the pixels reach WWK through its own argbImageSource (WorldWind 2.1.0 and later).

internal actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage? {
    val pixels = decodePixels(bytes, maxDimension) ?: return null
    return LoadedImage(imageSourceFromPixels(pixels, retainPixels = true) ?: return null, pixels.width, pixels.height)
}

internal actual fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
    val image = UIImage.imageWithData(data)?.CGImage ?: return null
    val sourceWidth = CGImageGetWidth(image).toInt()
    val sourceHeight = CGImageGetHeight(image).toInt()
    if (sourceWidth <= 0 || sourceHeight <= 0) return null
    // Shrunk so the longest side is at most maxDimension (and never enlarged).
    val longest = max(sourceWidth, sourceHeight)
    val scale = if (longest > maxDimension) maxDimension.toDouble() / longest else 1.0
    val width = max(1, (sourceWidth * scale).roundToInt())
    val height = max(1, (sourceHeight * scale).roundToInt())

    val rgba = ByteArray(width * height * 4)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val drawn = rgba.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0), width = width.toULong(), height = height.toULong(), bitsPerComponent = 8u,
            bytesPerRow = (width * 4).toULong(), space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
        )
        if (context == null) false else {
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
            CGContextRelease(context)
            true
        }
    }
    CGColorSpaceRelease(colorSpace)
    if (!drawn) return null

    // Core Graphics gives premultiplied RGBA bytes; PixelImage is plain (not premultiplied) 0xAARRGGBB.
    val argb = IntArray(width * height)
    for (i in argb.indices) {
        val a = rgba[i * 4 + 3].toInt() and 0xFF
        var r = rgba[i * 4].toInt() and 0xFF
        var g = rgba[i * 4 + 1].toInt() and 0xFF
        var b = rgba[i * 4 + 2].toInt() and 0xFF
        if (a in 1..254) {
            r = minOf(255, r * 255 / a)
            g = minOf(255, g * 255 / a)
            b = minOf(255, b * 255 / a)
        }
        argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return PixelImage(width, height, argb)
}

// WWK's iOS image factory treats the pixels as premultiplied, so they are premultiplied on the way in. The key is the image itself (compared by
// reference), like the other platforms, so one picture is one texture. The pixels are held for as long as WWK holds the source.
internal actual fun imageSourceFromPixels(image: PixelImage, retainPixels: Boolean): ImageSource? =
    argbImageSource(image) {
        val out = IntArray(image.argb.size)
        for (i in out.indices) {
            val v = image.argb[i]
            val a = v ushr 24
            out[i] = when (a) {
                255 -> v
                0 -> 0
                else -> (a shl 24) or ((((v shr 16) and 0xFF) * a / 255) shl 16) or ((((v shr 8) and 0xFF) * a / 255) shl 8) or ((v and 0xFF) * a / 255)
            }
        }
        ArgbImage(image.width, image.height, out)
    }

internal actual suspend fun preloadImageBytes(bytes: ByteArray) {}
