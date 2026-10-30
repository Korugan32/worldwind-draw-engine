package com.tcsappdev.worldwinddraw.demo

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.OverlayEngine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import com.tcsappdev.worldwinddraw.morph.Snapshot

/**
 * A ready-made pair of drawings to play between, for looking at what a morph does: [from] is what the map shows first, [to] what it ends
 * on, and [look] says what to watch for. The built-in scenarios sit over Turkey, in the view the demo starts in; one that sits elsewhere
 * says where the camera should go in [camera].
 */
class MorphScenario(val name: String, val look: String, val from: Snapshot, val to: Snapshot, val camera: GeoPosition? = null)

/** More scenarios a host adds to the demo's own: they get their buttons in the settings panel too. */
var extraMorphScenarios: List<MorphScenario> = emptyList()

private val WHITE = ShapeStyle.SolidColor(ColorRgba(255, 255, 255))

private fun solid(r: Int, g: Int, b: Int, a: Int = 190) = ShapeStyle.SolidColor(ColorRgba(r, g, b, a))

private fun v(id: String, lat: Double, lon: Double) = Vertex(VertexId(id), GeoPosition(lat, lon))

/** Four corners from [lat], [lon] going north and east; [lean] moves the north edge east, making it a parallelogram. Ids `<id>-1..4`. */
private fun quad(
    id: String, style: ShapeStyle, lat: Double, lon: Double, height: Double, width: Double,
    lean: Double = 0.0, outlineWidth: Float = 3f,
) = DrawnPolygon(
    EntityId(id), style,
    listOf(v("$id-1", lat, lon), v("$id-2", lat, lon + width), v("$id-3", lat + height, lon + width + lean), v("$id-4", lat + height, lon + lean)),
    outlineStyle = WHITE, outlineWidth = outlineWidth,
)

/** [count] corners on an ellipse around [lat], [lon], ids `<id>-1..count`. */
private fun ring(id: String, style: ShapeStyle, lat: Double, lon: Double, radiusLat: Double, radiusLon: Double, count: Int) = DrawnPolygon(
    EntityId(id), style,
    List(count) { i ->
        val angle = 2 * Math.PI * i / count
        v("$id-${i + 1}", lat + radiusLat * Math.sin(angle), lon + radiusLon * Math.cos(angle))
    },
    outlineStyle = WHITE, outlineWidth = 3f,
)

private fun snapshot(vararg shapes: DrawableEntity) = Snapshot(shapes.toList())

