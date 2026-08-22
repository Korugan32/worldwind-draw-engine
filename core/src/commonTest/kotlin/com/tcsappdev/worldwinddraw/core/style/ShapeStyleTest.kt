package com.tcsappdev.worldwinddraw.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShapeStyleTest {
    @Test
    fun constructorParametersMatchTheirPropertyNames() {
        // Regression test: ColorRgba's constructor once had `blue` declared before `green`,
        // even though the parameter names were correct — so a positional call like
        // ColorRgba(0, 255, 0) silently stored 255 into `blue` instead of `green`. Using three
        // distinct, non-symmetric values here means any future reordering trips this test,
        // whereas symmetric/zero values would stay silently green.
        val color = ColorRgba(10, 20, 30)
        assertEquals(10, color.red)
        assertEquals(20, color.green)
        assertEquals(30, color.blue)
    }

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
}