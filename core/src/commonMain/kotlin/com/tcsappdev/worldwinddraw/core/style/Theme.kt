package com.tcsappdev.worldwinddraw.core.style

/**
 * A named preset mapping each [StyleState] to the [ShapeStyle] shown while a shape is in
 * that state. Must define at least a [StyleState.DEFAULT] fill, which [fillFor] falls
 * back to for any state that isn't explicitly listed in [filledByState].
 */
internal data class Theme(
    val name: String,
    val filledByState: Map<StyleState, ShapeStyle>,
) {
    init {
        require(StyleState.DEFAULT in filledByState) { "a theme must define at least a DEFAULT fill" }
    }

    fun fillFor(state: StyleState): ShapeStyle {
        return filledByState[state] ?: filledByState.getValue(StyleState.DEFAULT)
    }
}
