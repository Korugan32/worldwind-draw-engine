package com.tcsappdev.worldwinddraw.core.animation

/**
 * A registry of active [Animatable]s. A host app calls [tick] once per frame (its own
 * clock, not ours — see [Animatable]) and every registered animation advances together.
 */
public class AnimationController {
    private val animations = mutableListOf<Animatable>()

    public fun register(animatable: Animatable) {
        animations.add(animatable)
    }

    public fun unregister(animatable: Animatable) {
        animations.remove(animatable)
    }

    public fun tick(deltaMs: Long) {
        // a copy: an animation may unregister itself, or register another one, when it ends
        animations.toList().forEach { it.onTick(deltaMs) }
    }
}