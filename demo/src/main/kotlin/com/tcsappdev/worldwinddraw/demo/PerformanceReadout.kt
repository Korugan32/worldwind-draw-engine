package com.tcsappdev.worldwinddraw.demo

import android.util.Log
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import earth.worldwind.WorldWindow
import earth.worldwind.frame.BasicFrameMetrics
import kotlinx.coroutines.delay
import kotlin.math.max

/** WWK's frame metrics that also remember the slowest frame of a round, which the average hides (a stall is what the eye sees). */
private class PeakFrameMetrics : BasicFrameMetrics() {
    @Volatile var renderPeak = 0L
    @Volatile var drawPeak = 0L

    override fun markEnd(metrics: TimeMetrics, timeMillis: Long) {
        super.markEnd(metrics, timeMillis)
        if (metrics === renderMetrics) renderPeak = max(renderPeak, metrics.time) else drawPeak = max(drawPeak, metrics.time)
    }

    fun startRound() {
        reset()
        renderPeak = 0
        drawPeak = 0
    }
}

/**
 * A small live readout of how the map is doing, for trying the demo on a real phone: once a second it shows the average and the slowest of
 * the last second's frames (WWK's render step, which prepares what is drawn, and its draw step on the GPU), how many frames there were, the
 * longest time the app's own thread went without a frame (what a stall while a shape or a picture is being built looks like), the
 * textures WWK holds and the app's memory. WWK only draws when something changes, so while the map is still it says "idle". The same line
 * goes to the log (`adb logcat -s Perf`).
 */
@Composable
fun PerformanceReadout(worldWindow: WorldWindow?, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("measuring...") }
    val stall = remember { LongArray(1) } // the longest gap between two frames of the app's own thread this round, in nanoseconds

    DisposableEffect(Unit) {
        var last = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (last != 0L) stall[0] = max(stall[0], frameTimeNanos - last)
                last = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
        onDispose { Choreographer.getInstance().removeFrameCallback(callback) }
    }

    LaunchedEffect(worldWindow) {
        val window = worldWindow ?: return@LaunchedEffect
        val metrics = PeakFrameMetrics()
        window.engine.frameMetrics = metrics
        try {
            while (true) {
                metrics.startRound()
                stall[0] = 0
                delay(1_000)
                val frames = metrics.renderCount
                val heap = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1_000_000 }
                val textures = metrics.renderResourceCacheUsedCapacity / 1_000_000
                val line = if (frames == 0L) "idle" else
                    "%d frames · render %.1f / %d ms · draw %.1f / %d ms".format(frames, metrics.renderTimeAverage, metrics.renderPeak, metrics.drawTimeAverage, metrics.drawPeak)
                text = line + "\n" + "app thread stall %d ms · textures %d MB · heap %d MB".format(stall[0] / 1_000_000, textures, heap)
                Log.i("Perf", text.replace("\n", " | "))
            }
        } finally {
            window.engine.frameMetrics = null
        }
    }

    Text(
        text = text,
        color = Color(0xFFB9F6CA),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE61C1C22))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
