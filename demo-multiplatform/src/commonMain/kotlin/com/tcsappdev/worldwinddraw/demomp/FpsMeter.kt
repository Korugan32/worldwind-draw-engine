package com.tcsappdev.worldwinddraw.demomp

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** Frames per second as Compose sees them (one per screen refresh it manages to draw): a stuck main thread shows up here on every platform. */
@Composable
fun FpsMeter(counter: FrameCounter? = null) {
    var fps by remember { mutableStateOf(0) }
    var gl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        var frames = 0
        var since = withFrameNanos { it }
        var glBefore = counter?.frames ?: 0
        var pickBefore = counter?.pickFrames ?: 0
        while (true) {
            val now = withFrameNanos { it }
            frames++
            if (now - since >= 1_000_000_000L) {
                fps = frames
                counter?.let { gl = " gl ${it.frames - glBefore} pick ${it.pickFrames - pickBefore} draw ${it.lastDrawMs}ms max ${it.worstDrawMs}"; glBefore = it.frames; pickBefore = it.pickFrames }
                frames = 0
                since = now
            }
        }
    }
    BasicText("$fps fps$gl", style = TextStyle(color = Color.White, fontSize = 15.sp))
}
