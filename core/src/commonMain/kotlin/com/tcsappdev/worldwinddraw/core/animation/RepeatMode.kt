package com.tcsappdev.worldwinddraw.core.animation

/** What an [AnimatedProperty] does once it reaches the end of its duration. */
enum class RepeatMode {
    /** Play once, then hold at the target value. */
    Once,

    /** Jump back to the start and play again, indefinitely. */
    Loop,

    /** Play backward to the start, then forward again — back and forth, indefinitely. */
    Reverse
}