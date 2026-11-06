package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.InsetEngine

/**
 * The part of a plot where a building may stand.
 *
 * @property rings the rings of that part (the outline of each part followed by its holes, like [DrawnPolygon.rings]); empty if nothing is left.
 * @property area its area in square metres (see [Measure.area]); 0 if nothing is left.
 */
public class Buildable(
    public val rings: List<List<Vertex>>,
    public val area: Double,
)

/**
 * What a setback leaves of a plot. A setback (*çekme mesafesi*) is a strip along the boundary where nothing may be built; the part inside it
 * is where a building may stand. The distances are not the same everywhere: they come from the zoning plan of the municipality, so they are
 * always given by the person using it and never assumed here.
 */
public object Setback {
    /**
     * The part of [polygon] that is at least [distanceMeters] metres from every edge, with mitred corners (a hole grows by the same distance).
     * It is empty when the plot is narrower than twice the distance.
     *
     * This is the same distance on every side. A plot whose front, sides and back have different setbacks is not covered.
     *
     * @param engine what takes the strip off (for example `JtsOverlayEngine` of the `boolean-ops` module).
     * @param newVertexId makes the ids of the corners of the result, which are all new.
     * @throws com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException if [polygon] is not a valid polygon.
     * @throws IllegalArgumentException if [distanceMeters] is negative or not a number, or the plot crosses the antimeridian.
     */
    public fun buildable(polygon: DrawnPolygon, distanceMeters: Double, engine: InsetEngine, newVertexId: () -> VertexId): Buildable {
        val rings = engine.inset(polygon, distanceMeters, newVertexId)
        if (rings.isEmpty()) return Buildable(emptyList(), 0.0)
        return Buildable(rings, Measure.area(polygon.copy(vertices = rings.first(), extraRings = rings.drop(1))))
    }
}
