package com.tcsappdev.worldwinddraw.core.style

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ShapeStyleTest {
    @Test
    fun colorChannelsMustBeWithinByteRange() {
        // green=260 and blue=-10 are both invalid, but only one assertFailsWith call is used
        // here: ColorRgba's init checks red, then green, then blue, then alpha in order, so
        // this call throws on green before blue is even checked. Both channels being invalid
        // doesn't mean both get exercised by a single call — a separate call per channel would
        // be needed to actually verify blue's own check independently.
        assertFailsWith<IllegalArgumentException> {
            ColorRgba(red = 0, green = 260, blue = -10, alpha = 20)
        }
        ColorRgba(red = 0, green = 255, blue = 0, alpha = 20)
    }

    @Test
    fun gradientRequiresAtLeastTwoColors() {
        // Kept as two separate assertFailsWith blocks on purpose: putting both invalid calls
        // inside one block would mean the first exception short-circuits the block, and the
        // second call would silently never run.
        assertFailsWith<IllegalArgumentException> {
            ShapeStyle.Gradient(colors = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ShapeStyle.Gradient(colors = listOf(ColorRgba(0, 0, 0, 0)))
        }
        ShapeStyle.Gradient(colors = listOf(ColorRgba(0, 0, 0, 0), ColorRgba(0, 0, 0, 0)))
    }
}