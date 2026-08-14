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
}
