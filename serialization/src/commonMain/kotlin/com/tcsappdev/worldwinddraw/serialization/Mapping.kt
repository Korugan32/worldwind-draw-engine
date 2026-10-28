package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.LabelPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.io.encoding.Base64

// Core's classes to the format's (Encoder) and back (Decoder). Every `when` over an enum or a sealed type is exhaustive on purpose: a new
// kind of style or a new enum value in core is a compile error here, until the format has been taught about it.

internal object Encoder {
    fun document(entities: List<DrawableEntity>): DocumentDto {
        val ids = HashSet<String>()
        for (entity in entities) require(ids.add(entity.id.id)) { "two shapes have the id \"${entity.id.id}\"" }
        val pictures = LinkedHashMap<String, ByteArray>()
        val dtos = entities.map { entity(it, pictures) }
        return DocumentDto(
            format = DrawJson.FORMAT,
            version = DrawJson.VERSION,
            images = pictures.mapValues { Base64.encode(it.value) },
            entities = dtos,
        )
    }

    private fun entity(entity: DrawableEntity, pictures: MutableMap<String, ByteArray>): EntityDto = when (entity) {
        is DrawnPolygon -> EntityDto.Polygon(
            id = entity.id.id,
            style = style(entity.style, pictures),
            rings = entity.rings.map { ring -> ring.map(::vertex) },
            outline = entity.outlineStyle?.let { style(it, pictures) },
            outlineWidth = entity.outlineWidth,
            description = entity.description,
            zIndex = entity.zIndex,
            properties = entity.properties,
        )
        is DrawnPolyline -> EntityDto.Polyline(
            id = entity.id.id,
            style = style(entity.style, pictures),
            vertices = entity.vertices.map(::vertex),
            lineWidth = entity.lineWidth,
            description = entity.description,
            zIndex = entity.zIndex,
            properties = entity.properties,
        )
        is DrawnMarker -> EntityDto.Marker(
            id = entity.id.id,
            style = style(entity.style, pictures),
            position = PositionDto(entity.position.latitude, entity.position.longitude, entity.position.altitude),
            size = entity.size,
            label = entity.label?.let(::label),
            description = entity.description,
            zIndex = entity.zIndex,
            properties = entity.properties,
        )
    }

    private fun vertex(vertex: Vertex) = VertexDto(
        id = vertex.id.id,
        lat = vertex.position.latitude,
        lon = vertex.position.longitude,
        alt = vertex.position.altitude,
        meta = vertex.metadata,
    )

    private fun label(label: MarkerLabel) = LabelDto(
        text = label.text,
        color = hex(label.color),
        size = label.size,
        outline = label.outlineColor?.let(::hex),
        position = when (label.position) {
            LabelPosition.Above -> LabelPositionDto.Above
            LabelPosition.Below -> LabelPositionDto.Below
            LabelPosition.Left -> LabelPositionDto.Left
            LabelPosition.Right -> LabelPositionDto.Right
            LabelPosition.Center -> LabelPositionDto.Center
        },
    )

    private fun style(style: ShapeStyle, pictures: MutableMap<String, ByteArray>): StyleDto = when (style) {
        is ShapeStyle.SolidColor -> StyleDto.Solid(hex(style.color))
        is ShapeStyle.ImagePattern -> StyleDto.ImagePattern(image(style.image, pictures), style.opacity)
        is ShapeStyle.ImageFill -> StyleDto.ImageFill(
            image = image(style.image, pictures),
            fit = when (style.fit) {
                ImageFit.Contain -> FitDto.Contain
                ImageFit.Cover -> FitDto.Cover
                ImageFit.Stretch -> FitDto.Stretch
                ImageFit.Inscribed -> FitDto.Inscribed
            },
            scale = style.scale,
            opacity = style.opacity,
            background = style.backgroundColor?.let(::hex),
            repeat = style.repeat,
            anchorX = style.anchorX,
            anchorY = style.anchorY,
            extendEdges = style.extendEdges,
            edgeSoftness = style.edgeSoftness,
        )
        is ShapeStyle.Hatch -> StyleDto.Hatch(
            pattern = when (style.pattern) {
                HatchPattern.DiagonalUp -> HatchPatternDto.DiagonalUp
                HatchPattern.DiagonalDown -> HatchPatternDto.DiagonalDown
                HatchPattern.Horizontal -> HatchPatternDto.Horizontal
                HatchPattern.Vertical -> HatchPatternDto.Vertical
                HatchPattern.Cross -> HatchPatternDto.Cross
                HatchPattern.Dots -> HatchPatternDto.Dots
            },
            color = hex(style.color),
            background = style.backgroundColor?.let(::hex),
            spacing = style.spacing,
            thickness = style.thickness,
        )
    }

    private fun image(image: ImageRef, pictures: MutableMap<String, ByteArray>): ImageDto = when (image) {
        is ImageRef.Url -> ImageDto.Url(image.url)
        is ImageRef.Bytes -> {
            val seen = pictures[image.key]
            if (seen == null) pictures[image.key] = image.data
            else require(seen.contentEquals(image.data)) { "two different pictures share the key \"${image.key}\"" }
            ImageDto.Bytes(image.key)
        }
    }
}

internal class Decoder(private val document: DocumentDto) {
    // Decoded once: a picture used by many shapes is the same array in all of them (ImageRef.Bytes is equal by key anyway).
    private val pictures: Map<String, ByteArray> = document.images.mapValues { (key, text) ->
        try {
            Base64.decode(text)
        } catch (e: IllegalArgumentException) {
            throw DrawFormatException("the picture \"$key\" is not valid base64", e)
        }
    }

