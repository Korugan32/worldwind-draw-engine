package com.tcsappdev.worldwinddraw.core.overlay

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId

/**
 * What takes a strip of a given width off the inside of a polygon, all the way round: the land that is left when a setback has to be kept
 * free along every edge (where a building may stand). Like [OverlayEngine] it is something `core` needs and has no engine for; the
 * `boolean-ops` module has one (`JtsOverlayEngine` is both).
 */
public interface InsetEngine {
    /**
     * The rings of what is left of [polygon] when everything within [distanceMeters] of an edge is taken away, as the model keeps them (see
     * [OverlayEngine]): the outline of each part followed by its holes. The distance is in metres on the ground, the same in every direction;
     * an outline is moved in by it, and a hole is moved outwards (so it grows), the corners staying sharp (a mitre, as a setback line is drawn).
     * An empty list means nothing is left (the plot is narrower than twice the distance). Every corner of the result is a new one: they take
     * their ids from [newVertexId].
     *
     * @throws InvalidShapeException if [polygon] is not a valid polygon.
     * @throws IllegalArgumentException if [distanceMeters] is negative or not a number, or the plot crosses the antimeridian (longitude 180).
     */
    public fun inset(polygon: DrawnPolygon, distanceMeters: Double, newVertexId: () -> VertexId): List<List<Vertex>>
}
