package com.tcsappdev.worldwinddraw.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.LabelPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.state.DrawableEntityType
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import kotlin.math.roundToInt
import kotlin.random.Random

// The demo's controls: one slim toolbar at the bottom whose buttons follow what is going on (drawing, a shape selected, a shape being
// edited), a small status label above it, and a settings panel behind the gear. Nothing but the buttons that make sense right now is shown,
// so the map stays visible and every button is big enough to hit.

private val PANEL = Color(0xE61C1C22)
private val CHIP = Color(0xFF2C2C35)
private val ACCENT = Color(0xFF8AB4FF)
private val DANGER = Color(0xFFFF8A80)
private val TEXT = Color(0xFFECECF1)
private val MUTED = Color(0xFFA8A8B3)

/** What the settings panel changes, and how. */
class DemoSettings(
    val handleSizes: List<String>,
    val handleSize: Int,
    val onHandleSize: (Int) -> Unit,
    val palettes: List<String>,
    val palette: Int,
    val onPalette: (Int) -> Unit,
    val allPoints: Boolean,
    val onAllPoints: (Boolean) -> Unit,
    val onEvictTextures: () -> Unit,
    val performance: Boolean,
    val onPerformance: (Boolean) -> Unit,
)

private enum class Kind { Normal, Primary, Danger }

private class Tool(val icon: String, val label: String, val kind: Kind = Kind.Normal, val enabled: Boolean = true, val onClick: () -> Unit)

private enum class Panel { Settings, Simplify }

/**
 * The toolbar, the status label and the panels, all laid out inside one box that fills the screen's bottom edge and the gear's corner.
 * [extraSettings] lets a host add its own controls to the settings panel.
 */
