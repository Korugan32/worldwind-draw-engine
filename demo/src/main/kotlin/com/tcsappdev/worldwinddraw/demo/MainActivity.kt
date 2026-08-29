package com.tcsappdev.worldwinddraw.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

class MainActivity : ComponentActivity() {
    private val entityRepository = EntityRepository()
    private val stateMachine = DrawStateMachine(HistoryManager())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entityRepository.add(demoPolygon())
        entityRepository.add(demoPolyline())
        entityRepository.add(demoMarker())

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorldWindowMap(
                            entityRepository = entityRepository,
                            stateMachine = stateMachine,
                            initialCameraPosition = GeoPosition(latitude = 39.0, longitude = 35.5, altitude = 3_000_000.0),
                        )
                        // Visual proof that real map taps reach PickInputBridge -> DrawEngineReducer:
                        // toggling "Draw" mode here, then tapping the map, accumulates
                        // pendingVertices from the actual pick position of each tap.
                        DrawModeOverlay(stateMachine, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawModeOverlay(stateMachine: DrawStateMachine, modifier: Modifier = Modifier) {
    val state by stateMachine.state.collectAsState()
    val isDrawing = state.mode is DrawMode.Draw

    Column(modifier = modifier) {
        Button(onClick = {
            val nextMode = if (isDrawing) DrawMode.Idle else DrawMode.Draw(DrawableEntityType.Polygon, emptyList())
            stateMachine.onEvent(DrawEvent.ModeChange(nextMode))
        }) {
            Text(if (isDrawing) "Stop Drawing" else "Start Drawing")
        }
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
    is DrawMode.Draw -> "Draw (${mode.entityType}, ${mode.pendingVertices.size} points)"
    is DrawMode.Edit -> "Edit (${mode.entityId.id})"
    is DrawMode.Select -> "Select (${mode.entityId.id})"
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
