package com.tcsappdev.worldwinddraw.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class HighlightStyleTest {
    private val style = HighlightStyle(selectedColor = ColorRgba(1, 2, 3), editingColor = ColorRgba(4, 5, 6))

    @Test
    fun onlySelectedAndEditingAreHighlighted() {
        assertEquals(ColorRgba(1, 2, 3), style.colorFor(StyleState.SELECTED))
        assertEquals(ColorRgba(4, 5, 6), style.colorFor(StyleState.EDITING))
        assertNull(style.colorFor(StyleState.DEFAULT))
        assertNull(style.colorFor(StyleState.HOVER))
        assertNull(style.colorFor(StyleState.DISABLED))
    }

    @Test
    fun defaultsUseDistinctColorsForSelectedAndEditing() {
        assertNotEquals(HighlightStyle().selectedColor, HighlightStyle().editingColor)
    }
}
