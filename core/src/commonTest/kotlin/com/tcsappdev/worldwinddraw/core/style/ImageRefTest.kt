package com.tcsappdev.worldwinddraw.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ImageRefTest {
    @Test
    fun bytesAreIdentifiedByTheirKeyAlone() {
        val a = ImageRef.Bytes("photo-1", byteArrayOf(1, 2, 3))
        val sameKeyOtherBytes = ImageRef.Bytes("photo-1", byteArrayOf(9, 9))
        val otherKey = ImageRef.Bytes("photo-2", byteArrayOf(1, 2, 3))

        assertEquals(a, sameKeyOtherBytes)
        assertEquals(a.hashCode(), sameKeyOtherBytes.hashCode())
        assertNotEquals(a, otherKey)
    }

    @Test
    fun bytesNeedAKeyAndContent() {
        assertFailsWith<IllegalArgumentException> { ImageRef.Bytes("", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { ImageRef.Bytes("  ", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { ImageRef.Bytes("k", byteArrayOf()) }
    }

    @Test
    fun anImagePatternFromAUrlIsAPatternOfAUrlRef() {
        val pattern = ShapeStyle.ImagePattern("https://example.com/a.png", opacity = 0.5f)

        assertEquals(ImageRef.Url("https://example.com/a.png"), pattern.image)
        assertEquals(0.5f, pattern.opacity)
    }

    @Test
    fun opacityDefaultsToOpaqueAndMustBeBetweenZeroAndOne() {
        assertEquals(1f, ShapeStyle.ImagePattern("https://example.com/a.png").opacity)
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImagePattern("https://example.com/a.png", opacity = -0.1f) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImagePattern("https://example.com/a.png", opacity = 1.1f) }
    }

    @Test
    fun anImageFillDefaultsToAFullSizeCentredContainedOpaquePicture() {
        val fill = ShapeStyle.ImageFill(ImageRef.Url("https://example.com/a.png"))

        assertEquals(ImageFit.Contain, fill.fit)
        assertEquals(1f, fill.scale)
        assertEquals(1f, fill.opacity)
    }

    @Test
    fun anImageFillNeedsAPositiveScaleAndAnOpacityBetweenZeroAndOne() {
        val image = ImageRef.Url("https://example.com/a.png")
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImageFill(image, scale = 0f) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImageFill(image, scale = -1f) }
        assertFailsWith<IllegalArgumentException> { ShapeStyle.ImageFill(image, opacity = 1.5f) }
    }
}
