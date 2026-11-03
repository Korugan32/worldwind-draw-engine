package com.tcsappdev.worldwinddraw.demomp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcsappdev.worldwinddraw.adapter.engine.WorldWindDrawEngine
import com.tcsappdev.worldwinddraw.adapter.engine.attachTo
import com.tcsappdev.worldwinddraw.adapter.mapper.preloadImage
import com.tcsappdev.worldwinddraw.booleanops.JtsOverlayEngine
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.morph.DrawOutlineEffect
import com.tcsappdev.worldwinddraw.morph.RouteEffect
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import earth.worldwind.compose.WorldWindow
import earth.worldwind.compose.rememberWorldWindowState
import earth.worldwind.globe.elevation.coverage.BasicElevationCoverage
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.atmosphere.AtmosphereLayer
import earth.worldwind.layer.starfield.StarFieldLayer
import earth.worldwind.layer.BlueMarbleLandsatLayer
import earth.worldwind.layer.BlueMarbleLayer
import earth.worldwind.layer.mercator.WebMercatorLayerFactory

/** The same map and the same draw engine on every platform, with a few buttons. */
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    // The whole draw engine: the shapes, the history, the state machine, the layer and the touches, built and connected.
    val drawEngine = remember { WorldWindDrawEngine(scope, JtsOverlayEngine(), buildOnFirstFrame = true).also { checkShapes().forEach(it.repository::add) } }
    LaunchedEffect(Unit) {
        preloadImage(FLAG_BYTES)
        pictureShapes().forEach(drawEngine.repository::add)
    }
    val state by drawEngine.state.collectAsState()
    val canUndo by drawEngine.canUndo.collectAsState()
    val canRedo by drawEngine.canRedo.collectAsState()
    val mode = state.mode
    val draft = mode as? DrawMode.Draw
    val select = mode as? DrawMode.Select
    val edit = mode as? DrawMode.Edit
    // For this check's own buttons that change the map: the window is only known once the map exists.
    val redraw = remember { arrayOf<() -> Unit>({}) }

    // The layers, to switch them on and off from the check's own buttons (what a bug on one platform belongs to).
    val elevation = remember { BasicElevationCoverage() }
    val stars = remember { StarFieldLayer() }
    // Off to begin with: its scattering shader draws garbage on the iOS Simulator's software OpenGL (black triangles); on a device it can go on.
    val atmosphere = remember { AtmosphereLayer().apply { isEnabled = false } }
    var atmosphereOn by remember { mutableStateOf(false) }
    val counter = remember { FrameCounter() }
    val counterLast = remember { FrameCounter(peer = counter) }
    val background = remember { BackgroundLayer() }
    val blueMarble = remember { BlueMarbleLayer() }
    // Sharper maps for zooming in: NASA's Landsat (15 m) and Esri's world imagery (tiles down to the streets; check Esri's terms before shipping).
    val landsat = remember { BlueMarbleLandsatLayer().also { it.isEnabled = false } }
    val esri = remember {
        WebMercatorLayerFactory.createLayer(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "Esri imagery", "image/jpeg", maxZoom = 19,
        ).also { it.isEnabled = false }
    }
    var mapMode by remember { mutableStateOf(0) }
    // The library has no clock of its own (see Animatable): the host feeds it one tick per frame. Without this the selected shape does not pulse.
    LaunchedEffect(drawEngine) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            drawEngine.tick((now - last) / 1_000_000)
            last = now
        }
    }

    val windowState = rememberWorldWindowState { engine ->
        // As WorldWind's own Compose sample sets its globe up (worldwind-compose-samples BasicGlobe): an elevation model, stars, an atmosphere.
        engine.globe.elevationModel.addCoverage(elevation)
        engine.layers.addLayer(counter)
        engine.layers.addLayer(stars)
        engine.layers.addLayer(atmosphere)
        engine.layers.addLayer(background)
        engine.layers.addLayer(blueMarble)
        engine.layers.addLayer(landsat)
        engine.layers.addLayer(esri)
        engine.layers.addLayer(counterLast)
        val camera = GeoPosition(39.0, 33.0, 3_000_000.0).toWwkPosition()
        engine.camera.position.set(camera.latitude, camera.longitude, camera.altitude)
    }

    Box(Modifier.fillMaxSize()) {
        WorldWindow(
            state = windowState,
            modifier = Modifier.fillMaxSize(),
            onCreated = { window ->
                drawEngine.attachTo(window) // its layer, its touches, and its redraw requests
                redraw[0] = window::requestRedraw
            },
        )
        Box(Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 8.dp).background(Color(0xAA000000)).padding(8.dp)) { FpsMeter(counter) }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            when {
                draft != null -> {
                    Button("Finish") { drawEngine.finishDrawing() }
                    Button("Cancel") { drawEngine.cancel() }
                }
                // Editing: drag a corner, or the "+" on the middle of an edge to grow a new one.
                edit != null -> Button("Done") { drawEngine.stopEditing() }
                // A shape is selected (tap one): edit it, delete it, or let go of it.
                select != null -> {
                    Button("Edit") { drawEngine.edit(select.entityId) }
                    Button("Delete") { drawEngine.deleteSelected() }
                    Button("Close") { drawEngine.cancel() }
                }
                else -> Button("Draw") { drawEngine.startDrawing(DrawableEntityType.Polygon) }
            }
            Button("Outline") {
                val polygon = drawEngine.entities.value.filterIsInstance<DrawnPolygon>().firstOrNull { it.id.id == "flag" } ?: return@Button
                var effect: DrawOutlineEffect? = null
                effect = DrawOutlineEffect(drawEngine.repository, polygon, AnimationSpec(6000, easing = { it }), FLAME) { effect?.let(drawEngine::stop) }
                drawEngine.play(effect)
            }
            Button("Route") {
                var effect: RouteEffect? = null
                effect = RouteEffect(drawEngine.repository, EntityId("route"), DEMO_ROUTE, AnimationSpec(9000, easing = { it }), PLANE_ROUTE) { effect?.let(drawEngine::stop) }
                drawEngine.play(effect)
            }
            Button(listOf("Map: NASA", "Map: Landsat", "Map: Esri")[mapMode]) {
                mapMode = (mapMode + 1) % 3
                blueMarble.isEnabled = mapMode == 0
                landsat.isEnabled = mapMode == 1
                esri.isEnabled = mapMode == 2
                redraw[0]()
            }
            Button(if (atmosphereOn) "Atm on" else "Atm off") { atmosphereOn = !atmosphereOn; atmosphere.isEnabled = atmosphereOn; redraw[0]() }
            Button("Undo", enabled = canUndo) { drawEngine.undo() }
            Button("Redo", enabled = canRedo) { drawEngine.redo() }
        }
    }
}

@Composable
private fun Button(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Color(0xFF3B5BDB) else Color(0xFF555555))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { BasicText(label, style = TextStyle(color = Color.White, fontSize = 16.sp)) }
}
