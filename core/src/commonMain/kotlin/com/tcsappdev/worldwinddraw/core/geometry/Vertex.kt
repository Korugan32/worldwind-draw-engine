package com.tcsappdev.worldwinddraw.core.geometry

import kotlin.jvm.JvmInline

/**
 * A stable identifier for a single [Vertex], independent of that vertex's current
 * [GeoPosition]. Two [VertexId]s are only equal if they wrap the same underlying string.
 */
@JvmInline
public value class VertexId(public val id: String)

/**
 * A single, editable corner point of a [DrawnPolygon] or [DrawnPolyline].
 *
 * The [id] stays the same as the user drags this vertex to a new [position] — that
 * stable identity is what lets editing/undo logic refer to "this vertex" independently
 * of where it currently sits on the globe.
 *
 * @property metadata optional, free-form tags (e.g. a label or a snapping hint).
 */
public data class Vertex(
    val id: VertexId,
    val position: GeoPosition,
    val metadata: Map<String, String> = emptyMap()
)
