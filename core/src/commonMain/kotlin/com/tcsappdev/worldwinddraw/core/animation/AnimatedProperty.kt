package com.tcsappdev.worldwinddraw.core.animation

/**
 * A value that animates from [startValue] to [targetValue] over [AnimationSpec.durationMs],
 * following [AnimationSpec.easing] and [AnimationSpec.repeatMode]. [interpolate] is supplied
 * by the caller because "what's halfway between two values" depends entirely on what [T] is
 * (a plain number, a color, a position, ...) — this class only owns the timing/easing/repeat
 * math, not the interpolation itself.
 */
class AnimatedProperty<T>(
    private val startValue: T,
    private val targetValue: T,
    private val spec: AnimationSpec,
    private val interpolate: (start: T, target: T, fraction: Float) -> T,
) : Animatable {
    private var elapsedMs: Long = 0L

    var value: T = startValue
        private set

    var isFinished: Boolean = false
        private set

    override fun onTick(deltaMs: Long) {
        if (isFinished) return

        elapsedMs += deltaMs
        val fraction = fractionFor(elapsedMs, spec.durationMs, spec.repeatMode)
        value = interpolate(startValue, targetValue, spec.easing.transform(fraction))

        if (spec.repeatMode == RepeatMode.Once && elapsedMs >= spec.durationMs) {
            isFinished = true
        }
    }

    private fun fractionFor(elapsedMs: Long, durationMs: Long, repeatMode: RepeatMode): Float {
        return when (repeatMode) {
            RepeatMode.Once -> (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            RepeatMode.Loop -> (elapsedMs % durationMs).toFloat() / durationMs.toFloat()
            RepeatMode.Reverse -> {
                val cycle = elapsedMs / durationMs
                val localFraction = (elapsedMs % durationMs).toFloat() / durationMs.toFloat()
                if (cycle % 2 == 0L) localFraction else 1f - localFraction
            }
        }
    }
}
