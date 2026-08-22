package com.tcsappdev.worldwinddraw.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
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
 */
@Composable
fun WorldWindowMap(
    entityRepository: EntityRepository,
    initialCameraPosition: GeoPosition,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var worldWindow by remember { mutableStateOf<WorldWindow?>(null) }

    AndroidView(
        factory = { context ->
            WorldWindow(context).apply {
                engine.layers.apply {
                    addLayer(BackgroundLayer())
                    addLayer(BlueMarbleLayer())
                    addLayer(DrawLayer(entityRepository, scope))
                }
                val camera = initialCameraPosition.toWwkPosition()
                engine.camera.position.set(camera.latitude, camera.longitude, camera.altitude)
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
