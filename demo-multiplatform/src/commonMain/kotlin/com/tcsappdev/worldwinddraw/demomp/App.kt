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
import com.tcsappdev.worldwinddraw.adapter.input.PickInputBridge
import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.mapper.preloadImage
import com.tcsappdev.worldwinddraw.booleanops.JtsOverlayEngine
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.animation.AnimationController
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import earth.worldwind.compose.WorldWindow
import earth.worldwind.compose.rememberWorldWindowState
import earth.worldwind.globe.elevation.coverage.BasicElevationCoverage
import earth.worldwind.globe.elevation.coverage.ElevationCoverage
import earth.worldwind.layer.BackgroundLayer
import earth.worldwind.layer.atmosphere.AtmosphereLayer
import earth.worldwind.layer.starfield.StarFieldLayer
import earth.worldwind.layer.BlueMarbleLayer
import earth.worldwind.layer.Layer
import kotlin.random.Random

/** The same map and the same draw engine on every platform, with a few buttons. */
@Composable
fun App() {
    val repository = remember { EntityRepository().apply { checkShapes().forEach(::add) } }
    val history = remember { HistoryManager() }
    val stateMachine = remember { DrawStateMachine(history, repository, JtsOverlayEngine()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        preloadImage(FLAG_BYTES)
        pictureShapes().forEach(repository::add)
    }
    val state by stateMachine.state.collectAsState()
    val canUndo by history.canUndo.collectAsState()
    val canRedo by history.canRedo.collectAsState()
    val mode = state.mode
    val draft = mode as? DrawMode.Draw
    val select = mode as? DrawMode.Select
    val edit = mode as? DrawMode.Edit
    // The draw layer asks for a redraw when its content changes; the window it asks is only known once the map exists.
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
    // The library has no clock of its own (see Animatable): the host feeds it one tick per frame. Without this the selected shape does not pulse.
    val animationController = remember { AnimationController() }
    val drawLayer = remember {
        DrawLayer(repository, stateMachine, scope, onContentChanged = { redraw[0]() }, buildOnFirstFrame = true).also { animationController.register(it) }
    }
    LaunchedEffect(animationController) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            animationController.tick((now - last) / 1_000_000)
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
        engine.layers.addLayer(drawLayer)
        engine.layers.addLayer(counterLast)
        val camera = GeoPosition(39.0, 33.0, 3_000_000.0).toWwkPosition()
        engine.camera.position.set(camera.latitude, camera.longitude, camera.altitude)
    }

    Box(Modifier.fillMaxSize()) {
        WorldWindow(
            state = windowState,
            modifier = Modifier.fillMaxSize(),
            onCreated = { window ->
                window.selectDragDetector.callback = PickInputBridge(stateMachine)
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
                    Button("Finish") {
                        val ids = draft.pendingVertices.map { VertexId(Random.nextLong().toString()) }
                        stateMachine.onEvent(DrawEvent.FinishDrawing(EntityId(Random.nextLong().toString()), ids))
                    }
                    Button("Cancel") { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle)) }
                }
                // Editing: drag a corner, or the "+" on the middle of an edge to grow a new one.
                edit != null -> Button("Done") { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(edit.entityId))) }
                // A shape is selected (tap one): edit it, delete it, or let go of it.
                select != null -> {
                    Button("Edit") { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(select.entityId))) }
                    Button("Delete") { stateMachine.onEvent(DrawEvent.DeleteSelected) }
                    Button("Close") { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle)) }
                }
                else -> Button("Draw") { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList()))) }
            }
            Button(if (atmosphereOn) "Atm on" else "Atm off") { atmosphereOn = !atmosphereOn; atmosphere.isEnabled = atmosphereOn; redraw[0]() }
            Button("Undo", enabled = canUndo) { history.undo() }
            Button("Redo", enabled = canRedo) { history.redo() }
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