    fun entities(): List<DrawableEntity> {
        val ids = HashSet<String>()
        return document.entities.map { dto ->
            if (!ids.add(dto.id)) throw DrawFormatException("two shapes have the id \"${dto.id}\"")
            try {
                entity(dto)
            } catch (e: DrawFormatException) {
                throw DrawFormatException("shape \"${dto.id}\": ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                // The model's own rules (require(...) in core's constructors) say what is wrong.
                throw DrawFormatException("shape \"${dto.id}\": ${e.message}", e)
            }
        }
    }

    private fun entity(dto: EntityDto): DrawableEntity = when (dto) {
        is EntityDto.Polygon -> {
            require(dto.rings.isNotEmpty()) { "a polygon needs at least one ring" }
            DrawnPolygon(
                id = EntityId(dto.id),
                style = style(dto.style),
                vertices = dto.rings.first().map(::vertex),
                outlineStyle = dto.outline?.let(::style),
                outlineWidth = dto.outlineWidth,
                extraRings = dto.rings.drop(1).map { ring -> ring.map(::vertex) },
                description = dto.description,
                zIndex = dto.zIndex,
                properties = dto.properties,
            )
        }
        is EntityDto.Polyline -> DrawnPolyline(
            id = EntityId(dto.id),
            style = style(dto.style),
            vertices = dto.vertices.map(::vertex),
            lineWidth = dto.lineWidth,
            description = dto.description,
            zIndex = dto.zIndex,
            properties = dto.properties,
        )
        is EntityDto.Marker -> DrawnMarker(
            id = EntityId(dto.id),
            style = style(dto.style),
            position = GeoPosition(dto.position.lat, dto.position.lon, dto.position.alt),
            size = dto.size,
            label = dto.label?.let(::label),
            description = dto.description,
            zIndex = dto.zIndex,
            properties = dto.properties,
        )
    }

    private fun vertex(dto: VertexDto) = Vertex(VertexId(dto.id), GeoPosition(dto.lat, dto.lon, dto.alt), dto.meta)

    private fun label(dto: LabelDto) = MarkerLabel(
        text = dto.text,
        color = color(dto.color),
        size = dto.size,
        outlineColor = dto.outline?.let(::color),
        position = when (dto.position) {
            LabelPositionDto.Above -> LabelPosition.Above
            LabelPositionDto.Below -> LabelPosition.Below
            LabelPositionDto.Left -> LabelPosition.Left
            LabelPositionDto.Right -> LabelPosition.Right
            LabelPositionDto.Center -> LabelPosition.Center
        },
    )

    private fun style(dto: StyleDto): ShapeStyle = when (dto) {
        is StyleDto.Solid -> ShapeStyle.SolidColor(color(dto.color))
        is StyleDto.ImagePattern -> ShapeStyle.ImagePattern(image(dto.image), dto.opacity)
        is StyleDto.ImageFill -> ShapeStyle.ImageFill(
            image = image(dto.image),
            fit = when (dto.fit) {
                FitDto.Contain -> ImageFit.Contain
                FitDto.Cover -> ImageFit.Cover
                FitDto.Stretch -> ImageFit.Stretch
                FitDto.Inscribed -> ImageFit.Inscribed
            },
            scale = dto.scale,
            opacity = dto.opacity,
            backgroundColor = dto.background?.let(::color),
            repeat = dto.repeat,
            anchorX = dto.anchorX,
            anchorY = dto.anchorY,
            extendEdges = dto.extendEdges,
            edgeSoftness = dto.edgeSoftness,
        )
        is StyleDto.Hatch -> ShapeStyle.Hatch(
            pattern = when (dto.pattern) {
                HatchPatternDto.DiagonalUp -> HatchPattern.DiagonalUp
                HatchPatternDto.DiagonalDown -> HatchPattern.DiagonalDown
                HatchPatternDto.Horizontal -> HatchPattern.Horizontal
                HatchPatternDto.Vertical -> HatchPattern.Vertical
                HatchPatternDto.Cross -> HatchPattern.Cross
                HatchPatternDto.Dots -> HatchPattern.Dots
            },
            color = color(dto.color),
            backgroundColor = dto.background?.let(::color),
            spacing = dto.spacing,
            thickness = dto.thickness,
        )
    }

    private fun image(dto: ImageDto): ImageRef = when (dto) {
        is ImageDto.Url -> ImageRef.Url(dto.url)
        is ImageDto.Bytes -> {
            val data = pictures[dto.key] ?: throw DrawFormatException("the picture \"${dto.key}\" is not in the document's images")
            ImageRef.Bytes(dto.key, data)
        }
    }
}

/** `#RRGGBB`, or `#RRGGBBAA` when the colour is not fully opaque. */
internal fun hex(color: ColorRgba): String {
    fun two(value: Int) = value.toString(16).padStart(2, '0').uppercase()
    val rgb = "#" + two(color.red) + two(color.green) + two(color.blue)
    return if (color.alpha == 255) rgb else rgb + two(color.alpha)
}

internal fun color(text: String): ColorRgba {
    require(text.startsWith("#") && (text.length == 7 || text.length == 9)) { "\"$text\" is not a colour (#RRGGBB or #RRGGBBAA)" }
    val channels = text.drop(1).chunked(2).map { pair ->
        pair.toIntOrNull(16)?.takeIf { pair.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            ?: throw IllegalArgumentException("\"$text\" is not a colour (#RRGGBB or #RRGGBBAA)")
    }
    return ColorRgba(channels[0], channels[1], channels[2], channels.getOrElse(3) { 255 })
}
