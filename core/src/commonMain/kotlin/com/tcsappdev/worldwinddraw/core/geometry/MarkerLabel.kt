package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ColorRgba

/** Where a [MarkerLabel] is written, as seen from the marker's icon. */
enum class LabelPosition {
    /** Above the icon, centred on it. */
    Above,

    /** Below the icon, centred on it. */
    Below,

    /** To the left of the icon. */
    Left,

    /** To the right of the icon. */
    Right,

    /** On the icon itself, centred on the marker's position: for a marker that is only text. */
    Center,
}

/**
 * The text written next to a [DrawnMarker].
 *
 * @property text what to write; not empty.
 * @property color the text colour; its alpha is the text's opacity.
 * @property size the text size, in the units of the display's text (about the height of a letter in pixels at 1x), from 6 to 96.
 * @property outlineColor the colour of the thin outline drawn around the letters so they can be read on any map, or `null` for
 * none; its alpha is the outline's opacity.
 * @property position where the text sits against the icon.
 */
data class MarkerLabel(
    val text: String,
    val color: ColorRgba = ColorRgba(255, 255, 255),
    val size: Int = 14,
    val outlineColor: ColorRgba? = ColorRgba(0, 0, 0),
    val position: LabelPosition = LabelPosition.Above,
) {
    init {
        require(text.isNotEmpty()) { "a label needs some text" }
        require(size in 6..96) { "size must be between 6 and 96, was $size" }
    }
}
