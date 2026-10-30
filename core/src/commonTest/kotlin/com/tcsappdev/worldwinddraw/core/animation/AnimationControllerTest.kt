package com.tcsappdev.worldwinddraw.core.animation

import kotlin.test.Test
import kotlin.test.assertEquals

class AnimationControllerTest {
    private class RecordingAnimatable : Animatable {
        var totalTicked: Long = 0L
        override fun onTick(deltaMs: Long) {
            totalTicked += deltaMs
        }
    }

    @Test
    fun tickReachesEveryRegisteredAnimatable() {
        val controller = AnimationController()
        val a = RecordingAnimatable()
        val b = RecordingAnimatable()
        controller.register(a)
        controller.register(b)

        controller.tick(16L)

        assertEquals(16L, a.totalTicked)
        assertEquals(16L, b.totalTicked)
    }

    @Test
    fun unregisteredAnimatablesStopReceivingTicks() {
        val controller = AnimationController()
        val a = RecordingAnimatable()
        val b = RecordingAnimatable()
        controller.register(a)
        controller.register(b)

        controller.unregister(a)
        controller.tick(16L)

        assertEquals(0L, a.totalTicked)
        assertEquals(16L, b.totalTicked)
    }

    @Test
    fun anAnimatableMayUnregisterItselfWhileItIsTicked() {
        val controller = AnimationController()
        val after = RecordingAnimatable()
        val leaving = object : Animatable {
            override fun onTick(deltaMs: Long) = controller.unregister(this)
        }
        controller.register(leaving)
        controller.register(after)

        controller.tick(16L)
        controller.tick(16L)

        assertEquals(32L, after.totalTicked) // the one registered after it was still ticked, and then again
    }
}
