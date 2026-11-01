package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import earth.worldwind.render.image.ImageSource
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal actual fun decodeImageBytes(bytes: ByteArray, maxDimension: Int): LoadedImage? {
    val decoded = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    val longest = maxOf(decoded.width, decoded.height)
    val image = if (longest <= maxDimension) decoded else {
        val scale = maxDimension.toDouble() / longest
        val width = maxOf(1, (decoded.width * scale).toInt())
        val height = maxOf(1, (decoded.height * scale).toInt())
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { scaled ->
            scaled.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(decoded, 0, 0, width, height, null)
                dispose()
            }
        }
    }
    return LoadedImage(ImageSource.fromImage(image), image.width, image.height)
}

internal actual fun decodePixels(bytes: ByteArray, maxDimension: Int): PixelImage? {
    val decoded = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    val longest = maxOf(decoded.width, decoded.height)
    val image = if (longest <= maxDimension) decoded else {
        val scale = maxDimension.toDouble() / longest
        val width = maxOf(1, (decoded.width * scale).toInt())
        val height = maxOf(1, (decoded.height * scale).toInt())
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { scaled ->
            scaled.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(decoded, 0, 0, width, height, null)
                dispose()
            }
        }
    }
    return PixelImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
}

internal actual fun imageSourceFromPixels(image: PixelImage, retainPixels: Boolean): ImageSource? {
    val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    buffered.setRGB(0, 0, image.width, image.height, image.argb, 0, image.width)
    return ImageSource.fromImage(buffered)
}

internal actual suspend fun preloadImageBytes(bytes: ByteArray) {}
