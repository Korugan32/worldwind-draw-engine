package com.tcsappdev.worldwinddraw.booleanops

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.operation.valid.IsValidOp

// The bridge to JTS: a DrawnPolygon to a JTS geometry (x = longitude, y = latitude), the overlay, and the result back to rings with vertex ids.

internal object JtsOverlay {
    enum class Operation(val code: Int) {
        Union(OverlayNG.UNION),
        Difference(OverlayNG.DIFFERENCE),
        Intersection(OverlayNG.INTERSECTION),
    }

    private val factory = GeometryFactory()

    fun run(operation: Operation, a: DrawnPolygon, b: DrawnPolygon, gridSize: Double, newVertexId: () -> VertexId): List<List<Vertex>> {
        require(gridSize >= 0.0 && gridSize.isFinite()) { "gridSize must be 0 or more, was $gridSize" }
        val first = geometryOf(a)
        val second = geometryOf(b)
        val result = if (gridSize == 0.0) {
            OverlayNGRobust.overlay(first, second, operation.code)
        } else {
            OverlayNG.overlay(first, second, operation.code, PrecisionModel(1.0 / gridSize))
        }
        return ringsOf(result, listOf(a, b), gridSize, newVertexId)
    }

    fun invalidReason(polygon: DrawnPolygon): String? {
        val geometry = try {
            build(polygon)
        } catch (e: IllegalArgumentException) {
            return e.message ?: "not a polygon"
        }
        return IsValidOp(geometry).getValidationError()?.getMessage()
    }

    // ---- DrawnPolygon -> JTS ----

    private fun geometryOf(polygon: DrawnPolygon): Geometry {
        val geometry = try {
            build(polygon)
        } catch (e: IllegalArgumentException) {
            throw InvalidShapeException("shape \"${polygon.id.id}\" is not a valid polygon: ${e.message}", e)
        }
        IsValidOp(geometry).getValidationError()?.let { throw InvalidShapeException("shape \"${polygon.id.id}\" is not a valid polygon: ${it.getMessage()}") }
        return geometry
    }

    // The rings of a DrawnPolygon are parts and holes together (even-odd); PolygonParts tells which is which: a part is an outline with its holes.
    private fun build(polygon: DrawnPolygon): Geometry {
        val parts = PolygonParts.split(polygon.rings.map { ring -> ring.map { it.position } })
        val polygons = parts.map { part ->
            val shell = ringOf(part.first())
            val holes = part.drop(1).map { ringOf(it) }.toTypedArray()
            factory.createPolygon(shell, holes)
        }
        require(polygons.isNotEmpty()) { "no ring with 3 points" }
        return if (polygons.size == 1) polygons.single() else factory.createMultiPolygon(polygons.toTypedArray())
    }

    private fun ringOf(positions: List<GeoPosition>): LinearRing {
        val coordinates = positions.map { Coordinate(it.longitude, it.latitude) }
        return factory.createLinearRing((coordinates + coordinates.first()).toTypedArray())
    }

    // ---- JTS -> rings ----

    private fun ringsOf(result: Geometry, inputs: List<DrawnPolygon>, gridSize: Double, newVertexId: () -> VertexId): List<List<Vertex>> {
        val polygons = ArrayList<Polygon>()
        collectPolygons(result, polygons)
        if (polygons.isEmpty()) return emptyList()

        // The inputs' vertices by where the overlay will have them: on the grid, so a vertex that was moved to its cell is still found. The
        // first one at a place wins, and [a] comes first.
        val precision = if (gridSize == 0.0) null else PrecisionModel(1.0 / gridSize)
        fun key(longitude: Double, latitude: Double) =
            if (precision == null) longitude to latitude else precision.makePrecise(longitude) to precision.makePrecise(latitude)
        val known = HashMap<Pair<Double, Double>, Vertex>()
        for (input in inputs) for (ring in input.rings) for (vertex in ring) {
            known.getOrPut(key(vertex.position.longitude, vertex.position.latitude)) { vertex }
        }
        val used = HashSet<String>()
        inputs.forEach { input -> input.rings.forEach { ring -> ring.forEach { used += it.id.id } } }
        val taken = HashSet<String>()

        fun fresh(): VertexId {
            while (true) {
                val id = newVertexId()
                if (id.id !in used && taken.add(id.id)) return id
            }
        }

        fun vertexAt(coordinate: Coordinate): Vertex {
            val original = known[coordinate.x to coordinate.y]
            // A kept vertex is the original (id, metadata and exact position), unless its id was already given out in this result.
            if (original != null && taken.add(original.id.id)) return original
            return Vertex(fresh(), GeoPosition(latitude = coordinate.y, longitude = coordinate.x))
        }

        val rings = ArrayList<List<Vertex>>()
        for (polygon in polygons) {
            rings += ringVertices(polygon.getExteriorRing().getCoordinates(), ::vertexAt)
            for (i in 0 until polygon.getNumInteriorRing()) rings += ringVertices(polygon.getInteriorRingN(i).getCoordinates(), ::vertexAt)
        }
        return rings
    }

    // The ring without its closing point, which repeats the first.
    private fun ringVertices(coordinates: Array<Coordinate>, vertexAt: (Coordinate) -> Vertex): List<Vertex> =
        coordinates.dropLast(1).map(vertexAt)

    // An overlay of polygons can also leave lines and points (two shapes that only touch); those are not shapes of ours.
    private fun collectPolygons(geometry: Geometry, out: MutableList<Polygon>) {
        when {
            geometry.isEmpty() -> {}
            geometry is Polygon -> out += geometry
            // a MultiPolygon or a mixed collection: look inside (only those, a line's "member" is itself)
            geometry is GeometryCollection -> for (i in 0 until geometry.getNumGeometries()) collectPolygons(geometry.getGeometryN(i), out)
        }
    }
}
