package com.tcsappdev.worldwinddraw.core.animation

/**
 * Anything that advances over time, fed by an external clock. The library never opens
 * its own thread/timer — a host app (e.g. Compose's `withFrameNanos`) is expected to
 * call [onTick] on every frame, passing how many milliseconds elapsed since the last call.
 */
public interface Animatable {
    /** Advances by [deltaMs] milliseconds, the time since the previous call. */
    public fun onTick(deltaMs: Long)
}

