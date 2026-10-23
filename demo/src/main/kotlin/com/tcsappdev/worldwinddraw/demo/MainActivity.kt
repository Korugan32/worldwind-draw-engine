package com.tcsappdev.worldwinddraw.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.WorldWindow
import kotlin.random.Random

private class Palette(val label: String, val highlight: HighlightStyle, val ring: ColorRgba, val plus: ColorRgba)

// HighlightStyle/HandleStyle colours can be changed live too: this cycles through three palettes.
private val PALETTES = listOf(
    Palette("Default", HighlightStyle(), HandleStyle().ringColor, HandleStyle().plusColor),
    Palette(
        "Amber/Violet",
        HighlightStyle(selectedColor = ColorRgba(255, 193, 7), editingColor = ColorRgba(156, 39, 176)),
        ring = ColorRgba(74, 20, 140),
        plus = ColorRgba(156, 39, 176),
    ),
    Palette(
        "Mono",
        HighlightStyle(selectedColor = ColorRgba(255, 255, 255), editingColor = ColorRgba(255, 255, 255, 220)),
        ring = ColorRgba(0, 0, 0),
        plus = ColorRgba(0, 0, 0),
    ),
)

private val HANDLE_SIZES = listOf(
    "S" to HandleStyle(vertexDiameter = 18f, midpointDiameter = 16f),
    "M" to HandleStyle(),
    "L" to HandleStyle(vertexDiameter = 40f, midpointDiameter = 36f),
)

class MainActivity : ComponentActivity() {
    private val entityRepository = EntityRepository()
    private val historyManager = HistoryManager()
    private val stateMachine = DrawStateMachine(historyManager, entityRepository)
    private var worldWindow: WorldWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entityRepository.add(demoPolygon())
        entityRepository.add(demoPolyline())
        entityRepository.add(demoMarker())
        entityRepository.add(demoPatternPolygon())
        entityRepository.add(demoFlagMarker())
        entityRepository.add(demoFlagPatternPolygon())
        entityRepository.add(demoFlagFillPolygon())

