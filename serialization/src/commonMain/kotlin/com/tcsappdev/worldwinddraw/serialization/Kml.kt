package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.roundToInt

/**
 * KML 2.2, the format of Google Earth, read into and written from the library's shapes.
 *
 * **Reading** ([decode]) finds every `Placemark` however deeply it sits in `Document`s and `Folder`s. A `Point` becomes a marker, a
 * `LineString` a polyline, a `Polygon` a polygon (`outerBoundaryIs` and every `innerBoundaryIs` are its rings), and a `MultiGeometry` one shape
 * per point or line in it and ONE polygon of all its polygons' rings (the model keeps parts and holes together under the even-odd rule, see
 * `DrawnPolygon`). The placemark's `id` attribute is the shape's id (made unique when it isn't), its `description` the shape's description,
 * and for a marker its `name` is the label (that is what Google Earth shows beside a point); a polygon or a line has no label, so its `name`
 * stands in for a missing description. The name is also kept as the property `name`.
 *
 * The shape's [properties][com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity.properties] are its `ExtendedData` (`Data` and the
 * `SimpleData` of a `SchemaData`; an entry called `zIndex` is the drawing order instead), its `name`, the time it holds for (`TimeSpan` as
 * `timeBegin` and `timeEnd`, `TimeStamp` as `timeStamp`, the text kept as KML has it) and the names of the `Folder`s it lies in, outermost
 * first (`folder`, joined with " / ").
 *
 * The look comes from `Style` (inline, or shared and found by `styleUrl="#id"`, also through a `StyleMap`'s `normal` pair): `PolyStyle` is the
 * fill (`<fill>0</fill>` no fill), `LineStyle` the outline of a polygon and the colour and width of a line (`<outline>0</outline>` no
 * outline), `IconStyle` the colour, `scale` (16 px at 1) and, for an `http`/`https` `Icon`, the picture of a marker, `LabelStyle` the colour and
 * `scale` (14 pt at 1) of its label. KML colours are written
 * `aabbggrr`, alpha first and blue before red, and each of these has KML's own default when missing (white, a width of 1).
 *
 * What is not read, with a line in [ImportResult.warnings] and counted by kind in [ImportResult.leftOut]: `GroundOverlay`, `ScreenOverlay`, `NetworkLink`, `Tour`,
 * `gx:Track` and the like (the model has nothing to put them in), icons that aren't a web address (relative paths and `.kmz` contents),
 * styles in other files, and the region and camera elements, which are ignored silently. A `.kmz` is a zip of a KML: unzip it first.
 *
 * **Writing** ([encode]) is lossy like [GeoJson.encode]: no pictures (except a marker's web-address picture, written as its `Icon`), no hatching, no
 * vertex ids. Kept are the id, the description, the colours (as KML's `aabbggrr`), widths, a marker's label (as its `name`, with its colour and
 * size as `LabelStyle`) and size (as `scale`), `zIndex` and the properties (the `name`, `timeBegin`/`timeEnd`/`timeStamp` as the time
 * elements, the rest as `ExtendedData`; the folders are not rebuilt, `folder` is just a property). A polygon of several parts becomes a `MultiGeometry`, each part's outline counter-clockwise and its holes
 * clockwise. For a lossless file use [DrawJson].
 *
 * Coordinates are `longitude,latitude[,altitude]`, as in GeoJSON.
 */
object Kml {
    /**
     * The shapes in [text], and what was left out.
     *
     * @throws DrawFormatException if [text] is not well-formed XML, or XML whose root is not `kml` (a bare `Document`, `Folder` or `Placemark`
     * is accepted as well).
     */
    fun decode(text: String): ImportResult {
        val root = Xml.parse(text)
        if (root.name !in setOf("kml", "Document", "Folder", "Placemark")) throw DrawFormatException("not KML: the root element is <${root.name}>")
        val reader = KmlReader(root)
        reader.read()
        return ImportResult(reader.entities, reader.log.warnings, reader.log.leftOut)
    }

    /** A KML document of [entities], in their order. [pretty] adds line breaks and indentation; the content is the same. */
    fun encode(entities: List<DrawableEntity>, pretty: Boolean = false): String = KmlWriter(pretty).document(entities)
}

// ---- reading ----

