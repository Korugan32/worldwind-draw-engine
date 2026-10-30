package com.tcsappdev.worldwinddraw.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
import androidx.compose.ui.unit.dp
import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.animation.AnimationController
import com.tcsappdev.worldwinddraw.core.animation.AnimationSpec
import com.tcsappdev.worldwinddraw.core.animation.Easing
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.morph.Snapshot
import com.tcsappdev.worldwinddraw.morph.SnapshotTransition
import com.tcsappdev.worldwinddraw.morph.restore
import com.tcsappdev.worldwinddraw.morph.snapshot
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HandleDetail
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.HighlightStyle
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import com.tcsappdev.worldwinddraw.booleanops.JtsOverlayEngine
import com.tcsappdev.worldwinddraw.serialization.DrawFormatException
import com.tcsappdev.worldwinddraw.serialization.DrawJson
import com.tcsappdev.worldwinddraw.serialization.GeoJson
import com.tcsappdev.worldwinddraw.serialization.ImportResult
import com.tcsappdev.worldwinddraw.serialization.Kml
import earth.worldwind.WorldWindow
import java.io.File

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
    // Merging and cutting polygons is JTS's (see the boolean-ops module); the state machine only gets it to enable the events.
    private val overlay = JtsOverlayEngine()
    private val stateMachine = DrawStateMachine(historyManager, entityRepository, overlay)
    private var worldWindow: WorldWindow? = null

    // The file formats the demo saves to and loads from, each in a file of the app's own storage (see DrawJson, GeoJson, Kml).
    private class FileFormat(val name: String, val file: String, val encode: (List<DrawableEntity>) -> String, val decode: (String) -> ImportResult)

    private val formats = listOf(
        FileFormat("drawing", "drawing.json", { DrawJson.encode(it, pretty = true) }, { ImportResult(DrawJson.decode(it), emptyList()) }),
        FileFormat("GeoJSON", "drawing.geojson", { GeoJson.encode(it, pretty = true) }, GeoJson::decode),
        FileFormat("KML", "drawing.kml", { Kml.encode(it, pretty = true) }, Kml::decode),
    )

    private fun save(format: FileFormat) {
        val shapes = entityRepository.entities.value
        val text = format.encode(shapes)
        File(filesDir, format.file).writeText(text)
        Toast.makeText(this, "Saved ${shapes.size} shapes as ${format.name} (${text.length / 1024} KB)", Toast.LENGTH_SHORT).show()
    }

    // Replaces what is on the map with the saved shapes. Dropping the history with it: its commands are about shapes that are gone.
    private fun load(format: FileFormat) {
        val file = File(filesDir, format.file)
        if (!file.exists()) {
            Toast.makeText(this, "No ${format.name} saved yet", Toast.LENGTH_SHORT).show()
            return
        }
        val result = try {
            format.decode(file.readText())
        } catch (e: DrawFormatException) {
            Toast.makeText(this, "Cannot load: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        result.warnings.forEach { android.util.Log.w("Demo", "${format.name}: $it") }
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        historyManager.clear()
        entityRepository.replaceAll(result.entities)
        val warnings = if (result.warnings.isEmpty()) "" else ", ${result.warnings.size} warnings (see logcat)"
        Toast.makeText(this, "Loaded ${result.entities.size} shapes$warnings", Toast.LENGTH_SHORT).show()
    }

    // Two moments of the drawing to play between (see SnapshotTransition), and the one that is playing.
    private val animationController = AnimationController()
    private var snapshotA: Snapshot? = null
    private var snapshotB: Snapshot? = null
    private var transition: SnapshotTransition? = null

    private fun keep(name: String) {
        val snapshot = entityRepository.snapshot()
        if (name == "A") snapshotA = snapshot else snapshotB = snapshot
        Toast.makeText(this, "Kept $name: ${snapshot.entities.size} shapes", Toast.LENGTH_SHORT).show()
    }

    // Plays the drawing from one kept moment to the other. A show, not an edit: the history is dropped (its commands are about shapes
    // that come and go) and editing is left first.
    private fun play(from: Snapshot?, to: Snapshot?, durationMs: Long = 2000) {
        if (from == null || to == null) {
            Toast.makeText(this, "Keep A and B first", Toast.LENGTH_SHORT).show()
            return
        }
        if (transition?.isFinished == false) return
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        historyManager.clear()
        lateinit var playing: SnapshotTransition
        playing = SnapshotTransition(entityRepository, from, to, AnimationSpec(durationMs, Easing { f -> f * f * (3 - 2 * f) })) {
            animationController.unregister(playing)
        }
        transition = playing
        animationController.register(playing)
    }

    // The ready-made morph scenarios (see MorphScenarios.kt). A scenario replaces the drawing on the map; what was there is kept
    // until "Back to my drawing".
    private val scenarios by lazy { morphScenarios(overlay) + extraMorphScenarios }
    private var drawingBeforeScenarios: Snapshot? = null
    private var slowMotion by mutableStateOf(false) // 12 seconds instead of 3, to look at what happens in between

    private fun runScenario(name: String) {
        if (transition?.isFinished == false) return
        val scenario = scenarios.first { it.name == name }
        if (drawingBeforeScenarios == null) drawingBeforeScenarios = entityRepository.snapshot()
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        historyManager.clear()
        entityRepository.restore(scenario.from)
        scenario.camera?.let { camera ->
            worldWindow?.let { window ->
                val target = camera.toWwkPosition()
                window.engine.camera.position.set(target.latitude, target.longitude, target.altitude)
                window.requestRedraw()
            }
        }
        play(scenario.from, scenario.to, durationMs = if (slowMotion) 12_000 else 3_000)
        Toast.makeText(this, "${scenario.name}: ${scenario.look}", Toast.LENGTH_LONG).show()
    }

    private fun backToMyDrawing() {
        if (transition?.isFinished == false) return
        val saved = drawingBeforeScenarios ?: return
        stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Idle))
        historyManager.clear()
        entityRepository.restore(saved)
        drawingBeforeScenarios = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entityRepository.add(demoPolygon())
        entityRepository.add(demoPolyline())
        entityRepository.add(demoMarker())
        entityRepository.add(demoPatternPolygon())
        entityRepository.add(demoFlagMarker())
        entityRepository.add(demoFlagPatternPolygon())
        entityRepository.add(demoFlagFillPolygon())
        entityRepository.add(demoOverlapPolygon())

        setContent {
            // DrawLayer.handleStyle can be changed live: this cycles it through three presets.
            var handleSizeIndex by remember { mutableIntStateOf(1) }
            var paletteIndex by remember { mutableIntStateOf(0) }
            // Handles on every point, or only the ones that matter at this zoom (see HandleDetail).
            var allPoints by remember { mutableStateOf(false) }
            // The live frame readout (see PerformanceReadout), and the map's window once it exists, which the readout needs to hook into.
            var performance by remember { mutableStateOf(false) }
            var window by remember { mutableStateOf<WorldWindow?>(null) }
            val palette = PALETTES[paletteIndex]
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorldWindowMap(
                            entityRepository = entityRepository,
                            stateMachine = stateMachine,
                            initialCameraPosition = GeoPosition(latitude = 39.0, longitude = 35.5, altitude = 3_000_000.0),
                            handleStyle = HANDLE_SIZES[handleSizeIndex].second.copy(
                                ringColor = palette.ring,
                                plusColor = palette.plus,
                                detail = if (allPoints) HandleDetail.All else HandleDetail.Adaptive,
                            ),
                            highlightStyle = palette.highlight,
                            onWorldWindow = { worldWindow = it; window = it },
                            animationController = animationController,
                        )
                        if (performance) PerformanceReadout(window, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 72.dp, top = 8.dp))
                        // The controls: a toolbar that follows what is being done, and the settings behind the gear (see DemoControls).
                        DemoControls(
                            stateMachine,
                            historyManager,
                            entityRepository,
                            settings = DemoSettings(
                                handleSizes = HANDLE_SIZES.map { it.first },
                                handleSize = handleSizeIndex,
                                onHandleSize = { handleSizeIndex = it },
                                palettes = PALETTES.map { it.label },
                                palette = paletteIndex,
                                onPalette = { paletteIndex = it },
                                allPoints = allPoints,
                                onAllPoints = { allPoints = it },
                                performance = performance,
                                onPerformance = { performance = it },
                                onEvictTextures = { worldWindow?.let { android.util.Log.i("Demo", "evicted ${it.engine.renderResourceCache.trimToAge(Long.MAX_VALUE)} bytes of textures"); it.requestRedraw() } },
                            ),
                            overlay = overlay,
                            // Saving and loading, in each of the file formats.
                            extraSettings = {
                                formats.forEach { format ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill("Save ${format.name}", selected = false) { save(format) }
                                        Pill("Load ${format.name}", selected = false) { load(format) }
                                    }
                                }
                                // Two moments of the drawing and a smooth play between them (shapes with the same points move, the rest fade).
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill("Keep A", selected = false) { keep("A") }
                                    Pill("Keep B", selected = false) { keep("B") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill("Play A→B", selected = false) { play(snapshotA, snapshotB) }
                                    Pill("Play B→A", selected = false) { play(snapshotB, snapshotA) }
                                }
                                // Ready-made scenarios: one tap puts the first drawing on the map and plays to the second (3 seconds, 12 in slow motion).
                                @Composable fun scenarioRow(title: String, vararg names: String) = SettingRow(title) { names.forEach { name -> Pill(name, selected = false) { runScenario(name) } } }
                                scenarioRow("Morph scenarios: one tap, plays at once", "Colour", "No fill", "Flag fill")
                                scenarioRow("Morph: pictures and looks", "Flag tiles", "Colour to flag")
                                scenarioRow("Morph: shapes that change", "New corners", "Other corners", "Appear and vanish")
                                scenarioRow("Morph: other", "Marker and line", "Merge", "Cut")
                                extraMorphScenarios.map { it.name }.chunked(2).forEachIndexed { index, names ->
                                    scenarioRow(if (index == 0) "Morph: more scenarios" else "", *names.toTypedArray())
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill("Slow motion", selected = slowMotion) { slowMotion = !slowMotion }
                                    Pill("Back to my drawing", selected = false) { backToMyDrawing() }
                                }
                            },
                            // Clear of the status bar, the gesture bar and the map's own zoom controls at the bottom left.
                            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                        )
                    }
                }
            }
        }
    }
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
    val bitmap = createBitmap(size, size)
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
internal fun turkishFlagBytes(width: Int): ByteArray {
    val height = width * 2 / 3
    val bitmap = createBitmap(width, height)
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

// A translucent shape that crosses the red one (and the water one north of it): something to merge into it or cut out of it.
private fun demoOverlapPolygon(): DrawnPolygon = DrawnPolygon(
    id = EntityId("demo-overlap-polygon"),
    style = ShapeStyle.SolidColor(ColorRgba(40, 90, 240, 150)),
    vertices = listOf(
        Vertex(VertexId("o1"), GeoPosition(latitude = 39.0, longitude = 30.0)),
        Vertex(VertexId("o2"), GeoPosition(latitude = 44.5, longitude = 30.0)),
        Vertex(VertexId("o3"), GeoPosition(latitude = 44.5, longitude = 37.0)),
        Vertex(VertexId("o4"), GeoPosition(latitude = 39.0, longitude = 37.0)),
    ),
    outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255, 255)),
    outlineWidth = 2f,
)
