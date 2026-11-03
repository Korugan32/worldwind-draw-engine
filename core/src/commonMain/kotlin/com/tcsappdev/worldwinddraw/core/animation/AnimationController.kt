package com.tcsappdev.worldwinddraw.core.animation

/**
 * A registry of active [Animatable]s. A host app calls [tick] once per frame (its own
 * clock, not ours — see [Animatable]) and every registered animation advances together.
 */
public class AnimationController {
    private val animations = mutableListOf<Animatable>()

    /** Starts advancing [animatable] with every [tick]. */
    public fun register(animatable: Animatable) {
        animations.add(animatable)
    }

    /** Stops advancing [animatable]; an animation that is not registered is ignored. It is safe to do this from an animation's own [Animatable.onTick]. */
    public fun unregister(animatable: Animatable) {
        animations.remove(animatable)
    }

    /** Advances every registered animation by [deltaMs] milliseconds: call it once per frame, from the host's own clock. */
    public fun tick(deltaMs: Long) {
        // a copy: an animation may unregister itself, or register another one, when it ends
        animations.toList().forEach { it.onTick(deltaMs) }
    }
}