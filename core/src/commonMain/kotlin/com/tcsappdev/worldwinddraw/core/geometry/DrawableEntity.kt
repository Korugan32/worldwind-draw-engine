package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.jvm.JvmInline

/** A stable identifier for a single [DrawableEntity] (a whole shape, not one of its vertices). */
@JvmInline
value class EntityId(val id: String)

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
sealed interface DrawableEntity {
    val id: EntityId
    val style: ShapeStyle

    /**
     * Free text about the shape (what it is, a source, a note), for the host to show where it likes, for example in a popup when
     * the shape is tapped. The library only keeps it: nothing is drawn from it. `null` is no description.
     */
    val description: String?

    /**
     * Where the shape lies in the drawing order: a shape with a higher number is drawn over one with a lower number, whatever kind
     * each of them is. Shapes with the same number keep the order of their kinds (polygons below lines below markers) and, within a
     * kind, the order they were added in. A tap on a place where shapes overlap reaches the one that is drawn on top.
     */
    val zIndex: Int
}

/** This shape with another [description] (see [DrawableEntity.description]). */
fun DrawableEntity.withDescription(description: String?): DrawableEntity = when (this) {
    is DrawnPolygon -> copy(description = description)
    is DrawnPolyline -> copy(description = description)
    is DrawnMarker -> copy(description = description)
}

/** This shape with another [zIndex] (see [DrawableEntity.zIndex]). */
fun DrawableEntity.withZIndex(zIndex: Int): DrawableEntity = when (this) {
    is DrawnPolygon -> copy(zIndex = zIndex)
    is DrawnPolyline -> copy(zIndex = zIndex)
    is DrawnMarker -> copy(zIndex = zIndex)
}