package com.tcsappdev.worldwinddraw.adapter.layer

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * How coarsely shapes are drawn: [NONE] draws every vertex; level `n` (0, 1, 2, ...) drops the vertices that move
 * the outline by less than [toleranceMeters] `(n)`, which doubles with every level. Levels are discrete, and
 * [forTolerance] only moves to another one once the camera is clearly past the edge between two, so a camera
 * hovering at the edge doesn't make every shape build again on each frame.
 */
internal object LodLevel {
    const val NONE = -1

    /** Level 0's tolerance, metres. Below this nothing is simplified: the data itself is about that detailed. */
    private const val BASE_TOLERANCE_METERS = 10.0

    /** How far past an edge, in levels (one level = a factor 2), the camera has to go before the level changes. */
    private const val HYSTERESIS = 0.25

    fun toleranceMeters(level: Int): Double = BASE_TOLERANCE_METERS * 2.0.pow(level)

    /** The level for a wanted [toleranceMeters], staying at [current] while that is still reasonable. */
    fun forTolerance(toleranceMeters: Double, current: Int): Int {
        val x = ln(toleranceMeters / BASE_TOLERANCE_METERS) / ln(2.0) // levels above the base; negative: nothing to drop
        val stays = if (current == NONE) x < HYSTERESIS else x >= current - HYSTERESIS && x < current + 1 + HYSTERESIS
        if (stays) return current
        return if (x < 0.0) NONE else floor(x).toInt()
    }
}
