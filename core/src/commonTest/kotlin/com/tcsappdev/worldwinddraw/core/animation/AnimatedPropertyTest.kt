package com.tcsappdev.worldwinddraw.core.animation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class AnimatedPropertyTest {
    private val linear = Easing { it }
    private fun lerp(start: Float, target: Float, fraction: Float) = start + (target - start) * fraction

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.01f) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, was $actual")
    }

    @Test
    fun onceReachesTargetAndStaysFinished() {
        val anim = AnimatedProperty(0f, 100f, AnimationSpec(1000L, linear, RepeatMode.Once), ::lerp)

        anim.onTick(500L)
        assertNear(50f, anim.value)

        anim.onTick(500L)
        assertNear(100f, anim.value)
        assertTrue(anim.isFinished)

        // Once finished, further ticks must not move the value past the target.
        anim.onTick(1000L)
        assertNear(100f, anim.value)
    }

    @Test
    fun loopWrapsBackToTheStart() {
        val anim = AnimatedProperty(0f, 100f, AnimationSpec(1000L, linear, RepeatMode.Loop), ::lerp)

        // 1500ms into a 1000ms loop is 1.5 cycles — halfway through the second cycle.
        anim.onTick(1500L)
        assertNear(50f, anim.value)
    }

    @Test
    fun reversePingPongsBetweenStartAndTarget() {
        val anim = AnimatedProperty(0f, 100f, AnimationSpec(1000L, linear, RepeatMode.Reverse), ::lerp)

        anim.onTick(500L) // halfway through the forward leg
        assertNear(50f, anim.value)

        anim.onTick(500L) // 1000ms: end of the forward leg, at the target
        assertNear(100f, anim.value)

        anim.onTick(500L) // 1500ms: halfway through the backward leg
        assertNear(50f, anim.value)
    }

    @Test
    fun easingReshapesTheRawFraction() {
        // An easing that always reports "fully there" should immediately jump to the
        // target, regardless of how little time has actually elapsed.
        val jumpToEnd = Easing { 1f }
        val anim = AnimatedProperty(0f, 100f, AnimationSpec(1000L, jumpToEnd, RepeatMode.Once), ::lerp)

        anim.onTick(1L)
        assertNear(100f, anim.value)
    }
}
