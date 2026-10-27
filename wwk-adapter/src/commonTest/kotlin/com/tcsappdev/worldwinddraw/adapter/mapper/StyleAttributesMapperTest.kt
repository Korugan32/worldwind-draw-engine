package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StyleAttributesMapperTest {
    @Test
    fun toWwkColorNormalizesEachChannelTo0To1Range() {
        // WWK's Color has two constructor overloads: one taking 0-255 Ints (normalizing
        // internally), one taking 0f-1f Floats directly. ColorRgba's fields are Ints, so
        // Kotlin must resolve to the Int overload here — if that overload resolution ever
        // broke (e.g. after a ColorRgba field type change), this would silently pass raw
        // 0-255 values through as 0f-1f floats instead of normalizing them.
        val wwkColor = ColorRgba(red = 255, green = 128, blue = 0, alpha = 51).toWwkColor()

        assertEquals(1f, wwkColor.red)
        assertEquals(128 / 255f, wwkColor.green)
        assertEquals(0f, wwkColor.blue)
        assertEquals(51 / 255f, wwkColor.alpha)
    }

    @Test
    fun solidColorMapsToInteriorColorWithNoImageSource() {
        val attributes = ShapeStyle.SolidColor(ColorRgba(255, 0, 0)).toWwkShapeAttributes()

        assertEquals(1f, attributes.interiorColor.red)
        assertEquals(0f, attributes.interiorColor.green)
        assertEquals(0f, attributes.interiorColor.blue)
        assertNull(attributes.interiorImageSource)
    }

    // No test for ShapeStyle.ImagePattern here: WWK's JS actual for
    // ImageSource.fromUrlString() reads the browser `window` global (it resolves the URL via
    // the DOM), so calling it from commonTest crashes under jsNodeTest (no `window` in
    // Node) even though it works fine in a real browser or on JVM/Android. Exercising this
    // branch is left to the demo app / JVM-only manual checks; the mapping itself is a
    // trivial one-line pass-through so the coverage loss is small.

    @Test
    fun solidColorMapsToOutlineColorAndWidthWithNoInteriorChange() {
        // toWwkLineAttributes must write outline* properties, not interior* — a polyline has
        // no interior, so writing interiorColor here would silently drop the entity's color.
        val attributes = ShapeStyle.SolidColor(ColorRgba(0, 0, 255)).toWwkLineAttributes(width = 4f)

        assertEquals(1f, attributes.outlineColor.blue)
        assertEquals(4f, attributes.outlineWidth)
        assertNull(attributes.outlineImageSource)
    }

    // No tests for toWwkPlacemarkAttributes() here: WWK's PlacemarkAttributes' default
    // constructor unconditionally builds a TextAttributes/Font, and WWK's Android actual for
    // Font calls android.content.res.Resources.getSystem() — which crashes under Android's
    // plain host unit tests (testAndroidHostTest's unmocked android.jar stub; it needs
    // Robolectric or a real device, neither of which commonTest has). This affects EVERY
    // call to PlacemarkAttributes(), not just an image-related branch (contrast with the
    // ImagePattern note above, which was one branch). See MarkerMapperTest in jvmTest for
    // this coverage instead — jvmTest isn't affected since there's no Android stub involved.
}
