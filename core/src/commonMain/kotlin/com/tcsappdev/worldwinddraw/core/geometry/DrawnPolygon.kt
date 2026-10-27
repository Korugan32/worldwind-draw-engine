package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

/**
 * A closed, filled shape: a ring of [vertices], and optionally [extraRings] — more rings of the same shape, each of them either
 * another separate part (the Aegean islands, Thrace beside Anatolia) or a hole in a part (a lake, an enclave). Every ring needs at
 * least 3 vertices, as fewer points cannot describe an enclosed area.
 *
 * There is no flag saying which ring is a part and which a hole: the shape is what the rings enclose under the *even-odd* rule — a
 * point is inside it when it is inside an odd number of rings. A ring inside another one is a hole, a ring inside that hole is a part
 * again, a ring away from the others is a part. This is the same rule WWK fills a `Polygon` with and the one the image fill is cut
 * with, so all three agree. The first ring, [vertices], is the one the shape's visual centre and everything that had a single ring
 * before refer to; the order of the others is only the order they were added in. Rings may not share vertices: every [VertexId]
 * is unique across all of them.
 *
 * Unlike [DrawnPolyline]/[DrawnMarker], a polygon has both an interior AND a border, so it
 * needs two independent styles: [style] (inherited from [DrawableEntity]) fills the
 * interior, [outlineStyle] draws the border. They're separate because either can vary on
 * its own (e.g. same fill color, thicker border when selected).
 *
 * @property outlineStyle the border's color, or `null` for no visible border at all
 * (`isDrawOutline = false` — a deliberate choice, not "unspecified", so a polygon nobody
 * asked to have a border doesn't render WWK's default one).
 * @property outlineWidth the border's thickness in screen pixels, must be greater than 0.
 * Only meaningful when [outlineStyle] is non-null.
 */
data class DrawnPolygon(
    override val id: EntityId,
    override val style: ShapeStyle,
    val vertices: List<Vertex>,
    val outlineStyle: ShapeStyle? = null,
    val outlineWidth: Float = 1f,
    val extraRings: List<List<Vertex>> = emptyList(),
) : DrawableEntity {
    init {
        require(vertices.size >= 3) { "A polygon must have at least 3 vertices" }
        require(extraRings.all { it.size >= 3 }) { "Every ring of a polygon must have at least 3 vertices" }
        require(outlineWidth > 0f) { "outlineWidth must be greater than 0" }
        if (extraRings.isNotEmpty()) {
            val ids = vertices.map { it.id } + extraRings.flatMap { ring -> ring.map { it.id } }
            require(ids.toSet().size == ids.size) { "A vertex id may appear only once in a polygon" }
        }
    }

    /** Every ring: [vertices] first, then [extraRings]. */
    val rings: List<List<Vertex>> = if (extraRings.isEmpty()) listOf(vertices) else listOf(vertices) + extraRings

    /** The index in [rings] of the ring that holds [vertexId], or -1 if no ring does. */
    fun ringIndexOf(vertexId: VertexId): Int = rings.indexOfFirst { ring -> ring.any { it.id == vertexId } }

    /** This polygon with the ring at [index] (in [rings]) replaced by [ring]. */
    fun withRing(index: Int, ring: List<Vertex>): DrawnPolygon {
        require(index in rings.indices) { "no ring $index in a polygon of ${rings.size} rings" }
        return if (index == 0) copy(vertices = ring)
        else copy(extraRings = extraRings.toMutableList().apply { this[index - 1] = ring })
    }

    /** This polygon with every ring replaced by what [transform] makes of it, keeping the order. */
    fun mapRings(transform: (List<Vertex>) -> List<Vertex>): DrawnPolygon =
        copy(vertices = transform(vertices), extraRings = extraRings.map(transform))

    /** This polygon with [ring] added as one more ring (another part, or a hole in one). */
    fun withExtraRing(ring: List<Vertex>): DrawnPolygon = copy(extraRings = extraRings + listOf(ring))

    /** This polygon without the ring at [index]; the next one becomes the first when it is the first that goes. */
    fun withoutRing(index: Int): DrawnPolygon {
        require(rings.size > 1) { "a polygon cannot lose its only ring" }
        require(index in rings.indices) { "no ring $index in a polygon of ${rings.size} rings" }
        val remaining = rings.filterIndexed { i, _ -> i != index }
        return copy(vertices = remaining.first(), extraRings = remaining.drop(1))
    }

    /** This polygon with [ring] inserted at [index] in [rings] (the inverse of [withoutRing]). */
    fun withRingInserted(index: Int, ring: List<Vertex>): DrawnPolygon {
        require(index in 0..rings.size) { "cannot insert a ring at $index in a polygon of ${rings.size} rings" }
        val all = rings.toMutableList().apply { add(index, ring) }
        return copy(vertices = all.first(), extraRings = all.drop(1))
    }
}
