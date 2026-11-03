package com.tcsappdev.worldwinddraw.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The classes of the file format, and nothing else: they are what is read from and written to the JSON, kept apart from core's own classes
// on purpose. A file outlives the code that wrote it, so renaming a field or changing a default in core must never silently change what an old
// file means. Every default written here is therefore part of the format (see DrawJson.VERSION): a missing field IS that value, forever,
// whatever core's default becomes. Not writing the values that equal their default keeps the files small.
//
// What changes here changes the format: add a field with a default and old files still read; anything else needs a new version.

@Serializable
internal class DocumentDto(
    val format: String,
    val version: Int,
    /** The pictures the shapes use, each once: key to its bytes in base64. A shape refers to one by key, so a flag used by 400 shapes is stored once. */
    val images: Map<String, String> = emptyMap(),
    val entities: List<EntityDto>,
)

@Serializable
internal sealed interface EntityDto {
    val id: String

    @Serializable
    @SerialName("polygon")
    class Polygon(
        override val id: String,
        val style: StyleDto,
        /** The outer ring first, then the holes and further parts. */
        val rings: List<List<VertexDto>>,
        val outline: StyleDto? = null,
        val outlineWidth: Float = 1f,
        val description: String? = null,
        val zIndex: Int = 0,
        val properties: Map<String, String> = emptyMap(),
    ) : EntityDto

    @Serializable
    @SerialName("polyline")
    class Polyline(
        override val id: String,
        val style: StyleDto,
        val vertices: List<VertexDto>,
        val lineWidth: Float = 1f,
        val description: String? = null,
        val zIndex: Int = 0,
        val properties: Map<String, String> = emptyMap(),
    ) : EntityDto

    @Serializable
    @SerialName("marker")
    class Marker(
        override val id: String,
        val style: StyleDto,
        val position: PositionDto,
        val size: Float = 16f,
        val label: LabelDto? = null,
        val description: String? = null,
        val zIndex: Int = 0,
        val properties: Map<String, String> = emptyMap(),
        val rotation: Float = 0f,
    ) : EntityDto
}

@Serializable
internal class PositionDto(val lat: Double, val lon: Double, val alt: Double = 0.0)

@Serializable
internal class VertexDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val alt: Double = 0.0,
    val meta: Map<String, String> = emptyMap(),
)

@Serializable
internal class LabelDto(
    val text: String,
    val color: String = "#FFFFFF",
    val size: Int = 14,
    /** `null` is "no outline", so it has to be written when it is `null`: the default here is the black outline. */
    val outline: String? = "#000000",
    val position: LabelPositionDto = LabelPositionDto.Above,
)

@Serializable
internal sealed interface StyleDto {
    @Serializable
    @SerialName("solid")
    class Solid(val color: String) : StyleDto

    @Serializable
    @SerialName("imagePattern")
    class ImagePattern(val image: ImageDto, val opacity: Float = 1f) : StyleDto

    @Serializable
    @SerialName("imageFill")
    class ImageFill(
        val image: ImageDto,
        val fit: FitDto = FitDto.Contain,
        val scale: Float = 1f,
        val opacity: Float = 1f,
        val background: String? = null,
        val repeat: Boolean = false,
        val anchorX: Float? = null,
        val anchorY: Float? = null,
        val extendEdges: Boolean = false,
        val edgeSoftness: Float = 0f,
    ) : StyleDto

    @Serializable
    @SerialName("hatch")
    class Hatch(
        val pattern: HatchPatternDto,
        val color: String,
        val background: String? = null,
        val spacing: Int = 12,
        val thickness: Float = 2f,
    ) : StyleDto
}

@Serializable
internal sealed interface ImageDto {
    @Serializable
    @SerialName("url")
    class Url(val url: String) : ImageDto

    /** The bytes are in the document's `images`, under this key. */
    @Serializable
    @SerialName("bytes")
    class Bytes(val key: String) : ImageDto
}

// The enums are mirrored (not core's own written by name) so that renaming one in core is a compile error in the mappers, not a file that
// no longer opens.

@Serializable
internal enum class FitDto {
    @SerialName("contain") Contain,
    @SerialName("cover") Cover,
    @SerialName("stretch") Stretch,
    @SerialName("inscribed") Inscribed,
}

@Serializable
internal enum class HatchPatternDto {
    @SerialName("diagonalUp") DiagonalUp,
    @SerialName("diagonalDown") DiagonalDown,
    @SerialName("horizontal") Horizontal,
    @SerialName("vertical") Vertical,
    @SerialName("cross") Cross,
    @SerialName("dots") Dots,
}

@Serializable
internal enum class LabelPositionDto {
    @SerialName("above") Above,
    @SerialName("below") Below,
    @SerialName("left") Left,
    @SerialName("right") Right,
    @SerialName("center") Center,
}
