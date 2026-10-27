package com.tcsappdev.worldwinddraw.adapter.mapper

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.LabelPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.geom.Offset
import earth.worldwind.geom.OffsetMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// jvmTest: a Placemark's attributes can't be built in Android's plain host tests (see StyleAttributesMapperTest).
class MarkerLabelMapperTest {
    private fun marker(label: MarkerLabel?, size: Float = 20f) = DrawnMarker(
        id = EntityId("m"),
        style = ShapeStyle.SolidColor(ColorRgba(0, 255, 0)),
        position = GeoPosition(41.0, 29.0),
        size = size,
        label = label,
    )

    @Test
    fun aMarkerWithoutALabelHasNoText() {
        assertNull(marker(null).toWwkPlacemark().label)
    }

    @Test
    fun theTextAndItsColourAndOpacityComeFromTheLabel() {
        val placemark = marker(MarkerLabel("Istanbul", color = ColorRgba(255, 128, 0, 102))).toWwkPlacemark()
        val text = placemark.attributes.labelAttributes

        assertEquals("Istanbul", placemark.label)
        assertEquals(1f, text.textColor.red)
        assertEquals(128 / 255f, text.textColor.green, 0.01f)
        assertEquals(0f, text.textColor.blue)
        assertEquals(0.4f, text.textColor.alpha, 0.01f)
    }

    @Test
    fun theOutlineIsOnWithItsOwnColourAndOpacityOrOffWhenThereIsNone() {
        val outlined = marker(MarkerLabel("a", outlineColor = ColorRgba(0, 0, 255, 51))).toWwkPlacemark().attributes.labelAttributes
        assertTrue(outlined.isOutlineEnabled)
        assertEquals(1f, outlined.outlineColor.blue)
        assertEquals(0.2f, outlined.outlineColor.alpha, 0.01f)

        val plain = marker(MarkerLabel("a", outlineColor = null)).toWwkPlacemark().attributes.labelAttributes
        assertFalse(plain.isOutlineEnabled)
    }

    @Test
    fun theSizeChangesTheFont() {
        val small = marker(MarkerLabel("a", size = 12)).toWwkPlacemark().attributes.labelAttributes.font
        val big = marker(MarkerLabel("a", size = 40)).toWwkPlacemark().attributes.labelAttributes.font
        val same = marker(MarkerLabel("b", size = 12)).toWwkPlacemark().attributes.labelAttributes.font
        assertNotEquals(small, big)
        assertEquals(small, same)
    }

    @Test
    fun theLabelIsNotHiddenByTheRelief() {
        assertFalse(marker(MarkerLabel("a")).toWwkPlacemark().attributes.labelAttributes.isDepthTest)
    }

    @Test
    fun theLabelClearsTheIconByHalfItsSizeAndAGapWhicheverSideItIsOn() {
        // 20 pixels of icon: 10 to its edge and 2 of gap.
        assertEquals(Offset(OffsetMode.FRACTION, 0.5, OffsetMode.PIXELS, -12.0), labelOffset(LabelPosition.Above, 20f))
        assertEquals(Offset(OffsetMode.FRACTION, 0.5, OffsetMode.INSET_PIXELS, -12.0), labelOffset(LabelPosition.Below, 20f))
        assertEquals(Offset(OffsetMode.PIXELS, -12.0, OffsetMode.FRACTION, 0.5), labelOffset(LabelPosition.Right, 20f))
        assertEquals(Offset(OffsetMode.INSET_PIXELS, -12.0, OffsetMode.FRACTION, 0.5), labelOffset(LabelPosition.Left, 20f))
        assertEquals(Offset.center(), labelOffset(LabelPosition.Center, 20f))
        // A bigger icon pushes it further out.
        assertEquals(Offset(OffsetMode.FRACTION, 0.5, OffsetMode.PIXELS, -42.0), labelOffset(LabelPosition.Above, 80f))
    }

    @Test
    fun thePlacementReachesThePlacemarksTextAttributes() {
        val placemark = marker(MarkerLabel("a", position = LabelPosition.Right), size = 40f).toWwkPlacemark()
        assertEquals(labelOffset(LabelPosition.Right, 40f), placemark.attributes.labelAttributes.textOffset)
    }
}
