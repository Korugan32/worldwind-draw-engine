package com.tcsappdev.worldwinddraw.core.animation

/** How an [AnimatedProperty] should play: how long, along what curve, and whether/how it repeats. */
public data class AnimationSpec(
    val durationMs: Long,
    val easing: Easing,
    val repeatMode: RepeatMode = RepeatMode.Once
)
