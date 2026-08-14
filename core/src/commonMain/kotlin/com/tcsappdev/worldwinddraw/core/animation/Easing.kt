package com.tcsappdev.worldwinddraw.core.animation

/**
 * Reshapes an animation's linear progress into how it should actually feel. [transform]
 * takes a linear fraction (0f at the start, 1f at the end) and returns the eased fraction
 * actually used to compute the animated value — e.g. a constant-speed (linear) easing
 * returns its input unchanged, while an "ease in" curve returns smaller values early on
 * so the animation starts slow and speeds up.
 */
fun interface Easing {
    fun transform(fraction: Float): Float
}