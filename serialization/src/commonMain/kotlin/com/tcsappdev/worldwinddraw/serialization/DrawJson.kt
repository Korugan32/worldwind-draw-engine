package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A file that cannot be read or written: the text is not this format, it is a newer version of it, or a shape in it is not valid. */
class DrawFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The library's own file format: a list of shapes written as JSON, losslessly. Everything a shape is comes back as it was — styles (with their
 * pictures), rings and holes, vertex ids and metadata, labels, descriptions, drawing order — so a drawing can be saved and loaded again, and
 * two saves compared.
 *
 * ```
 * val text = DrawJson.encode(repository.entities.value)
 * val shapes = DrawJson.decode(text)
 * ```
 *
 * The result is a `String`: where it goes (a file, a database, the network) is the host's business. A picture given as bytes is stored once
 * in the document, in base64, however many shapes use it; one given as a URL is only its URL, so loading it later needs the network.
 *
 * Every document says which [VERSION] of the format it is. Newer fields can be added without a new version (an old file just lacks them, and a
 * reader ignores the ones it doesn't know); a file of a newer version than this code knows is refused rather than half understood.
 */
object DrawJson {
    /** What the `format` field of every document says, so a stray JSON file is told apart from ours. */
    const val FORMAT = "worldwind-draw-engine"

    /** The version of the format this code writes and the newest it reads. */
    const val VERSION = 1

    private val compact = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    private val pretty = Json(compact) { prettyPrint = true }

    /**
     * The JSON for [entities], in their order. [pretty] adds line breaks and indentation, for a person to read; the content is the same.
     *
     * @throws DrawFormatException if two shapes have the same id, or two different pictures share a key (a key names one picture).
     */
    fun encode(entities: List<DrawableEntity>, pretty: Boolean = false): String {
        val document = try {
            Encoder.document(entities)
        } catch (e: IllegalArgumentException) {
            throw DrawFormatException(e.message ?: "cannot write these shapes", e)
        }
        return (if (pretty) this.pretty else compact).encodeToString(DocumentDto.serializer(), document)
    }

    /**
     * The shapes in [text], in the order they were written.
     *
     * @throws DrawFormatException if [text] is not JSON, not a document of this format, of a newer [VERSION], or holds a shape that breaks the
     * model's rules (a polygon with two points, a latitude of 120, a picture that is missing, ...). The message names the shape.
     */
    fun decode(text: String): List<DrawableEntity> {
        val document = try {
            compact.decodeFromString(DocumentDto.serializer(), text)
        } catch (e: SerializationException) {
            throw DrawFormatException("not a ${FORMAT} document: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw DrawFormatException("not a ${FORMAT} document: ${e.message}", e)
        }
        if (document.format != FORMAT) throw DrawFormatException("not a $FORMAT document (format is \"${document.format}\")")
        if (document.version < 1) throw DrawFormatException("invalid version ${document.version}")
        if (document.version > VERSION) {
            throw DrawFormatException("the document is version ${document.version}, this code reads up to version $VERSION: update the library")
        }
        return Decoder(document).entities()
    }
}