private class KmlStyle(
    val fill: ColorRgba? = null,
    val showFill: Boolean = true,
    val showOutline: Boolean = true,
    val line: ColorRgba? = null,
    val lineWidth: Float? = null,
    val icon: ColorRgba? = null,
    val iconScale: Float? = null,
    val iconHref: String? = null,
    val labelColor: ColorRgba? = null,
    val labelScale: Float? = null,
)

private class KmlReader(private val root: XmlNode) {
    val entities = ArrayList<DrawableEntity>()
    val log = ReadLog()
    private val ids = UniqueIds()
    private val styles = HashMap<String, KmlStyle>()
    private val styleMaps = HashMap<String, String>() // StyleMap id -> the "normal" pair's styleUrl or inline style key
    private var placemarks = 0

    private sealed interface Piece {
        class Point(val position: GeoPosition) : Piece
        class Line(val positions: List<GeoPosition>) : Piece
        class Area(val rings: List<List<GeoPosition>>) : Piece
    }

    fun read() {
        collectStyles(root)
        walk(root, emptyList())
    }

    // Shared styles may come after the placemarks that use them, so they are all found first.
    private fun collectStyles(node: XmlNode) {
        for (child in node.children) {
            when (child.name) {
                "Style" -> child.attributes["id"]?.let { styles[it] = style(child) }
                "StyleMap" -> child.attributes["id"]?.let { id ->
                    val normal = child.children("Pair").firstOrNull { it.childText("key") == "normal" }
                    val url = normal?.childText("styleUrl")
                    val inline = normal?.child("Style")
                    if (inline != null) styles[id] = style(inline) else if (url != null) styleMaps[id] = url.removePrefix("#")
                }
            }
            collectStyles(child)
        }
    }

    // [folders] are the names of the folders around [node], outermost first.
    private fun walk(node: XmlNode, folders: List<String>) {
        if (node.name == "Placemark") return placemark(node, folders)
        val inside = if (node.name == "Folder") folders + listOfNotNull(node.childText("name")) else folders
        for (child in node.children) {
            when (child.name) {
                "GroundOverlay", "ScreenOverlay", "PhotoOverlay", "NetworkLink", "Tour" ->
                    log.leave(child.name, "<${child.name}> is not supported and is left out")
                else -> walk(child, inside)
            }
        }
    }

    private fun placemark(node: XmlNode, folders: List<String>) {
        placemarks++
        val label = "placemark $placemarks"
        val pieces = ArrayList<Piece>()
        val polygonRings = ArrayList<List<GeoPosition>>()
        var areaAt = -1
        for (child in node.children) {
            if (child.name in GEOMETRY) {
                try {
                    collect(child, label, pieces, polygonRings) { if (areaAt < 0) areaAt = pieces.size }
                } catch (e: IllegalArgumentException) {
                    log.leave("unusable geometry", "$label: ${e.message}")
                }
            }
        }
        if (polygonRings.isNotEmpty()) pieces.add(minOf(areaAt, pieces.size).coerceAtLeast(0), Piece.Area(polygonRings))
        if (pieces.isEmpty()) {
            if (node.children.none { it.name in GEOMETRY }) log.leave("placemark without geometry", "$label: no geometry")
            return
        }
        val style = styleOf(node, label)
        val name = node.childText("name")
        val description = node.childText("description") ?: name
        val data = extendedData(node)
        val zIndex = data["zIndex"]?.toIntOrNull() ?: 0
        val bag = properties(node, data, folders)
        val base = node.attributes["id"] ?: label.replace(' ', '-')
        pieces.forEachIndexed { k, piece ->
            val id = ids.make(base, if (pieces.size > 1) k + 1 else null)
            entities += when (piece) {
                is Piece.Point -> DrawnMarker(
                    id = EntityId(id),
                    style = markerStyle(style, label),
                    position = piece.position,
                    size = ((style.iconScale ?: 1f) * 16f).coerceIn(1f, 512f),
                    label = name?.let { markerLabel(it, style) },
                    description = node.childText("description"),
                    zIndex = zIndex,
                    properties = bag,
                )
                is Piece.Line -> DrawnPolyline(
                    EntityId(id), ShapeStyle.SolidColor(style.line ?: WHITE), vertices(id, 0, piece.positions),
                    style.lineWidth ?: 1f, description, zIndex, bag,
                )
                is Piece.Area -> DrawnPolygon(
                    id = EntityId(id),
                    style = ShapeStyle.SolidColor(if (style.showFill) style.fill ?: WHITE else CLEAR),
                    vertices = vertices(id, 0, piece.rings.first()),
                    outlineStyle = ShapeStyle.SolidColor(if (style.showOutline) style.line ?: WHITE else CLEAR),
                    outlineWidth = style.lineWidth ?: 1f,
                    extraRings = piece.rings.drop(1).mapIndexed { r, ring -> vertices(id, r + 1, ring) },
                    description = description,
                    zIndex = zIndex,
                    properties = bag,
                )
            }
        }
    }

