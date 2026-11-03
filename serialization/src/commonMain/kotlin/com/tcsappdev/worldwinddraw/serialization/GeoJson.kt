package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * GeoJSON (RFC 7946), the format most map data is published in, read into and written from the library's shapes.
 *
 * **Reading** ([decode]) takes a FeatureCollection, a single Feature or a bare geometry. A Point becomes a marker, a LineString a polyline, a
 * Polygon a polygon whose rings are the outline and its holes. A MultiPolygon becomes ONE polygon of all its rings (the model keeps parts and
 * holes together under the even-odd rule, see `DrawnPolygon`), and a MultiPoint, MultiLineString or GeometryCollection becomes one shape per
 * member. A feature's `id` is the shape's id (made unique when it isn't), its `description` or else `name` property the shape's description,
 * and the [simplestyle](https://github.com/mapbox/simplestyle-spec) properties give the colours: `fill`, `fill-opacity`, `stroke`,
 * `stroke-width`, `stroke-opacity` and `marker-color`, `marker-size`, each with the spec's own default when missing. Every other
 * property is kept as text in the shape's `properties` (a number as its text, an object or array as its JSON text, `null` skipped), `name`
 * among them, which also stands in for a missing description. Vertex ids are made up (`<shape>-<ring>-<index>`), since GeoJSON has none, and the closing point of a ring is dropped (the model
 * closes a ring itself).
 *
 * Real data is untidy, so reading is forgiving: a feature that cannot be used (no geometry, a point outside the globe, a ring of two points)
 * is left out and says so in [ImportResult.warnings], and is counted by kind in [ImportResult.leftOut], rather than failing the whole file.
 * Only a text that is not GeoJSON at all throws.
 *
 * **Writing** ([encode]) is lossy by nature: GeoJSON has no pictures, no hatching, no labels and no vertex ids, so a shape filled with an
 * image has no `fill`, and the id, description, colours (alpha as `-opacity`), `zIndex` and `properties` (as text properties of the same names, except the
 * keys the library writes itself) are what is kept. A polygon of several parts
 * becomes a MultiPolygon, its outer rings counter-clockwise and its holes clockwise as the RFC asks. For a lossless file use [DrawJson].
 *
 * Coordinates are `[longitude, latitude]`, with the altitude as a third number only when it isn't 0. Nothing is reprojected: GeoJSON is
 * WGS 84 degrees, like the model.
 */
public object GeoJson {
    private val json = Json
    private val prettyJson = Json { prettyPrint = true }

    /**
     * The shapes in [text], and what was left out.
     *
     * @throws DrawFormatException if [text] is not JSON, or JSON that is not GeoJSON (no recognisable `type`).
     */
    public fun decode(text: String): ImportResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw DrawFormatException("not JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw DrawFormatException("not JSON: ${e.message}", e)
        }
        val reader = GeoJsonReader()
        reader.root(root)
        return ImportResult(reader.entities, reader.log.warnings, reader.log.leftOut)
    }

    /** A FeatureCollection of [entities], in their order. [pretty] adds line breaks and indentation; the content is the same. */
    public fun encode(entities: List<DrawableEntity>, pretty: Boolean = false): String {
        val collection = buildJsonObject {
            put("type", "FeatureCollection")
            put("features", buildJsonArray { entities.forEach { add(GeoJsonWriter.feature(it)) } })
        }
        return (if (pretty) prettyJson else json).encodeToString(JsonElement.serializer(), collection)
    }
}

// ---- reading ----

private class GeoJsonReader {
    val entities = ArrayList<DrawableEntity>()
    val log = ReadLog()
    private val ids = UniqueIds()

    // One piece of geometry, already checked and turned into the model's positions.
    private sealed interface Piece {
        class Point(val position: GeoPosition) : Piece
        class Line(val positions: List<GeoPosition>) : Piece
        class Area(val rings: List<List<GeoPosition>>) : Piece
    }

    fun root(element: JsonElement) {
        val obj = element as? JsonObject ?: throw DrawFormatException("not GeoJSON: the top level is not an object")
        when (val type = text(obj["type"])) {
            "FeatureCollection" -> (obj["features"] as? JsonArray)?.forEachIndexed { i, f -> feature(f, "feature ${i + 1}") }
                ?: throw DrawFormatException("not GeoJSON: a FeatureCollection without \"features\"")
            "Feature" -> feature(obj, "feature 1")
            "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon", "GeometryCollection" ->
                shapes(obj, properties = null, baseId = null, label = "geometry")
            else -> throw DrawFormatException("not GeoJSON: type is ${type?.let { "\"$it\"" } ?: "missing"}")
        }
    }

    private fun feature(element: JsonElement, label: String) {
        val obj = element as? JsonObject
        if (obj == null || text(obj["type"]) != "Feature") return log.leave("not a Feature", "$label: not a Feature")
        val geometry = obj["geometry"] as? JsonObject ?: return log.leave("feature without geometry", "$label: no geometry")
        val rawId = (obj["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        shapes(geometry, obj["properties"] as? JsonObject, rawId, label)
    }

    private fun shapes(geometry: JsonObject, properties: JsonObject?, baseId: String?, label: String) {
        val pieces = ArrayList<Piece>()
        collect(geometry, label, pieces)
        if (pieces.isEmpty()) return
        val style = Styles(properties, label, log::warn)
        val bag = propertiesOf(properties)
        val description = text(properties?.get("description")) ?: text(properties?.get("name"))
        val zIndex = (properties?.get("zIndex") as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
        pieces.forEachIndexed { k, piece ->
            val id = ids.make(baseId ?: label.replace(' ', '-'), if (pieces.size > 1) k + 1 else null)
            entities += when (piece) {
                is Piece.Point -> DrawnMarker(EntityId(id), style.marker(), piece.position, style.markerSize(), description = description, zIndex = zIndex, properties = bag)
                is Piece.Line -> DrawnPolyline(EntityId(id), style.line(), vertices(id, 0, piece.positions), style.lineWidth(), description, zIndex, bag)
                is Piece.Area -> DrawnPolygon(
                    id = EntityId(id),
                    style = style.fill(),
                    vertices = vertices(id, 0, piece.rings.first()),
                    outlineStyle = style.outline(),
                    outlineWidth = style.lineWidth(),
                    extraRings = piece.rings.drop(1).mapIndexed { r, ring -> vertices(id, r + 1, ring) },
                    description = description,
                    zIndex = zIndex,
                    properties = bag,
                )
            }
        }
    }

    private fun collect(geometry: JsonObject, label: String, out: MutableList<Piece>) {
        try {
            when (val type = text(geometry["type"])) {
                "Point" -> position(geometry["coordinates"])?.let { out += Piece.Point(it) } ?: log.leave("point without coordinates", "$label: a point without usable coordinates")
                "MultiPoint" -> array(geometry["coordinates"]).forEach { p -> position(p)?.let { out += Piece.Point(it) } ?: log.leave("point without coordinates", "$label: a point without usable coordinates") }
                "LineString" -> line(geometry["coordinates"], label)?.let { out += it }
                "MultiLineString" -> array(geometry["coordinates"]).forEach { l -> line(l, label)?.let { out += it } }
                "Polygon" -> area(array(geometry["coordinates"]), label)?.let { out += it }
                "MultiPolygon" -> area(array(geometry["coordinates"]).flatMap { array(it) }, label)?.let { out += it }
                "GeometryCollection" -> array(geometry["geometries"]).forEach { g ->
                    (g as? JsonObject)?.let { collect(it, label, out) } ?: log.leave("geometry that is not an object", "$label: a geometry that is not an object")
                }
                else -> log.leave("geometry type ${type ?: "missing"}", "$label: geometry type ${type?.let { "\"$it\"" } ?: "missing"} is not supported")
            }
        } catch (e: IllegalArgumentException) {
            log.leave("unusable geometry", "$label: ${e.message}")
        }
    }

    private fun line(coordinates: JsonElement?, label: String): Piece.Line? {
        val positions = array(coordinates).mapNotNull { position(it) }
        if (positions.size < 2) return log.leave("line of fewer than 2 points", "$label: a line of fewer than 2 points").let { null }
        return Piece.Line(positions)
    }

    // All the rings of one shape (a Polygon's, or every ring of a MultiPolygon's parts); rings that cannot be used are left out.
    private fun area(rings: List<JsonElement?>, label: String): Piece.Area? {
        val usable = rings.mapNotNull { ring ->
            val positions = withoutClosingPoint(array(ring).mapNotNull { position(it) })
            if (positions.size < 3) null.also { log.leave("ring of fewer than 3 points", "$label: a ring of fewer than 3 points is left out") } else positions
        }
        return if (usable.isEmpty()) null else Piece.Area(usable)
    }

    private fun position(element: JsonElement?): GeoPosition? {
        val numbers = array(element).map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }
        if (numbers.size < 2) return null
        return GeoPosition(latitude = numbers[1], longitude = numbers[0], altitude = numbers.getOrElse(2) { 0.0 })
    }

    private fun array(element: JsonElement?): List<JsonElement> = (element as? JsonArray) ?: emptyList()

}

// What simplestyle and the library itself take out of `properties` (so it isn't kept twice): the look and the drawing order. The rest
// is kept as the shape's properties, `name` included (it also stands in for a missing description), and a `description` that was used.
private val RESERVED = setOf("zIndex", "fill", "fill-opacity", "stroke", "stroke-width", "stroke-opacity", "marker-color", "marker-size")

private fun propertiesOf(properties: JsonObject?): Map<String, String> {
    if (properties == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for ((key, value) in properties) {
        if (key in RESERVED || key == "description" && text(value) != null) continue
        when (value) {
            is JsonNull -> {}
            is JsonPrimitive -> out[key] = value.content
            else -> out[key] = value.toString() // an object or an array is kept as its JSON text
        }
    }
    return out
}

private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotEmpty() }

// The simplestyle properties of one feature, with the spec's defaults. A value that isn't usable is a warning and the default.
private class Styles(private val properties: JsonObject?, private val label: String, private val warn: (String) -> Unit) {
    private fun colour(key: String, default: ColorRgba): ColorRgba {
        val value = text(properties?.get(key)) ?: return default
        return parseShortHex(value) ?: default.also { warn("$label: $key \"$value\" is not a colour (#RGB or #RRGGBB), the default is used") }
    }

    private fun number(key: String, default: Double): Double {
        val value = properties?.get(key) as? JsonPrimitive ?: return default
        return value.doubleOrNull ?: default.also { warn("$label: $key is not a number, the default is used") }
    }

    private fun withOpacity(color: ColorRgba, opacityKey: String, default: Double): ColorRgba {
        val opacity = number(opacityKey, default).coerceIn(0.0, 1.0)
        return color.copy(alpha = (opacity * 255).roundToInt())
    }

    fun fill() = ShapeStyle.SolidColor(withOpacity(colour("fill", GREY), "fill-opacity", 0.6))
    fun outline() = ShapeStyle.SolidColor(withOpacity(colour("stroke", GREY), "stroke-opacity", 1.0))
    fun line() = outline()
    fun lineWidth() = number("stroke-width", 2.0).toFloat().takeIf { it > 0f } ?: 2f
    fun marker() = ShapeStyle.SolidColor(colour("marker-color", ColorRgba(0x7e, 0x7e, 0x7e)))
    fun markerSize() = when (text(properties?.get("marker-size"))) {
        "small" -> 12f
        "large" -> 24f
        else -> 16f
    }

    private companion object {
        val GREY = ColorRgba(0x55, 0x55, 0x55)
    }
}

// #RGB or #RRGGBB (what simplestyle files contain); no alpha, that is what the -opacity properties are for.
private fun parseShortHex(text: String): ColorRgba? {
    if (!text.startsWith("#") || text.length != 4 && text.length != 7) return null
    val digits = text.drop(1)
    if (!digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val full = if (digits.length == 3) digits.map { "$it$it" } else digits.chunked(2)
    val (r, g, b) = full.map { it.toInt(16) }
    return ColorRgba(r, g, b)
}

// ---- writing ----

private object GeoJsonWriter {
    fun feature(entity: DrawableEntity): JsonObject = buildJsonObject {
        put("type", "Feature")
        put("id", entity.id.id)
        put("properties", properties(entity))
        put("geometry", geometry(entity))
    }

    private fun properties(entity: DrawableEntity) = buildJsonObject {
        // the shape's own data first, under the names that are free (a property called like one of the keys written below would be overwritten)
        entity.properties.forEach { (key, value) -> if (key !in RESERVED && key != "description") put(key, value) }
        entity.description?.let { put("description", it) }
        if (entity.zIndex != 0) put("zIndex", entity.zIndex)
        when (entity) {
            is DrawnPolygon -> {
                solid(entity.style)?.let { put("fill", hex(it.copy(alpha = 255))); opacity("fill-opacity", it) }
                solid(entity.outlineStyle)?.let { put("stroke", hex(it.copy(alpha = 255))); opacity("stroke-opacity", it) }
                put("stroke-width", entity.outlineWidth)
            }
            is DrawnPolyline -> {
                solid(entity.style)?.let { put("stroke", hex(it.copy(alpha = 255))); opacity("stroke-opacity", it) }
                put("stroke-width", entity.lineWidth)
            }
            is DrawnMarker -> solid(entity.style)?.let { put("marker-color", hex(it.copy(alpha = 255))) }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.opacity(key: String, color: ColorRgba) {
        if (color.alpha != 255) put(key, (color.alpha / 255.0 * 1000).roundToInt() / 1000.0)
    }

    private fun solid(style: ShapeStyle?): ColorRgba? = (style as? ShapeStyle.SolidColor)?.color

    private fun geometry(entity: DrawableEntity): JsonObject = buildJsonObject {
        when (entity) {
            is DrawnMarker -> {
                put("type", "Point")
                put("coordinates", coordinates(entity.position))
            }
            is DrawnPolyline -> {
                put("type", "LineString")
                put("coordinates", buildJsonArray { entity.vertices.forEach { add(coordinates(it.position)) } })
            }
            is DrawnPolygon -> {
                val parts = PolygonParts.split(entity.rings.map { ring -> ring.map { it.position } })
                if (parts.size == 1) {
                    put("type", "Polygon")
                    put("coordinates", part(parts.single()))
                } else {
                    put("type", "MultiPolygon")
                    put("coordinates", buildJsonArray { parts.forEach { add(part(it)) } })
                }
            }
        }
    }

    // One part's rings: the outline counter-clockwise first, the holes clockwise after it, each closed by repeating its first point.
    private fun part(rings: List<List<GeoPosition>>) = buildJsonArray {
        orientedPart(rings).forEach { wanted ->
            add(buildJsonArray {
                wanted.forEach { add(coordinates(it)) }
                add(coordinates(wanted.first()))
            })
        }
    }

    private fun coordinates(p: GeoPosition) = buildJsonArray {
        add(JsonPrimitive(p.longitude))
        add(JsonPrimitive(p.latitude))
        if (p.altitude != 0.0) add(JsonPrimitive(p.altitude))
    }
}
