package com.tcsappdev.worldwinddraw.core.animation

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

class RedrawSignalTest {
    @Test
    fun requestRedrawReachesAnAlreadyActiveCollector() = runTest {
        val signal = RedrawSignal()
        // Start collecting first — this matches real usage: the host is already
        // observing before any redraw is ever requested.
        val collected = async { withTimeout(2000L) { signal.signal.first() } }
        delay(50) // non-blocking: yields so the async collector actually starts listening
        signal.requestRedraw()
        collected.await() // completes without timing out
    }
}
