package com.tcsappdev.worldwinddraw.booleanops

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import kotlin.random.Random

/**
 * Union, difference and intersection of two polygons ([DrawnPolygon]s): what "merge" and "cut" are made of.
 *
 * Each takes the shapes and answers with the **rings of the shape that results**, as the model keeps them (see `DrawnPolygon`): the outline
 * of every part, each followed by its holes, all in one list, so a result of two separate countries is the rings of both. An empty list
 * means nothing is left (a difference of a shape from itself, an intersection of two shapes that don't touch). The caller builds the shape,
 * with the id and look it wants (the first ring is the outline `DrawnPolygon.vertices`, the rest its `extraRings`).
 *
 * **Vertices.** A vertex of the result that was a vertex of `a` or `b` keeps its id, its metadata and its exact position (when both have a
 * vertex there, the one of `a` wins); one that is new, where the outlines cross, gets an id from `newVertexId`. No two vertices of a result
 * share an id, even if the two inputs reuse one. Altitude is not part of the overlay: new vertices are at altitude 0.
 *
 * **The grid.** Exact geometry does not join two neighbours whose shared border is written a hair's breadth apart (1e-12 degrees is enough
 * to leave them two shapes, which is what two sources of the same border give). So the overlay is done on a grid of `gridSize` degrees:
 * coordinates are rounded to it, and a gap or overlap smaller than a cell disappears. The default is a cell of 1e-7 degrees, about a
 * centimetre. `0.0` is no grid: exact, and then a hair's breadth is a gap. The result's new vertices lie on the grid; the ones kept from
 * the inputs keep their own position.
 *
 * **Not a sphere.** Longitude and latitude are used as a flat plane, like the rest of the library (`PolygonCenter`, `PolygonParts`): fine
 * for countries, small errors on long edges, and a shape that crosses the antimeridian is not handled.
 *
 * The overlay itself is JTS's (OverlayNG, in its Kotlin Multiplatform port), see docs/PLAN.md (Faz 12b).
 */
public object PolygonOverlay {
    /** One cell of the default grid, in degrees. */
    public const val DEFAULT_GRID_SIZE: Double = 1e-7

    /** The rings of [a] together with [b]. @throws com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException if either is not a valid polygon. */
    public fun union(
        a: DrawnPolygon,
        b: DrawnPolygon,
        gridSize: Double = DEFAULT_GRID_SIZE,
        newVertexId: () -> VertexId = ::randomVertexId,
    ): List<List<Vertex>> = JtsOverlay.run(JtsOverlay.Operation.Union, a, b, gridSize, newVertexId)

    /** The rings of [a] with [b] cut out of it. @throws com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException if either is not a valid polygon. */
    public fun difference(
        a: DrawnPolygon,
        b: DrawnPolygon,
        gridSize: Double = DEFAULT_GRID_SIZE,
        newVertexId: () -> VertexId = ::randomVertexId,
    ): List<List<Vertex>> = JtsOverlay.run(JtsOverlay.Operation.Difference, a, b, gridSize, newVertexId)

    /** The rings of what [a] and [b] have in common. @throws com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException if either is not a valid polygon. */
    public fun intersection(
        a: DrawnPolygon,
        b: DrawnPolygon,
        gridSize: Double = DEFAULT_GRID_SIZE,
        newVertexId: () -> VertexId = ::randomVertexId,
    ): List<List<Vertex>> = JtsOverlay.run(JtsOverlay.Operation.Intersection, a, b, gridSize, newVertexId)

    /** Why [polygon] cannot be used in an overlay (a line crosses itself, a hole is outside, a ring has too few points...), or `null` if it can. */
    public fun validate(polygon: DrawnPolygon): String? = JtsOverlay.invalidReason(polygon)

    private fun randomVertexId() = VertexId("v" + Random.nextLong().toString(36))
}