fun morphScenarios(overlay: OverlayEngine): List<MorphScenario> {
    // Pictures from the demo's own drawing code: the flag as one picture (ImageFill) and as a small tile (ImagePattern).
    val flag = ImageRef.Bytes("flag-tr", turkishFlagBytes(width = 300))
    val flagTile = ImageRef.Bytes("flag-tr-tile", turkishFlagBytes(width = 90))

    // A merge and a cut, worked out by the real overlay, so what plays is what a user's merge or cut changes.
    val red = quad("a", solid(220, 30, 30), 35.0, 30.0, 5.0, 7.0)
    val blue = quad("b", solid(40, 90, 240), 38.0, 34.0, 5.0, 7.0)
    fun rebuilt(rings: List<List<Vertex>>) = red.copy(vertices = rings.first(), extraRings = rings.drop(1))
    var counter = 0
    val merged = rebuilt(overlay.union(red, blue) { VertexId("n-${++counter}") })
    counter = 0
    val cut = rebuilt(overlay.difference(red, blue) { VertexId("n-${++counter}") })

    return listOf(
        MorphScenario(
            "Colour", "corners slide, red turns blue, outline gets thicker",
            snapshot(quad("c", solid(220, 30, 30, 170), 35.0, 30.0, 4.0, 5.0, outlineWidth = 2f)),
            snapshot(quad("c", solid(30, 90, 230, 210), 38.0, 35.0, 6.0, 6.0, lean = 1.0, outlineWidth = 10f)),
        ),
        MorphScenario(
            "No fill", "an empty outline grows and fills with green",
            snapshot(quad("n", solid(0, 0, 0, 0), 35.0, 30.0, 3.0, 4.0, outlineWidth = 5f)),
            snapshot(quad("n", solid(40, 200, 90, 200), 37.0, 33.0, 6.0, 8.0, outlineWidth = 5f)),
        ),
        MorphScenario(
            "Flag fill", "a flag fades in while its shape slides and grows",
            snapshot(quad("f", ShapeStyle.ImageFill(flag, ImageFit.Contain, opacity = 0f), 35.0, 30.0, 3.0, 4.0)),
            snapshot(quad("f", ShapeStyle.ImageFill(flag, ImageFit.Contain, opacity = 0.95f), 35.0, 32.0, 8.0, 8.0)),
        ),
        MorphScenario(
            "Flag tiles", "tiled flag gets more opaque while the shape moves",
            snapshot(quad("t", ShapeStyle.ImagePattern(flagTile, opacity = 0.15f), 36.0, 30.0, 3.0, 6.0)),
            snapshot(quad("t", ShapeStyle.ImagePattern(flagTile, opacity = 0.85f), 39.0, 34.0, 5.0, 7.0, lean = 1.0)),
        ),
        MorphScenario(
            "Colour to flag", "shape slides; red turns into the flag at half way",
            snapshot(quad("k", solid(220, 30, 30, 200), 35.0, 30.0, 4.0, 5.0)),
            snapshot(quad("k", ShapeStyle.ImageFill(flag, ImageFit.Contain, opacity = 0.95f), 37.0, 34.0, 6.0, 7.0)),
        ),
        MorphScenario(
            "New corners", "the square gets 8 more corners: they grow out of its edges while it slides and turns purple",
            snapshot(quad("s", solid(255, 140, 0), 35.0, 30.0, 5.0, 5.0)),
            snapshot(ring("s", solid(150, 50, 220), 39.0, 35.0, 3.5, 4.0, count = 12)),
        ),
        MorphScenario(
            "Other corners", "no corner in common: the square fades out, the round one fades in",
            snapshot(quad("s", solid(255, 140, 0), 35.0, 30.0, 5.0, 5.0)),
            snapshot(ring("o", solid(150, 50, 220), 39.0, 35.0, 3.5, 4.0, count = 12).copy(id = EntityId("s"))),
        ),
        MorphScenario(
            "Appear and vanish", "red moves, blue fades away, green and marker fade in",
            snapshot(
                quad("stay", solid(220, 30, 30), 35.0, 30.0, 3.0, 5.0),
                ring("gone", solid(40, 90, 240), 41.0, 32.0, 2.5, 3.0, count = 3),
            ),
            snapshot(
                quad("stay", solid(220, 30, 30), 35.5, 35.0, 4.0, 6.0),
                ring("born", solid(40, 200, 90), 42.0, 38.0, 2.5, 3.0, count = 5),
                DrawnMarker(EntityId("born-marker"), ShapeStyle.SolidColor(ColorRgba(255, 255, 0)), GeoPosition(42.0, 38.0), size = 28f),
            ),
        ),
        MorphScenario(
            "Marker and line", "marker travels and grows, line swings and gets thick",
            snapshot(
                DrawnMarker(
                    EntityId("m"), ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), GeoPosition(41.0, 31.0), size = 18f,
                    label = MarkerLabel("Trip", ColorRgba(255, 255, 255), outlineColor = ColorRgba(0, 0, 0)),
                ),
                DrawnPolyline(EntityId("l"), ShapeStyle.SolidColor(ColorRgba(255, 220, 0)), listOf(v("l-1", 36.0, 30.0), v("l-2", 37.0, 34.0)), lineWidth = 2f),
            ),
            snapshot(
                DrawnMarker(
                    EntityId("m"), ShapeStyle.SolidColor(ColorRgba(255, 60, 60)), GeoPosition(36.0, 40.0), size = 56f,
                    label = MarkerLabel("Trip", ColorRgba(255, 230, 0), outlineColor = ColorRgba(0, 0, 0)),
                ),
                DrawnPolyline(EntityId("l"), ShapeStyle.SolidColor(ColorRgba(255, 0, 200)), listOf(v("l-1", 44.0, 31.0), v("l-2", 38.0, 41.0)), lineWidth = 14f),
            ),
        ),
        MorphScenario(
            "Merge", "red grows over blue, which fades away (real merge result)",
            snapshot(red, blue),
            snapshot(merged),
        ),
        MorphScenario(
            "Cut", "red is cut back where blue overlaps it (real cut result)",
            snapshot(red, blue),
            snapshot(cut, blue),
        ),
    )
}
