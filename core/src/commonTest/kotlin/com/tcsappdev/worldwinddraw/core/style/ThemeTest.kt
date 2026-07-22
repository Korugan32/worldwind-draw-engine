package com.tcsappdev.worldwinddraw.core.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThemeTest {

    @Test
    fun requiresADefaultFillAsFallback() {
        assertFailsWith<IllegalArgumentException> {
            Theme(
                name = "TestTheme",
                filledByState = mapOf(
                    StyleState.SELECTED to ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0))
                )
            )
        }
        Theme(
            name = "TestTheme1",
            filledByState = mapOf(
                StyleState.DEFAULT to ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0))
            )
        )
    }

    @Test
    fun fallsBackToDefaultForUnmappedStates() {
        // SELECTED is not in filledByState. fillFor must NOT throw here — it should silently
        // fall back to DEFAULT rather than crash for a state nobody bothered to customize.
        val theme = Theme(
            name = "TestTheme",
            filledByState = mapOf(
                StyleState.DEFAULT to ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0))
            )
        )
        assertEquals(
            ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
            theme.fillFor(StyleState.SELECTED)
        )
    }

    @Test
    fun usesExplicitOverrideWhenPresent() {
        val theme = Theme(
            name = "TestTheme",
            filledByState = mapOf(
                StyleState.DEFAULT to ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
                StyleState.SELECTED to ShapeStyle.SolidColor(ColorRgba(1, 1, 1, 1))
            )
        )
        assertEquals(
            ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 0)),
            theme.fillFor(StyleState.DEFAULT)
        )
        assertEquals(
            ShapeStyle.SolidColor(ColorRgba(1, 1, 1, 1)),
            theme.fillFor(StyleState.SELECTED)
        )
    }
}