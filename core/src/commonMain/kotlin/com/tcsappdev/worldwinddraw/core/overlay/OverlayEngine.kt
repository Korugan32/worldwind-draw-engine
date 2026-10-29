package com.tcsappdev.worldwinddraw.core.overlay

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId

/** A shape that cannot be used in an overlay because it is not a valid polygon (it crosses itself, a hole lies outside it, ...). */
class InvalidShapeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * What joins two polygons or cuts one out of another, as `core` needs it. `core` has no geometry engine of its own (an overlay is hard to
 * get right, and a library that does it well already exists): a host that wants merging and cutting gives the [DrawStateMachine][com.tcsappdev.worldwinddraw.core.state.DrawStateMachine]
 * one, for example the one in the `boolean-ops` module, and without one the events that merge and cut do nothing.
 *
 * Both operations answer with the **rings of the shape that results**, as the model keeps them (see [DrawnPolygon]): the outline of each part
 * followed by its holes, all in one list. An empty list means nothing is left. The vertices of the inputs that are still on the outline of the
 * result keep their id; new ones (where the outlines cross) take their ids from `newVertexId`.
 */
interface OverlayEngine {
    /** Why [polygon] cannot be used (a line crosses itself, a hole is outside it, ...), or `null` if it can. */
    fun validate(polygon: DrawnPolygon): String?

    /** The rings of [a] together with [b]. @throws InvalidShapeException if either is not a valid polygon. */
    fun union(a: DrawnPolygon, b: DrawnPolygon, newVertexId: () -> VertexId): List<List<Vertex>>

    /** The rings of [a] with [b] cut out of it. @throws InvalidShapeException if either is not a valid polygon. */
    fun difference(a: DrawnPolygon, b: DrawnPolygon, newVertexId: () -> VertexId): List<List<Vertex>>
}
