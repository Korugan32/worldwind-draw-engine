package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.jvm.JvmInline

/** A stable identifier for a single [DrawableEntity] (a whole shape, not one of its vertices). */
@JvmInline
public value class EntityId(public val id: String)

/**
 * The common contract every shape a user can draw must satisfy: it has a stable [id]
 * and a visual [style], and may carry a [description] and a [zIndex]. Code that manages
 * entities generically (repository, undo/redo history, rendering) depends only on this
 * contract, never on a specific shape type.
 *
 * Sealed so the compiler can guarantee every `when` over a [DrawableEntity] handles all
 * known shape kinds (see [DrawnPolygon], [DrawnPolyline], [DrawnMarker]) — adding a new
 * shape type later will surface every place that needs updating as a compile error.
 */
public sealed interface DrawableEntity {
    /** The id that names this shape, stable for as long as it exists. */
    public val id: EntityId
    /** How the shape looks (see [ShapeStyle] for what it means for each kind of shape). */
    public val style: ShapeStyle

    /**
     * Free text about the shape (what it is, a source, a note), for the host to show where it likes, for example in a popup when
     * the shape is tapped. The library only keeps it: nothing is drawn from it. `null` is no description.
     */
    public val description: String?

    /**
     * Where the shape lies in the drawing order: a shape with a higher number is drawn over one with a lower number, whatever kind
     * each of them is. Shapes with the same number keep the order of their kinds (polygons below lines below markers) and, within a
     * kind, the order they were added in. A tap on a place where shapes overlap reaches the one that is drawn on top.
     */
    public val zIndex: Int

    /**
     * Data attached to the shape, as named values: what a GeoJSON feature's `properties` or a KML placemark's `ExtendedData` hold (a
     * population, a country code, a year, a source). The library only keeps it, it is not drawn, and it is carried through the file
     * formats: nothing in it means anything to the engine, so a host may put what it needs there. Values are text; a number or a flag
     * read from a file comes in as its text (`5000000`, `true`) and goes back out as a text.
     * Empty when there is none.
     */
    public val properties: Map<String, String>
}

/** This shape with another [description] (see [DrawableEntity.description]). */
public fun DrawableEntity.withDescription(description: String?): DrawableEntity = when (this) {
    is DrawnPolygon -> copy(description = description)
    is DrawnPolyline -> copy(description = description)
    is DrawnMarker -> copy(description = description)
}

/** This shape with other [properties] (see [DrawableEntity.properties]). */
public fun DrawableEntity.withProperties(properties: Map<String, String>): DrawableEntity = when (this) {
    is DrawnPolygon -> copy(properties = properties)
    is DrawnPolyline -> copy(properties = properties)
    is DrawnMarker -> copy(properties = properties)
}

/** This shape with another [zIndex] (see [DrawableEntity.zIndex]). */
public fun DrawableEntity.withZIndex(zIndex: Int): DrawableEntity = when (this) {
    is DrawnPolygon -> copy(zIndex = zIndex)
    is DrawnPolyline -> copy(zIndex = zIndex)
    is DrawnMarker -> copy(zIndex = zIndex)
}