@Composable
fun DemoControls(
    stateMachine: DrawStateMachine,
    historyManager: HistoryManager,
    entityRepository: EntityRepository,
    settings: DemoSettings,
    modifier: Modifier = Modifier,
    extraSettings: @Composable ColumnScope.() -> Unit = {},
) {
    val state by stateMachine.state.collectAsState()
    val canUndo by historyManager.canUndo.collectAsState()
    val canRedo by historyManager.canRedo.collectAsState()
    val entities by entityRepository.entities.collectAsState()
    var panel by remember { mutableStateOf<Panel?>(null) }
    var labelLook by remember { mutableIntStateOf(0) }
    var keepFraction by remember { mutableFloatStateOf(0.5f) }
    // The note being written, while its window is open (see the Note button).
    var noteText by remember { mutableStateOf<String?>(null) }

    val mode = state.mode
    val draft = mode as? DrawMode.Draw
    val edit = mode as? DrawMode.Edit
    val selectedId = (mode as? DrawMode.Select)?.entityId ?: edit?.entityId
    val selected = entities.firstOrNull { it.id == selectedId }
    val polygon = selected as? DrawnPolygon
    val marker = selected as? DrawnMarker
    val vertexCount = when (selected) {
        is DrawnPolygon -> selected.rings.sumOf { it.size }
        is DrawnPolyline -> selected.vertices.size
        else -> 0
    }

    val tools = ArrayList<Tool>()
    if (draft != null) {
        tools += Tool("✓", "Finish", Kind.Primary, enabled = draft.pendingVertices.size >= minimumPointsFor(draft.entityType)) {
            val ids = draft.pendingVertices.map { VertexId(Random.nextLong().toString()) }
            stateMachine.onEvent(DrawEvent.FinishDrawing(EntityId(Random.nextLong().toString()), ids))
        }
        tools += Tool("✕", "Cancel") {
            stateMachine.onEvent(DrawEvent.ModeChange(draft.targetEntityId?.let { DrawMode.Edit(it) } ?: DrawMode.Idle))
        }
    } else if (selected == null) {
        tools += Tool("✏", "Draw", Kind.Primary) { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList()))) }
    } else {
        if (edit == null) tools += Tool("✎", "Edit", Kind.Primary) { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(selected.id))) }
        else tools += Tool("✓", "Done", Kind.Primary) { stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Select(selected.id))) }
    }
    if (mode !is DrawMode.Idle && mode !is DrawMode.Pan) {
        tools += Tool("↶", "Undo", enabled = canUndo) { historyManager.undo() }
        tools += Tool("↷", "Redo", enabled = canRedo) { historyManager.redo() }
    }
    if (draft == null && selected != null) {
        if (edit != null && polygon != null) {
            tools += Tool("◌", "Add ring", enabled = edit.activeDrag == null) {
                stateMachine.onEvent(DrawEvent.ModeChange(DrawMode.Draw(DrawableEntityType.Polygon, emptyList(), targetEntityId = polygon.id)))
            }
        }
        if (edit != null && selected !is DrawnMarker) {
            tools += Tool("⌫", "Del point", enabled = edit.selectedVertexId != null) { stateMachine.onEvent(DrawEvent.DeleteSelectedVertex) }
            if (polygon != null && polygon.rings.size > 1) {
                tools += Tool("⊖", "Del ring", enabled = edit.selectedVertexId != null) { stateMachine.onEvent(DrawEvent.DeleteSelectedRing) }
            }
        }
        if (selected !is DrawnMarker && vertexCount > 6) {
            tools += Tool("≈", "Simplify") { panel = if (panel == Panel.Simplify) null else Panel.Simplify }
        }
        if (marker != null) {
            tools += Tool("Aa", "Label") {
                labelLook = (labelLook + 1) % DEMO_LABELS.size
                stateMachine.onEvent(DrawEvent.SetMarkerLabel(DEMO_LABELS[labelLook]))
            }
            tools += Tool("−", "Smaller") { stateMachine.onEvent(DrawEvent.ResizeMarker((marker.size * 0.8f).coerceAtLeast(8f))) }
            tools += Tool("+", "Bigger") { stateMachine.onEvent(DrawEvent.ResizeMarker((marker.size * 1.25f).coerceAtMost(240f))) }
        }
        tools += Tool("⇧", "Front") { stateMachine.onEvent(DrawEvent.BringToFront) }
        tools += Tool("⇩", "Back") { stateMachine.onEvent(DrawEvent.SendToBack) }
        tools += Tool("📝", "Note", enabled = edit?.activeDrag == null) { noteText = selected.description ?: "" }
        tools += Tool("🗑", "Delete", Kind.Danger) { stateMachine.onEvent(DrawEvent.DeleteSelected) }
    }

    // A window to write the selected shape's note in: what is written is the shape's description (an empty note takes it away).
    val writing = noteText
    if (writing != null && selected != null) {
        AlertDialog(
            onDismissRequest = { noteText = null },
            title = { Text("Note on ${selected.id.id}") },
            text = {
                OutlinedTextField(
                    value = writing,
                    onValueChange = { noteText = it },
                    placeholder = { Text("What is this shape?") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val note = writing.trim().ifEmpty { null }
                    if (note != selected.description) stateMachine.onEvent(DrawEvent.SetDescription(note))
                    noteText = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { noteText = null }) { Text("Cancel") } },
        )
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // The gear, top left: the settings are rarely needed, so they are one small button and a panel.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(PANEL)
                .clickable { panel = if (panel == Panel.Settings) null else Panel.Settings }
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("⚙", color = TEXT, fontSize = 20.sp) }

        if (panel == Panel.Settings) {
            SettingsPanel(settings, extraSettings, Modifier.align(Alignment.TopStart).padding(top = 56.dp))
        }

        Column(modifier = Modifier.align(Alignment.BottomStart), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            if (panel == Panel.Simplify && selected != null) {
                val target = maxOf(3 * (polygon?.rings?.size ?: 1).coerceAtLeast(1), (vertexCount * keepFraction).roundToInt())
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PANEL).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Slider(value = keepFraction, onValueChange = { keepFraction = it }, valueRange = 0.05f..1f, modifier = Modifier.widthIn(max = 150.dp))
                    Pill("$vertexCount → $target", selected = false, enabled = target < vertexCount) {
                        stateMachine.onEvent(DrawEvent.SimplifyShape(keepFraction))
                        panel = null
                    }
                }
            }
            StatusLabel(mode, selected, vertexCount)
            Toolbar(tools)
        }
    }
}

@Composable
private fun Toolbar(tools: List<Tool>) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(PANEL)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) { tools.forEach { ToolButton(it) } }
}