        setContent {
            // DrawLayer.handleStyle can be changed live: this cycles it through three presets.
            var handleSizeIndex by remember { mutableStateOf(1) }
            var paletteIndex by remember { mutableStateOf(0) }
            val palette = PALETTES[paletteIndex]
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorldWindowMap(
                            entityRepository = entityRepository,
                            stateMachine = stateMachine,
                            initialCameraPosition = GeoPosition(latitude = 39.0, longitude = 35.5, altitude = 3_000_000.0),
                            handleStyle = HANDLE_SIZES[handleSizeIndex].second.copy(ringColor = palette.ring, plusColor = palette.plus),
                            highlightStyle = palette.highlight,
                            onWorldWindow = { worldWindow = it },
                        )
                        // Visual proof that real map taps reach PickInputBridge -> DrawEngineReducer:
                        // toggling "Draw" mode here, then tapping the map, accumulates
                        // pendingVertices from the actual pick position of each tap. The Undo
                        // button proves a dragged vertex is recorded as ONE undoable command
                        // (see ReduceResult's KDoc on commands vs. previewCommands).
                        DrawModeOverlay(
                            stateMachine,
                            historyManager,
                            entityRepository,
                            handleSizeLabel = HANDLE_SIZES[handleSizeIndex].first,
                            onCycleHandleSize = { handleSizeIndex = (handleSizeIndex + 1) % HANDLE_SIZES.size },
                            paletteLabel = palette.label,
                            onCyclePalette = { paletteIndex = (paletteIndex + 1) % PALETTES.size },
                            onEvictTextures = { worldWindow?.let { android.util.Log.i("Demo", "evicted ${it.engine.renderResourceCache.trimToAge(Long.MAX_VALUE)} bytes of textures"); it.requestRedraw() } },
                            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawModeOverlay(
    stateMachine: DrawStateMachine,
    historyManager: HistoryManager,
    entityRepository: EntityRepository,
    handleSizeLabel: String,
    onCycleHandleSize: () -> Unit,
    paletteLabel: String,
    onCyclePalette: () -> Unit,
    onEvictTextures: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by stateMachine.state.collectAsState()
    val canUndo by historyManager.canUndo.collectAsState()
    val canRedo by historyManager.canRedo.collectAsState()
    val drawMode = state.mode as? DrawMode.Draw
    val isDrawing = drawMode != null

    Column(modifier = modifier) {
        Button(onClick = {
            val nextMode = if (isDrawing) DrawMode.Idle else DrawMode.Draw(DrawableEntityType.Polygon, emptyList())
            stateMachine.onEvent(DrawEvent.ModeChange(nextMode))
        }) {
            Text(if (isDrawing) "Stop Drawing" else "Start Drawing")
        }
        // Only enabled once there are enough points for the entity type being drawn — see
        // DrawnPolygon/DrawnPolyline's own minimum-vertex-count rules, which
        // DrawEngineReducer.reduceFinishDrawing re-checks anyway (this is just so the button
        // doesn't invite a tap it's going to silently ignore).
        val canFinish = drawMode != null && drawMode.pendingVertices.size >= minimumPointsFor(drawMode.entityType)
        val canDelete = state.mode is DrawMode.Select || state.mode is DrawMode.Edit
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Button(
                onClick = {
                    val vertices = drawMode?.pendingVertices ?: return@Button
                    val entityId = EntityId(Random.nextLong().toString())
                    val vertexIds = vertices.map { VertexId(Random.nextLong().toString()) }
                    stateMachine.onEvent(DrawEvent.FinishDrawing(entityId, vertexIds))
                },
                enabled = canFinish,
            ) {
                Text("Finish")
            }
            // Deletes the selected/edited shape; Undo brings it back.
            Button(
                onClick = { stateMachine.onEvent(DrawEvent.DeleteSelected) },
                enabled = canDelete,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text("Delete")
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = { historyManager.undo() }, enabled = canUndo) {
                Text("Undo")
            }
            Button(onClick = { historyManager.redo() }, enabled = canRedo, modifier = Modifier.padding(start = 4.dp)) {
                Text("Redo")
            }
        }
        Button(onClick = onCycleHandleSize, modifier = Modifier.padding(top = 4.dp)) {
            Text("Handles: $handleSizeLabel")
        }
        Button(onClick = onCyclePalette, modifier = Modifier.padding(top = 4.dp)) {
            Text("Colors: $paletteLabel")
        }
        // Resizes the selected/edited marker (undoable while editing); drag it in edit mode to move it.
        val entities by entityRepository.entities.collectAsState()
        val selectedId = (state.mode as? DrawMode.Select)?.entityId ?: (state.mode as? DrawMode.Edit)?.entityId
        val selectedMarker = entities.firstOrNull { it.id == selectedId } as? DrawnMarker
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Button(
                onClick = { selectedMarker?.let { stateMachine.onEvent(DrawEvent.ResizeMarker((it.size * 0.8f).coerceAtLeast(8f))) } },
                enabled = selectedMarker != null,
            ) { Text("Size -") }
            Button(
                onClick = { selectedMarker?.let { stateMachine.onEvent(DrawEvent.ResizeMarker((it.size * 1.25f).coerceAtMost(240f))) } },
                enabled = selectedMarker != null,
                modifier = Modifier.padding(start = 4.dp),
            ) { Text("Size +") }
        }
        // Debug aid: evicts every texture from WWK's cache the way it does for stale ones or under memory pressure (they are released on the next frame), so every picture is made again.
        Button(onClick = onEvictTextures, modifier = Modifier.padding(top = 4.dp)) { Text("Evict textures") }
        Text(
            text = describeMode(state.mode),
            color = Color.White,
            modifier = Modifier
                .padding(top = 8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp),
        )
    }
}

private fun describeMode(mode: DrawMode): String = when (mode) {
    is DrawMode.Idle -> "Idle"
    is DrawMode.Pan -> "Pan"
    is DrawMode.Draw -> "Draw (${mode.entityType}, ${mode.pendingVertices.size} points)" + when (val drag = mode.activeDrag) {
        is DrawMode.Draw.ActiveDrag.MovingPoint -> " - moving point ${drag.index}"
        is DrawMode.Draw.ActiveDrag.InsertingPoint -> " - adding a point on edge ${drag.edgeIndex}"
        null -> ""
    }
    is DrawMode.Edit -> when (val drag = mode.activeDrag) {
        is DrawMode.Edit.ActiveDrag.ExistingVertex -> "Edit (${mode.entityId.id}) - dragging ${drag.vertexId.id}"
        is DrawMode.Edit.ActiveDrag.NewVertex -> "Edit (${mode.entityId.id}) - adding a vertex on edge ${drag.edgeIndex}"
        is DrawMode.Edit.ActiveDrag.MovingMarker -> "Edit (${mode.entityId.id}) - moving the marker"
        null -> "Edit (${mode.entityId.id}) - drag a vertex, a midpoint handle or a marker"
    }
    is DrawMode.Select -> "Select (${mode.entityId.id}) - tap again to edit"
}

// Mirrors DrawEngineReducer.reduceFinishDrawing's per-type minimum-vertex guard, just so
// the "Finish" button's enabled state doesn't invite a tap it's going to silently ignore.
private fun minimumPointsFor(entityType: DrawableEntityType): Int = when (entityType) {
    DrawableEntityType.Polygon -> 3
    DrawableEntityType.Polyline -> 2
    DrawableEntityType.Marker -> 1
}

// A rectangle over Turkey, semi-transparent so the base imagery stays visible underneath —
// this is a placeholder shape only, standing in for a real drawing UI that doesn't exist yet.
// The outline is solid white and thick, so it's visually distinct from the fill and from
// the polyline's yellow — exercises DrawnPolygon's separate interior/outline styles.
private fun demoPolygon(): DrawnPolygon = DrawnPolygon(
    id = EntityId("demo-polygon"),
    style = ShapeStyle.SolidColor(ColorRgba(red = 220, green = 30, blue = 30, alpha = 140)),
    vertices = listOf(
        Vertex(VertexId("v1"), GeoPosition(latitude = 36.0, longitude = 26.0)),
        Vertex(VertexId("v2"), GeoPosition(latitude = 42.0, longitude = 26.0)),
        Vertex(VertexId("v3"), GeoPosition(latitude = 42.0, longitude = 45.0)),
        Vertex(VertexId("v4"), GeoPosition(latitude = 36.0, longitude = 45.0)),
    ),
    outlineStyle = ShapeStyle.SolidColor(ColorRgba(red = 255, green = 255, blue = 255, alpha = 255)),
    outlineWidth = 4f,
)

// A thick yellow line cutting across the polygon — exercises DrawnPolyline's separate
// "style applies to the stroke, not a fill" path (see ShapeStyle's KDoc) and its
// lineWidth, distinct from the polygon's thin default WWK outline.
private fun demoPolyline(): DrawnPolyline = DrawnPolyline(
    id = EntityId("demo-polyline"),
    style = ShapeStyle.SolidColor(ColorRgba(red = 255, green = 220, blue = 0, alpha = 255)),
    vertices = listOf(
        Vertex(VertexId("p1"), GeoPosition(latitude = 41.0, longitude = 29.0)),
        Vertex(VertexId("p2"), GeoPosition(latitude = 39.9, longitude = 32.8)),
        Vertex(VertexId("p3"), GeoPosition(latitude = 37.0, longitude = 35.3)),
    ),
    lineWidth = 5f,
)

// A marker at the polyline's start (Istanbul) — no image source, so it renders as a plain
// SolidColor square at `size` pixels (see DrawnMarker's KDoc for why size can't default
// to something imperceptibly small).
private fun demoMarker(): DrawnMarker = DrawnMarker(
    id = EntityId("demo-marker"),
    style = ShapeStyle.SolidColor(ColorRgba(red = 0, green = 255, blue = 120, alpha = 255)),
    position = GeoPosition(latitude = 41.0082, longitude = 28.9784),
    size = 30f,
)

// A water-like tile, built here as the bytes of a PNG: stands in for what a real app gets from a photo
// picker, a file or its own assets (see ImageRef.Bytes). No network is involved.
private fun demoPatternBytes(): ByteArray {
    val size = 96
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(30, 110, 190))
    val wave = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(140, 200, 245)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    for (row in 0 until 3) {
        val y = 16f + row * 32f
        val path = android.graphics.Path().apply {
            moveTo(0f, y)
            quadTo(size / 4f, y - 12f, size / 2f, y)
            quadTo(size * 3 / 4f, y + 12f, size.toFloat(), y)
        }
        canvas.drawPath(path, wave)
    }
    return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

// Filled with that tile at 60% opacity, so the map shows through the pattern.
private fun demoPatternPolygon(): DrawnPolygon = DrawnPolygon(
    id = EntityId("demo-pattern-polygon"),
    style = ShapeStyle.ImagePattern(ImageRef.Bytes(key = "demo-water-tile", data = demoPatternBytes()), opacity = 0.6f),
    vertices = listOf(
        Vertex(VertexId("w1"), GeoPosition(latitude = 43.2, longitude = 33.0)),
        Vertex(VertexId("w2"), GeoPosition(latitude = 46.0, longitude = 33.0)),
        Vertex(VertexId("w3"), GeoPosition(latitude = 46.0, longitude = 40.0)),
        Vertex(VertexId("w4"), GeoPosition(latitude = 43.2, longitude = 40.0)),
    ),
    outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255, 255)),
    outlineWidth = 2f,
)

