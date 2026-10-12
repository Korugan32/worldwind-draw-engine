package com.tcsappdev.worldwinddraw.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tcsappdev.worldwinddraw.adapter.input.PickInputBridge
import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.animation.AnimationController
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import earth.worldwind.WorldWindow
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.BlueMarbleLayer

/**
 * Embeds WWK's `WorldWindow` (a `GLSurfaceView`, not a native Compose widget) via
 * [AndroidView], and drives its GL render loop from the host [Lifecycle] — WWK expects
 * [WorldWindow.onResume]/[WorldWindow.onPause] to be called explicitly, the same way a
 * plain `GLSurfaceView` would need in a non-Compose Activity.
 *
 * [initialCameraPosition]'s `altitude` doubles as the camera's viewing distance in meters —
 * WWK has no separate "zoom level" concept, the camera is just a point above the globe.
 *
 * Real taps reach [stateMachine] via [PickInputBridge], wired through WWK's own
 * `selectDragDetector` (see [PickInputBridge]'s KDoc for why that's the right hook, not a
 * `WorldWindowController` decorator).
 */
@Composable
fun WorldWindowMap(
    entityRepository: EntityRepository,
    stateMachine: DrawStateMachine,
    initialCameraPosition: GeoPosition,
    modifier: Modifier = Modifier,
    handleStyle: HandleStyle = HandleStyle(),
    highlightStyle: HighlightStyle = HighlightStyle(),
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var worldWindow by remember { mutableStateOf<WorldWindow?>(null) }
    val animationController = remember { AnimationController() }
    var drawLayer by remember { mutableStateOf<DrawLayer?>(null) }

    // DrawLayer.handleStyle/highlightStyle can change at any time; this just forwards the host's current values.
    LaunchedEffect(drawLayer, handleStyle) { drawLayer?.handleStyle = handleStyle }
    LaunchedEffect(drawLayer, highlightStyle) { drawLayer?.highlightStyle = highlightStyle }

    // The library never owns a clock (see Animatable): the host feeds it one tick per frame.
    // withFrameNanos only fires while Compose is actually rendering frames, so this loop
    // pauses by itself when the app is in the background.
    LaunchedEffect(animationController) {
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            animationController.tick((frameNanos - lastFrameNanos) / 1_000_000)
            lastFrameNanos = frameNanos
        }
    }

    AndroidView(
        factory = { context ->
            WorldWindow(context).apply {
                engine.layers.apply {
                    addLayer(BackgroundLayer())
                    addLayer(BlueMarbleLayer())
                    val layer = DrawLayer(entityRepository, stateMachine, scope, onContentChanged = ::requestRedraw, handleStyle = handleStyle, highlightStyle = highlightStyle)
                    animationController.register(layer)
                    addLayer(layer)
                    drawLayer = layer
                }
                val camera = initialCameraPosition.toWwkPosition()
                engine.camera.position.set(camera.latitude, camera.longitude, camera.altitude)
                selectDragDetector.callback = PickInputBridge(stateMachine)
                worldWindow = this
            }
        },
        modifier = modifier.fillMaxSize(),
    )

    DisposableEffect(lifecycleOwner, worldWindow) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> worldWindow?.onResume()
                Lifecycle.Event.ON_PAUSE -> worldWindow?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
