package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

internal fun solid(r: Int, g: Int, b: Int, a: Int = 255) = ShapeStyle.SolidColor(ColorRgba(r, g, b, a))

internal fun vertex(id: String, lat: Double, lon: Double) = Vertex(VertexId(id), GeoPosition(lat, lon))

/** A triangle whose corners are [size] degrees from [lat], [lon]; the vertex ids are `<id>-1..3`, whatever its size. */
internal fun triangle(id: String, size: Double, lat: Double = 0.0, lon: Double = 0.0, style: ShapeStyle = solid(255, 0, 0)) = DrawnPolygon(
    EntityId(id), style,
    listOf(vertex("$id-1", lat, lon), vertex("$id-2", lat, lon + size), vertex("$id-3", lat + size, lon)),
)

internal fun marker(id: String, lat: Double, lon: Double, style: ShapeStyle = solid(0, 0, 255)) =
    DrawnMarker(EntityId(id), style, GeoPosition(lat, lon))

internal fun line(id: String, vararg ends: Double, style: ShapeStyle = solid(0, 255, 0)) =
    DrawnPolyline(EntityId(id), style, listOf(vertex("$id-1", ends[0], ends[1]), vertex("$id-2", ends[2], ends[3])))

internal fun polygon(id: String, vararg corners: Vertex, style: ShapeStyle = solid(255, 0, 0)) = DrawnPolygon(EntityId(id), style, corners.toList())