// The Turkish flag (3:2) drawn at [width] pixels, as the bytes of a PNG — a stylised version: the
// crescent and star are close to the official construction, not exact. Flags are a good test image:
// recognisable, and asymmetric, so mirroring or rotating would show.
private fun turkishFlagBytes(width: Int): ByteArray {
    val height = width * 2 / 3
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val u = height / 2f // the flag is 3 units wide and 2 units tall
    val red = android.graphics.Color.rgb(227, 10, 23)
    canvas.drawColor(red)
    val white = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val redPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = red }
    canvas.drawCircle(1.0f * u, 1.0f * u, 0.5f * u, white)       // crescent: a white disc...
    canvas.drawCircle(1.125f * u, 1.0f * u, 0.4f * u, redPaint)  // ...with a smaller red disc cut out, opening to the right
    val star = android.graphics.Path()
    val centerX = 1.65f * u
    val centerY = 1.0f * u
    val outer = 0.25f * u
    val inner = outer * 0.382f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = Math.PI + i * Math.PI / 5 // a point faces left, toward the crescent
        val x = centerX + (radius * Math.cos(angle)).toFloat()
        val y = centerY + (radius * Math.sin(angle)).toFloat()
        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    canvas.drawPath(star, white)
    return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

// One flag as a marker icon, 64 px wide on screen whatever size the source image is (see ImageRef.Bytes).
private fun demoFlagMarker(): DrawnMarker = DrawnMarker(
    id = EntityId("demo-flag-marker"),
    style = ShapeStyle.ImagePattern(ImageRef.Bytes(key = "flag-tr", data = turkishFlagBytes(width = 300))),
    position = GeoPosition(latitude = 39.93, longitude = 32.86), // Ankara
    size = 64f,
)

