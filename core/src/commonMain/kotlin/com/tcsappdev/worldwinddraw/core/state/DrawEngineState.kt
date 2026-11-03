package com.tcsappdev.worldwinddraw.core.state

/**
 * An immutable snapshot of the draw engine's current interaction state. Does not hold the
 * drawn shapes themselves — those live in a separate entity repository — only what mode
 * the engine is in and that mode's associated data.
 */
public data class DrawEngineState(
    val mode: DrawMode = DrawMode.Idle
)