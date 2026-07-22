package com.tcsappdev.worldwinddraw.core.geometry

import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.jvm.JvmInline

/** A stable identifier for a single [DrawableEntity] (a whole shape, not one of its vertices). */
@JvmInline
value class EntityId(val id: String)

/**
 * The common contract every shape a user can draw must satisfy: it has a stable [id]
 * and a visual [style]. Code that manages entities generically (repository, undo/redo
 * history, rendering) depends only on this contract, never on a specific shape type.
 *
 * Sealed so the compiler can guarantee every `when` over a [DrawableEntity] handles all
 * known shape kinds (see [DrawnPolygon], [DrawnPolyline], [DrawnMarker]) — adding a new
 * shape type later will surface every place that needs updating as a compile error.
 */
sealed interface DrawableEntity {
    val id: EntityId
    val style: ShapeStyle
}