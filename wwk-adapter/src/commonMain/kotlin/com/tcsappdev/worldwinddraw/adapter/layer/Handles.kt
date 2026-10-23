package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.adapter.mapper.toGeoPosition
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkColor
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPosition
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.style.HandleStyle
import earth.worldwind.geom.AltitudeMode
import earth.worldwind.geom.Position
import earth.worldwind.render.Color
import earth.worldwind.render.Renderable
import earth.worldwind.shape.PathType
import earth.worldwind.shape.Placemark
import earth.worldwind.shape.PlacemarkAttributes

/**
 * A vertex handle is two renderables at the same spot: the visible dot, which is *not*
 * pickable, and a transparent square [HandleStyle.touchTargetDiameter] wide that carries the
 * tags `PickInputBridge` reads ([tag] sets them) and is what a finger actually grabs. The split
 * exists because WWK's pick only sees what is really drawn, so the dot alone could only be
 * grabbed within its own ~12px radius; an invisible square draws nothing yet still counts in
 * the pick pass (in pick mode WWK paints an image-less placemark as a full pick-coloured square
 * and ignores its colour's alpha).
 *
 * Used for the handles of both an existing shape being edited and a draft being drawn — they
 * differ only in how [tag] labels the target.
 */
internal fun vertexHandleRenderables(position: GeoPosition, style: HandleStyle, tag: Placemark.() -> Unit): List<Renderable> {
    val dot = Placemark(
        position.toWwkPosition(),
        handleAttributes(vertexHandleImage(style.ringColor, style.fillColor), style.fillColor.toWwkColor(), style.vertexDiameter),
    ).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isPickEnabled = false
    }
    return listOf(dot, hitTarget(position, style.touchSizeFor(style.vertexDiameter)).apply(tag))
}

/** See [vertexHandleRenderables]; this is the smaller "+" handle on the middle of an edge. */
internal fun midpointHandleRenderables(position: GeoPosition, style: HandleStyle, tag: Placemark.() -> Unit): List<Renderable> {
    val dot = Placemark(
        position.toWwkPosition(),
        handleAttributes(
            midpointHandleImage(style.ringColor, style.fillColor, style.plusColor),
            style.fillColor.toWwkColor().apply { alpha *= MIDPOINT_FALLBACK_OPACITY },
            style.midpointDiameter,
        ),
    ).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
        isPickEnabled = false
    }
    return listOf(dot, hitTarget(position, style.touchSizeFor(style.midpointDiameter)).apply(tag))
}

/**
 * What a finger grabs to move the marker being edited: a transparent, pickable square over the marker
 * itself, [HandleStyle.touchSizeFor] its size (so a small marker still has a finger-sized target).
 * There is no visible dot — the marker's own icon is what is being dragged. [tag] labels it for
 * `PickInputBridge`, like the other handles.
 */
internal fun markerMoveTarget(position: GeoPosition, markerSize: Float, style: HandleStyle, tag: Placemark.() -> Unit): Renderable =
    hitTarget(position, style.touchSizeFor(markerSize)).apply(tag)

/** A fully transparent, image-less square [size] pixels wide: draws nothing, but is pickable. */
private fun hitTarget(position: GeoPosition, size: Float): Placemark =
    Placemark(position.toWwkPosition(), PlacemarkAttributes().apply { imageColor = TRANSPARENT; imageScale = size.toDouble() }).apply {
        altitudeMode = AltitudeMode.CLAMP_TO_GROUND
    }

private val TRANSPARENT = Color(1f, 1f, 1f, 0f)

/** Where there's no handle image, a midpoint's plain square is drawn a little fainter than a vertex's, so the two stay distinguishable. */
private const val MIDPOINT_FALLBACK_OPACITY = 0.75f

/**
 * Every edge's index paired with its midpoint. [isClosed] adds the wraparound edge (last
 * vertex back to the first) for a polygon; a polyline has one fewer edge than vertices.
 *
 * Uses WWK's own [Position.interpolateAlongPath] (with [PathType.GREAT_CIRCLE] — the
 * default `pathType` [PolygonMapper][com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon]/
 * [PolylineMapper][com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolyline] leave every
 * shape at) rather than a plain lat/lon average: WWK renders an edge as a great-circle
 * arc, which is *not* the same curve a naive planar midpoint sits on — over a long edge
 * the two visibly diverge, so a `core`-side pure-math midpoint would float off the
 * actual rendered line. This is why the computation lives here in `wwk-adapter` and not
 * in `core.geometry.GeometryOps`: it only stays correct by depending on the exact same
 * WWK math the shape itself is drawn with.
 */
internal fun edgeMidpoints(vertices: List<GeoPosition>, isClosed: Boolean): List<Pair<Int, GeoPosition>> {
    val edgeCount = if (isClosed) vertices.size else vertices.size - 1
    val scratch = Position()
    return (0 until edgeCount).map { i ->
        val start = vertices[i].toWwkPosition()
        val end = vertices[(i + 1) % vertices.size].toWwkPosition()
        start.interpolateAlongPath(end, PathType.GREAT_CIRCLE, 0.5, scratch)
        i to scratch.toGeoPosition()
    }
}

