package com.tcsappdev.worldwinddraw.core.style

/**
 * The colour of the outline drawn around the shape the user has selected or is editing. It is
 * drawn *around* the shape rather than recolouring it, so a shape's own fill and style are
 * never touched. A colour's alpha is honoured: the highlight pulses between that opacity and
 * a faded one.
 *
 * The defaults (a coral rose for [selectedColor], a teal for [editingColor]) were picked to
 * stand out on both dark ocean and green/tan land imagery and not to clash with each other.
 * The shape being drawn (before it is finished) uses [editingColor] for its outline.
 */
public data class HighlightStyle(
    val selectedColor: ColorRgba = ColorRgba(red = 255, green = 92, blue = 138),
    val editingColor: ColorRgba = ColorRgba(red = 45, green = 212, blue = 191),
) {
    /** The highlight colour for a shape in [state], or `null` if that state isn't highlighted (all but SELECTED and EDITING). */
    public fun colorFor(state: StyleState): ColorRgba? = when (state) {
        StyleState.SELECTED -> selectedColor
        StyleState.EDITING -> editingColor
        else -> null
    }
}
