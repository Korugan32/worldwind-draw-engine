package com.tcsappdev.worldwinddraw.core.style

/**
 * The visual "mode" a [com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity] is
 * currently in, driven by user interaction. A [Theme] maps each of these states to the
 * [ShapeStyle] that should be shown while a shape is in that state.
 */
public enum class StyleState {
    DEFAULT,
    HOVER,
    SELECTED,
    EDITING,
    DISABLED
}
