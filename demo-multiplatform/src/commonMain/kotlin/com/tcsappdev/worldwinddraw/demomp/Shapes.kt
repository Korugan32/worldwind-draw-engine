package com.tcsappdev.worldwinddraw.demomp

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private fun v(id: String, lat: Double, lon: Double) = Vertex(VertexId(id), GeoPosition(lat, lon))

// A small three-stripe flag (60x40 PNG), to see that pictures work on this platform: one picture cut to a shape, and one tiled.
private const val FLAG_PNG = "iVBORw0KGgoAAAANSUhEUgAAADwAAAAoCAYAAACiu5n/AAAAqUlEQVR42u3XMRJAMBCF4ZVxAU5AoeNCrk3vBq5ApSGFkN1k+LcME755JnYLEdkkYk1NFXM7GcYu6n5OflaAAQMGDBgwYMCAtaq0fmC/rJe1ua2/B/ZBz9cs4C419sl9WYNDEdpolxPWAs1vKXW62imTMGDAgL31tmvS6rpIOIeUNXtq9YRDX157gDD5pO8iLKYls/HwwPxmHk6B45QGDBgwYMCAAQO2rB2ExSgOm0dZrAAAAABJRU5ErkJggg=="

@OptIn(ExperimentalEncodingApi::class)
internal val FLAG_BYTES: ByteArray by lazy { Base64.decode(FLAG_PNG) }

private val WHITE = ShapeStyle.SolidColor(ColorRgba(255, 255, 255))

/** A few shapes to look at when the map opens: what each kind of shape and look does on this platform. */
fun checkShapes(): List<DrawableEntity> = listOf(
    DrawnPolygon(
        EntityId("solid"), ShapeStyle.SolidColor(ColorRgba(220, 30, 30, 190)),
        listOf(v("s1", 38.0, 30.0), v("s2", 38.0, 36.0), v("s3", 42.0, 36.0), v("s4", 42.0, 30.0)),
        outlineStyle = WHITE, outlineWidth = 3f,
    ),
    DrawnPolygon(
        EntityId("hatch"), ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(255, 255, 255), ColorRgba(40, 90, 240, 160), spacing = 24, thickness = 3f),
        listOf(v("h1", 35.0, 37.0), v("h2", 35.0, 43.0), v("h3", 39.0, 43.0), v("h4", 39.0, 37.0)),
        outlineStyle = WHITE, outlineWidth = 3f,
    ),
    DrawnPolyline(
        EntityId("line"), ShapeStyle.SolidColor(ColorRgba(255, 220, 0)),
        listOf(v("l1", 36.0, 26.0), v("l2", 40.0, 29.0), v("l3", 37.0, 33.0)), lineWidth = 5f,
    ),
    DrawnMarker(EntityId("marker"), ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), GeoPosition(41.0, 33.0), size = 22f),
)

/** The shapes whose look is a picture. In the browser the picture has to be loaded first (preloadImage), so these are added after that. */
fun pictureShapes(): List<DrawableEntity> = listOf(
    DrawnPolygon(
        EntityId("flag"), ShapeStyle.ImageFill(ImageRef.Bytes("flag", FLAG_BYTES), ImageFit.Contain, extendEdges = true),
        listOf(v("f1", 32.5, 30.0), v("f2", 32.5, 36.0), v("f3", 35.5, 36.0), v("f4", 35.5, 30.0)),
        outlineStyle = WHITE, outlineWidth = 3f,
    ),
    DrawnPolygon(
        EntityId("tiles"), ShapeStyle.ImagePattern(ImageRef.Bytes("flag-tile", FLAG_BYTES), opacity = 0.9f),
        listOf(v("t1", 42.5, 37.0), v("t2", 42.5, 43.0), v("t3", 45.5, 43.0), v("t4", 45.5, 37.0)),
        outlineStyle = WHITE, outlineWidth = 3f,
    ),
)