    // The named values of `ExtendedData`: `Data` (name + value) and the `SimpleData` of a `SchemaData` (name + text).
    private fun extendedData(placemark: XmlNode): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val extended = placemark.child("ExtendedData") ?: return out
        for (data in extended.children("Data")) {
            val key = data.attributes["name"] ?: continue
            out[key] = data.child("value")?.text().orEmpty()
        }
        for (schema in extended.children("SchemaData")) {
            for (simple in schema.children("SimpleData")) simple.attributes["name"]?.let { out[it] = simple.text() }
        }
        return out
    }

    // The shape's properties: its extended data (without the `zIndex` that is the drawing order), the name, the time it holds for
    // (as `timeBegin` and `timeEnd`, or `timeStamp`, in the text KML has them in) and the folders it lies in (`folder`, joined with " / ").
    private fun properties(node: XmlNode, data: Map<String, String>, folders: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in data) if (key != "zIndex") out[key] = value
        node.childText("name")?.let { out["name"] = it }
        node.child("TimeSpan")?.let { span ->
            span.childText("begin")?.let { out["timeBegin"] = it }
            span.childText("end")?.let { out["timeEnd"] = it }
        }
        node.child("TimeStamp")?.childText("when")?.let { out["timeStamp"] = it }
        if (folders.isNotEmpty()) out["folder"] = folders.joinToString(" / ")
        return out
    }

    // KML's label is the placemark's name; its colour and scale (14 pt at 1) are in LabelStyle.
    private fun markerLabel(text: String, style: KmlStyle) =
        MarkerLabel(text, color = style.labelColor ?: WHITE, size = ((style.labelScale ?: 1f) * 14f).roundToInt().coerceIn(6, 96))

    private fun collect(node: XmlNode, label: String, out: MutableList<Piece>, polygonRings: MutableList<List<GeoPosition>>, onPolygon: () -> Unit) {
        when (node.name) {
            "Point" -> positions(node.child("coordinates"), label).singleOrNull()?.let { out += Piece.Point(it) }
                ?: run { log.leave("point without coordinates", "$label: a point without usable coordinates") }
            "LineString", "LinearRing" -> {
                val line = positions(node.child("coordinates"), label)
                if (line.size >= 2) out += Piece.Line(line) else log.leave("line of fewer than 2 points", "$label: a line of fewer than 2 points")
            }
            "Polygon" -> {
                onPolygon()
                val rings = listOfNotNull(node.child("outerBoundaryIs")) + node.children("innerBoundaryIs")
                for (boundary in rings) {
                    val ring = withoutClosingPoint(positions(boundary.child("LinearRing")?.child("coordinates"), label))
                    if (ring.size >= 3) polygonRings += ring else log.leave("ring of fewer than 3 points", "$label: a ring of fewer than 3 points is left out")
                }
            }
            "MultiGeometry" -> node.children.filter { it.name in GEOMETRY }.forEach { collect(it, label, out, polygonRings, onPolygon) }
        }
    }

    // "lon,lat[,alt] lon,lat[,alt] ..." separated by any whitespace. A tuple that isn't numbers is dropped (and said).
    private fun positions(node: XmlNode?, label: String): List<GeoPosition> {
        val text = node?.text().orEmpty()
        return text.split(' ', '\n', '\r', '\t').filter { it.isNotEmpty() }.mapNotNull { tuple ->
            val numbers = tuple.split(',').map { it.toDoubleOrNull() }
            if (numbers.size < 2 || numbers.any { it == null }) {
                log.leave("bad coordinate", "$label: \"$tuple\" is not a coordinate (lon,lat[,alt])")
                null
            } else {
                GeoPosition(latitude = numbers[1]!!, longitude = numbers[0]!!, altitude = numbers.getOrNull(2) ?: 0.0)
            }
        }
    }

    private fun styleOf(placemark: XmlNode, label: String): KmlStyle {
        placemark.child("Style")?.let { return style(it) }
        var url = placemark.childText("styleUrl") ?: return KmlStyle()
        if (!url.startsWith("#")) {
            log.warn("$label: the style \"$url\" is in another file and is not read")
            return KmlStyle()
        }
        url = url.removePrefix("#")
        url = styleMaps[url] ?: url
        return styles[url] ?: KmlStyle().also { log.warn("$label: the style \"#$url\" is not defined") }
    }

    private fun style(node: XmlNode): KmlStyle {
        val poly = node.child("PolyStyle")
        val line = node.child("LineStyle")
        val icon = node.child("IconStyle")
        val labelStyle = node.child("LabelStyle")
        return KmlStyle(
            fill = poly?.childText("color")?.let(::kmlColor),
            showFill = poly?.childText("fill") != "0",
            showOutline = poly?.childText("outline") != "0",
            line = line?.childText("color")?.let(::kmlColor),
            lineWidth = line?.childText("width")?.toFloatOrNull()?.takeIf { it > 0f },
            icon = icon?.childText("color")?.let(::kmlColor),
            iconScale = icon?.childText("scale")?.toFloatOrNull()?.takeIf { it > 0f },
            iconHref = icon?.child("Icon")?.childText("href"),
            labelColor = labelStyle?.childText("color")?.let(::kmlColor),
            labelScale = labelStyle?.childText("scale")?.toFloatOrNull()?.takeIf { it > 0f },
        )
    }

    private fun markerStyle(style: KmlStyle, label: String): ShapeStyle {
        val href = style.iconHref
        if (href != null) {
            if (href.startsWith("http://") || href.startsWith("https://")) return ShapeStyle.ImagePattern(ImageRef.Url(href))
            log.warn("$label: the icon \"$href\" is not a web address and is not read")
        }
        return ShapeStyle.SolidColor(style.icon ?: WHITE)
    }

    private companion object {
        val GEOMETRY = setOf("Point", "LineString", "LinearRing", "Polygon", "MultiGeometry")
        val WHITE = ColorRgba(255, 255, 255)
        val CLEAR = ColorRgba(255, 255, 255, 0)
    }
}