// A small flag tiled across a polygon at 55% opacity: this is what a pattern does with ANY image. Showing one flag
// stretched over a whole shape is a different feature (see docs/PLAN.md, Faz 10).
private fun demoFlagPatternPolygon(): DrawnPolygon = DrawnPolygon(
    id = EntityId("demo-flag-pattern-polygon"),
    style = ShapeStyle.ImagePattern(ImageRef.Bytes(key = "flag-tr-tile", data = turkishFlagBytes(width = 90)), opacity = 0.55f),
    vertices = listOf(
        Vertex(VertexId("f1"), GeoPosition(latitude = 33.2, longitude = 29.0)),
        Vertex(VertexId("f2"), GeoPosition(latitude = 35.0, longitude = 29.0)),
        Vertex(VertexId("f3"), GeoPosition(latitude = 35.0, longitude = 37.0)),
        Vertex(VertexId("f4"), GeoPosition(latitude = 33.2, longitude = 37.0)),
    ),
    outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255, 255)),
    outlineWidth = 2f,
)

// ONE flag, centred in the shape's bounding box and cut to the shape (a concave one, with a notch in its
// south edge) — not repeated. Contain keeps the whole flag and its proportions; opacity lets the map through.
private fun demoFlagFillPolygon(): DrawnPolygon = DrawnPolygon(
    id = EntityId("demo-flag-fill-polygon"),
    style = ShapeStyle.ImageFill(
        image = ImageRef.Bytes(key = "flag-tr", data = turkishFlagBytes(width = 300)),
        fit = ImageFit.Contain,
        opacity = 0.9f,
    ),
    vertices = listOf(
        Vertex(VertexId("g1"), GeoPosition(latitude = 28.8, longitude = 26.0)),
        Vertex(VertexId("g2"), GeoPosition(latitude = 32.8, longitude = 26.0)),
        Vertex(VertexId("g3"), GeoPosition(latitude = 32.8, longitude = 38.0)),
        Vertex(VertexId("g4"), GeoPosition(latitude = 28.8, longitude = 38.0)),
        Vertex(VertexId("g5"), GeoPosition(latitude = 30.6, longitude = 32.0)),
    ),
    outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255, 255)),
    outlineWidth = 2f,
)