@Composable
private fun ToolButton(tool: Tool) {
    val tint = when (tool.kind) {
        Kind.Primary -> ACCENT
        Kind.Danger -> DANGER
        Kind.Normal -> TEXT
    }
    Column(
        modifier = Modifier
            .heightIn(min = 52.dp)
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (tool.kind == Kind.Primary) tint.copy(alpha = 0.16f) else CHIP)
            .alpha(if (tool.enabled) 1f else 0.35f)
            .clickable(enabled = tool.enabled, onClick = tool.onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        // A fixed-height slot, so the labels line up whatever the icons are (a small dot and a tall arrow are not the same height).
        Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            Text(tool.icon, color = tint, fontSize = 20.sp, textAlign = TextAlign.Center)
        }
        Text(tool.label, color = if (tool.kind == Kind.Normal) MUTED else tint, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** What the engine is doing, in a few words, above the toolbar: nothing at all while idle. */
@Composable
private fun StatusLabel(mode: DrawMode, selected: DrawableEntity?, vertexCount: Int) {
    val text = statusText(mode, selected, vertexCount) ?: return
    Text(
        text = text,
        color = TEXT,
        fontSize = 12.sp,
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PANEL)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun statusText(mode: DrawMode, selected: DrawableEntity?, vertexCount: Int): String? {
    val note = selected?.description?.let { "\n$it" } ?: ""
    return when (mode) {
        is DrawMode.Idle, is DrawMode.Pan -> null
        is DrawMode.Draw -> {
            val what = mode.targetEntityId?.let { "ring for ${it.id}" } ?: mode.entityType.toString().lowercase()
            "Drawing a $what · ${mode.pendingVertices.size} points · tap the map to add"
        }
        is DrawMode.Select -> "${selected?.id?.id ?: mode.entityId.id} selected$note"
        is DrawMode.Edit -> {
            val rings = (selected as? DrawnPolygon)?.rings?.size?.takeIf { it > 1 }?.let { " · $it rings" } ?: ""
            "Editing ${mode.entityId.id}" + (if (vertexCount > 0) " · $vertexCount points$rings" else "") + note
        }
    }
}

@Composable
private fun SettingsPanel(settings: DemoSettings, extra: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PANEL)
            .padding(14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        SettingRow("Handle size") {
            settings.handleSizes.forEachIndexed { i, name -> Pill(name, i == settings.handleSize) { settings.onHandleSize(i) } }
        }
        SettingRow("Colours") {
            settings.palettes.forEachIndexed { i, name -> Pill(name, i == settings.palette) { settings.onPalette(i) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("A handle on every point", color = TEXT, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = settings.allPoints, onCheckedChange = settings.onAllPoints)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Performance readout", color = TEXT, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = settings.performance, onCheckedChange = settings.onPerformance)
        }
        Pill("Evict textures (debug)", selected = false) { settings.onEvictTextures() }
        extra()
    }
}

@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        Text(title, color = MUTED, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { content() }
    }
}

/** A small rounded button for the panels: filled when [selected]. Also what hosts use for the controls they add to the settings. */
@Composable
fun Pill(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color(0xFF0B0B10) else TEXT,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ACCENT else CHIP)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// The looks "Label" steps through: none, then text above, below, beside and on a marker.
private val DEMO_LABELS: List<MarkerLabel?> = listOf(
    null,
    MarkerLabel("Marker"),
    MarkerLabel("Marker", color = ColorRgba(255, 230, 0), size = 20, position = LabelPosition.Below),
    MarkerLabel("Marker", color = ColorRgba(255, 255, 255, 120), size = 16, position = LabelPosition.Right),
    MarkerLabel("Marker", color = ColorRgba(255, 60, 60), size = 28, outlineColor = ColorRgba(255, 255, 255), position = LabelPosition.Center),
)

// Mirrors DrawEngineReducer.reduceFinishDrawing's per-type minimum-vertex guard, just so
// the "Finish" button's enabled state doesn't invite a tap it's going to silently ignore.
private fun minimumPointsFor(entityType: DrawableEntityType): Int = when (entityType) {
    DrawableEntityType.Polygon -> 3
    DrawableEntityType.Polyline -> 2
    DrawableEntityType.Marker -> 1
}