/** KML writes a colour as `aabbggrr` (hex, alpha first, blue before red). `null` if [text] is not 8 hex digits. */
internal fun kmlColor(text: String): ColorRgba? {
    if (text.length != 8 || !text.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val (a, b, g, r) = text.chunked(2).map { it.toInt(16) }
    return ColorRgba(r, g, b, a)
}

internal fun kmlHex(color: ColorRgba): String {
    fun two(value: Int) = value.toString(16).padStart(2, '0')
    return two(color.alpha) + two(color.blue) + two(color.green) + two(color.red)
}

// ---- writing ----

private class KmlWriter(private val pretty: Boolean) {
    private val out = StringBuilder()
    private var depth = 0

    fun document(entities: List<DrawableEntity>): String {
        out.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        newline()
        open("""kml xmlns="http://www.opengis.net/kml/2.2"""")
        open("Document")
        entities.forEach(::placemark)
        close("Document")
        close("kml")
        return out.toString()
    }

    private fun placemark(entity: DrawableEntity) {
        open("""Placemark id="${Xml.escape(entity.id.id)}"""")
        // The name is a marker's label, else the property called `name`.
        ((entity as? DrawnMarker)?.label?.text ?: entity.properties["name"])?.let { text("name", it) }
        entity.description?.let { text("description", it) }
        time(entity.properties)
        style(entity)
        extendedData(entity)
        when (entity) {
            is DrawnMarker -> {
                open("Point")
                text("coordinates", coordinate(entity.position))
                close("Point")
            }
            is DrawnPolyline -> line(entity.vertices.map { it.position })
            is DrawnPolygon -> {
                val parts = PolygonParts.split(entity.rings.map { ring -> ring.map { it.position } })
                if (parts.size == 1) polygon(parts.single())
                else {
                    open("MultiGeometry")
                    parts.forEach(::polygon)
                    close("MultiGeometry")
                }
            }
        }
        close("Placemark")
    }

    // The properties that KML has an element of its own for are written there (see KmlReader.properties), not as extended data.
    private val ownElements = setOf("zIndex", "name", "timeBegin", "timeEnd", "timeStamp")

    private fun time(properties: Map<String, String>) {
        val begin = properties["timeBegin"]
        val end = properties["timeEnd"]
        if (begin != null || end != null) {
            open("TimeSpan")
            begin?.let { text("begin", it) }
            end?.let { text("end", it) }
            close("TimeSpan")
        } else {
            properties["timeStamp"]?.let { open("TimeStamp"); text("when", it); close("TimeStamp") }
        }
    }

    private fun extendedData(entity: DrawableEntity) {
        val named = entity.properties.filterKeys { it !in ownElements }
        if (entity.zIndex == 0 && named.isEmpty()) return
        open("ExtendedData")
        if (entity.zIndex != 0) data("zIndex", entity.zIndex.toString())
        named.forEach { (key, value) -> data(key, value) }
        close("ExtendedData")
    }

    private fun data(name: String, value: String) {
        open("""Data name="${Xml.escape(name)}"""")
        text("value", value)
        close("Data")
    }

    private fun style(entity: DrawableEntity) {
        open("Style")
        when (entity) {
            is DrawnPolygon -> {
                val outline = (entity.outlineStyle as? ShapeStyle.SolidColor)?.color
                if (outline != null) {
                    open("LineStyle"); text("color", kmlHex(outline)); text("width", number(entity.outlineWidth)); close("LineStyle")
                }
                open("PolyStyle")
                val fill = (entity.style as? ShapeStyle.SolidColor)?.color
                if (fill != null) text("color", kmlHex(fill)) else text("fill", "0")
                close("PolyStyle")
            }
            is DrawnPolyline -> {
                open("LineStyle")
                (entity.style as? ShapeStyle.SolidColor)?.let { text("color", kmlHex(it.color)) }
                text("width", number(entity.lineWidth))
                close("LineStyle")
            }
            is DrawnMarker -> {
                open("IconStyle")
                (entity.style as? ShapeStyle.SolidColor)?.let { text("color", kmlHex(it.color)) }
                text("scale", number(entity.size / 16f))
                val picture = ((entity.style as? ShapeStyle.ImagePattern)?.image as? ImageRef.Url)?.url
                if (picture != null) {
                    open("Icon"); text("href", picture); close("Icon")
                }
                close("IconStyle")
                entity.label?.let { label ->
                    open("LabelStyle")
                    text("color", kmlHex(label.color))
                    text("scale", number(label.size / 14f))
                    close("LabelStyle")
                }
            }
        }
        close("Style")
    }

    private fun polygon(part: List<List<GeoPosition>>) {
        open("Polygon")
        orientedPart(part).forEachIndexed { i, ring ->
            open(if (i == 0) "outerBoundaryIs" else "innerBoundaryIs")
            open("LinearRing")
            text("coordinates", (ring + ring.first()).joinToString(" ") { coordinate(it) })
            close("LinearRing")
            close(if (i == 0) "outerBoundaryIs" else "innerBoundaryIs")
        }
        close("Polygon")
    }

    private fun line(positions: List<GeoPosition>) {
        open("LineString")
        text("coordinates", positions.joinToString(" ") { coordinate(it) })
        close("LineString")
    }

    private fun coordinate(p: GeoPosition) =
        if (p.altitude != 0.0) "${p.longitude},${p.latitude},${p.altitude}" else "${p.longitude},${p.latitude}"

    // 5.0 is written 5 (a width, a scale), 0.5 as it is.
    private fun number(value: Float): String = if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else value.toString()

    private fun open(tag: String) {
        indent()
        out.append('<').append(tag).append('>')
        newline()
        depth++
    }

    private fun close(tag: String) {
        depth--
        indent()
        out.append("</").append(tag.substringBefore(' ')).append('>')
        newline()
    }

    private fun text(tag: String, value: String) {
        indent()
        out.append('<').append(tag).append('>').append(Xml.escape(value)).append("</").append(tag).append('>')
        newline()
    }

    private fun indent() {
        if (pretty) repeat(depth) { out.append("  ") }
    }

    private fun newline() {
        if (pretty) out.append('\n')
    }
}
