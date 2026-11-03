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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawJsonTest {
    private fun v(id: String, lat: Double, lon: Double, alt: Double = 0.0, meta: Map<String, String> = emptyMap()) =
        Vertex(VertexId(id), GeoPosition(lat, lon, alt), meta)

    private val red = ShapeStyle.SolidColor(ColorRgba(220, 30, 30))
    private val flag = byteArrayOf(1, 2, 3, 4, 5, -1, -128, 127)

    private fun simplePolygon(id: String = "p") =
        DrawnPolygon(EntityId(id), red, listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)))

    private fun roundTrip(entities: List<DrawableEntity>, pretty: Boolean = false) = DrawJson.decode(DrawJson.encode(entities, pretty))

    private fun failure(text: String) = assertFailsWith<DrawFormatException> { DrawJson.decode(text) }.message.orEmpty()

    // A whole drawing: every kind of shape and every kind of style, using every field at a value that is not its default.
    private fun everything(): List<DrawableEntity> = listOf(
        DrawnPolygon(
            id = EntityId("tr"),
            style = ShapeStyle.ImageFill(
                image = ImageRef.Bytes("flag", flag),
                fit = ImageFit.Inscribed,
                scale = 0.75f,
                opacity = 0.9f,
                backgroundColor = ColorRgba(10, 20, 30, 128),
                repeat = true,
                anchorX = 0.25f,
                anchorY = 0.75f,
                extendEdges = true,
                edgeSoftness = 0.5f,
            ),
            vertices = listOf(v("o1", 36.0, 26.0, 100.5, mapOf("name" to "west", "x" to "1")), v("o2", 42.0, 26.0), v("o3", 42.0, 45.0), v("o4", 36.0, 45.0)),
            outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255)),
            outlineWidth = 4f,
            extraRings = listOf(
                listOf(v("h1", 38.0, 30.0), v("h2", 40.0, 30.0), v("h3", 40.0, 32.0)),
                listOf(v("i1", 38.5, 40.0), v("i2", 39.5, 40.0), v("i3", 39.5, 41.0), v("i4", 38.5, 41.0)),
            ),
            description = "Türkiye \"ülke\"\nikinci satır",
            zIndex = 3,
        ),
        DrawnPolygon(
            EntityId("hatched"),
            ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(255, 0, 0, 200), ColorRgba(0, 0, 0, 40), spacing = 20, thickness = 3.5f),
            listOf(v("q1", 1.0, 1.0), v("q2", 1.0, 2.0), v("q3", 2.0, 2.0)),
        ),
        DrawnPolygon(
            EntityId("tiled"),
            ShapeStyle.ImagePattern(ImageRef.Url("https://example.com/water.png"), opacity = 0.6f),
            listOf(v("r1", 3.0, 3.0), v("r2", 3.0, 4.0), v("r3", 4.0, 4.0)),
        ),
        DrawnPolyline(
            EntityId("road"),
            ShapeStyle.SolidColor(ColorRgba(255, 220, 0)),
            listOf(v("l1", 41.0, 29.0), v("l2", 39.9, 32.8), v("l3", 37.0, 35.3)),
            lineWidth = 5f,
            description = "a road",
            zIndex = -2,
        ),
        DrawnMarker(
            EntityId("ankara"),
            ShapeStyle.ImagePattern(ImageRef.Bytes("flag", flag)),
            GeoPosition(39.93, 32.86, 850.0),
            size = 64f,
            label = MarkerLabel("Ankara", ColorRgba(255, 230, 0, 120), 20, ColorRgba(1, 2, 3, 4), LabelPosition.Below),
            description = "capital",
            zIndex = 7,
        ),
    )

    @Test
    fun aDrawingWithEverythingInItComesBackEqual() {
        val before = everything()
        val after = roundTrip(before)
        assertEquals(before, after)
        // ImageRef.Bytes is equal by its key, so the bytes themselves are checked apart.
        val flagFill = (after[0] as DrawnPolygon).style as ShapeStyle.ImageFill
        assertContentEquals(flag, (flagFill.image as ImageRef.Bytes).data)
    }

    @Test
    fun theSameThroughTheReadableFormToo() {
        assertEquals(everything(), roundTrip(everything(), pretty = true))
    }

    @Test
    fun theOrderOfTheShapesIsKept() {
        val ids = listOf("z", "a", "m", "b")
        assertEquals(ids, roundTrip(ids.map { simplePolygon(it) }).map { it.id.id })
    }

    @Test
    fun noShapesIsAValidDocument() {
        assertEquals(emptyList(), roundTrip(emptyList()))
    }

    @Test
    fun theSamePictureIsStoredOnceHoweverManyShapesUseIt() {
        val shapes = (1..5).map { i ->
            DrawnPolygon(
                EntityId("s$i"),
                ShapeStyle.ImageFill(ImageRef.Bytes("flag", flag)),
                listOf(v("a$i", 0.0, 0.0), v("b$i", 0.0, 1.0), v("c$i", 1.0, 1.0)),
            )
        }
        val text = DrawJson.encode(shapes)
        val base64 = "AQIDBAX/gH8=" // flag
        assertEquals(1, Regex.escape(base64).toRegex().findAll(text).count())
        assertEquals(5, roundTrip(shapes).size)
    }

    @Test
    fun valuesThatAreTheDefaultAreNotWritten() {
        val text = DrawJson.encode(listOf(simplePolygon()))
        assertFalse("zIndex" in text)
        assertFalse("outlineWidth" in text)
        assertFalse("description" in text)
        assertFalse("alt" in text)
        assertFalse("images" in text)
    }

    @Test
    fun aMarkersRotationSurvivesAndIsLeftOutWhenItIsZero() {
        val turned = DrawnMarker(EntityId("m"), red, GeoPosition(1.0, 2.0), rotation = 135f)
        assertEquals(135f, (roundTrip(listOf(turned)).single() as DrawnMarker).rotation)
        assertTrue("rotation" in DrawJson.encode(listOf(turned)))
        assertFalse("rotation" in DrawJson.encode(listOf(turned.copy(rotation = 0f))))
    }

    @Test
    fun aLabelWithoutAnOutlineIsNotGivenTheDefaultOne() {
        val marker = DrawnMarker(EntityId("m"), red, GeoPosition(1.0, 2.0), label = MarkerLabel("x", outlineColor = null))
        assertEquals(null, (roundTrip(listOf(marker)).single() as DrawnMarker).label!!.outlineColor)
        val plain = DrawnMarker(EntityId("m"), red, GeoPosition(1.0, 2.0), label = MarkerLabel("x"))
        assertEquals(ColorRgba(0, 0, 0), (roundTrip(listOf(plain)).single() as DrawnMarker).label!!.outlineColor)
    }

    // The file written by hand below is what version 1 means: a field that is missing is its default, whatever core's defaults become.
    @Test
    fun aVersionOneFileWithOnlyTheRequiredFieldsReadsAsTheDocumentedDefaults() {
        val text = """
            {"format":"worldwind-draw-engine","version":1,"entities":[
              {"type":"polygon","id":"p","style":{"type":"solid","color":"#FF0000"},
               "rings":[[{"id":"a","lat":0,"lon":0},{"id":"b","lat":0,"lon":1},{"id":"c","lat":1,"lon":1,"alt":5}]]},
              {"type":"polyline","id":"l","style":{"type":"solid","color":"#00FF0080"},
               "vertices":[{"id":"x","lat":0,"lon":0},{"id":"y","lat":1,"lon":1}]},
              {"type":"marker","id":"m","style":{"type":"imageFill","image":{"type":"url","url":"https://e.com/a.png"}},
               "position":{"lat":10,"lon":20},"label":{"text":"hi"}}
            ]}
        """.trimIndent()
        val (polygon, line, marker) = DrawJson.decode(text)
        polygon as DrawnPolygon
        assertEquals(red.copy(color = ColorRgba(255, 0, 0)), polygon.style)
        assertEquals(1f, polygon.outlineWidth)
        assertEquals(null, polygon.outlineStyle)
        assertEquals(0, polygon.zIndex)
        assertEquals(5.0, polygon.vertices[2].position.altitude)
        line as DrawnPolyline
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0, 255, 0, 128)), line.style)
        assertEquals(1f, line.lineWidth)
        marker as DrawnMarker
        assertEquals(ShapeStyle.ImageFill(ImageRef.Url("https://e.com/a.png")), marker.style)
        assertEquals(16f, marker.size)
        assertEquals(MarkerLabel("hi", ColorRgba(255, 255, 255), 14, ColorRgba(0, 0, 0), LabelPosition.Above), marker.label)
    }

    @Test
    fun fieldsThisVersionDoesNotKnowAreIgnored() {
        val text = """{"format":"worldwind-draw-engine","version":1,"future":true,"entities":[
            {"type":"marker","id":"m","style":{"type":"solid","color":"#FFFFFF"},"position":{"lat":1,"lon":2},"glow":9}]}"""
        assertEquals(1, DrawJson.decode(text).size)
    }

    @Test
    fun colourWritingKeepsTheAlphaOnlyWhenThereIsAny() {
        assertEquals("#0A141E", hex(ColorRgba(10, 20, 30)))
        assertEquals("#0A141E80", hex(ColorRgba(10, 20, 30, 128)))
        assertEquals("#00000000", hex(ColorRgba(0, 0, 0, 0)))
        assertEquals(ColorRgba(171, 205, 239), color("#abcdef"))
        assertEquals(ColorRgba(171, 205, 239, 1), color("#ABCDEF01"))
    }

    @Test
    fun aTextThatIsNotAColourIsRefused() {
        for (bad in listOf("", "#", "red", "#FFF", "#12345G", "#+12345", "-1-1-1", "#1234567", "FF0000")) {
            assertFailsWith<IllegalArgumentException>(bad) { color(bad) }
        }
    }

    @Test
    fun textThatIsNotJsonIsRefused() {
        assertTrue("not a worldwind-draw-engine document" in failure("hello"))
        assertTrue("not a worldwind-draw-engine document" in failure(""))
    }

    @Test
    fun anotherKindOfJsonIsRefused() {
        assertTrue("format" in failure("""{"type":"FeatureCollection","features":[]}"""))
        assertTrue("\"geojson\"" in failure("""{"format":"geojson","version":1,"entities":[]}"""))
    }

    @Test
    fun aNewerVersionIsRefusedNotHalfRead() {
        val message = failure("""{"format":"worldwind-draw-engine","version":${DrawJson.VERSION + 1},"entities":[]}""")
        assertTrue("version ${DrawJson.VERSION + 1}" in message && "update the library" in message, message)
    }

    @Test
    fun versionZeroIsRefused() {
        assertTrue("invalid version" in failure("""{"format":"worldwind-draw-engine","version":0,"entities":[]}"""))
    }

    @Test
    fun aShapeThatBreaksTheModelIsRefusedAndNamed() {
        val twoPoints = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"polygon","id":"tiny","style":{"type":"solid","color":"#FF0000"},"rings":[[{"id":"a","lat":0,"lon":0},{"id":"b","lat":1,"lon":1}]]}]}"""
        val message = failure(twoPoints)
        assertTrue("shape \"tiny\"" in message && "3 vertices" in message, message)
    }

    @Test
    fun aLatitudeOutOfRangeIsRefusedAndNamed() {
        val text = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"marker","id":"far","style":{"type":"solid","color":"#FF0000"},"position":{"lat":120,"lon":0}}]}"""
        assertTrue("shape \"far\"" in failure(text))
    }

    @Test
    fun aPolygonWithNoRingsIsRefused() {
        val text = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"polygon","id":"empty","style":{"type":"solid","color":"#FF0000"},"rings":[]}]}"""
        assertTrue("shape \"empty\"" in failure(text))
    }

    @Test
    fun aPictureThatIsNotInTheDocumentIsRefused() {
        val text = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"marker","id":"m","style":{"type":"imagePattern","image":{"type":"bytes","key":"gone"}},"position":{"lat":1,"lon":2}}]}"""
        val message = failure(text)
        assertTrue("shape \"m\"" in message && "\"gone\"" in message, message)
    }

    @Test
    fun picturesThatAreNotBase64AreRefused() {
        val text = """{"format":"worldwind-draw-engine","version":1,"images":{"f":"***"},"entities":[]}"""
        assertTrue("\"f\"" in failure(text))
    }

    @Test
    fun aStyleOrShapeKindThisVersionDoesNotKnowIsRefused() {
        val text = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"circle","id":"c","style":{"type":"solid","color":"#FF0000"}}]}"""
        assertTrue("circle" in failure(text))
    }

    @Test
    fun aBadColourIsRefusedAndNamed() {
        val text = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"marker","id":"m","style":{"type":"solid","color":"red"},"position":{"lat":1,"lon":2}}]}"""
        val message = failure(text)
        assertTrue("shape \"m\"" in message && "red" in message, message)
    }

    @Test
    fun twoShapesWithTheSameIdAreRefusedWhenReading() {
        val shape = """{"type":"marker","id":"m","style":{"type":"solid","color":"#FF0000"},"position":{"lat":1,"lon":2}}"""
        assertTrue("\"m\"" in failure("""{"format":"worldwind-draw-engine","version":1,"entities":[$shape,$shape]}"""))
    }

    @Test
    fun twoShapesWithTheSameIdAreRefusedWhenWriting() {
        assertFailsWith<DrawFormatException> { DrawJson.encode(listOf(simplePolygon("same"), simplePolygon("same"))) }
    }

    @Test
    fun twoDifferentPicturesUnderOneKeyAreRefusedWhenWriting() {
        fun marker(id: String, data: ByteArray) =
            DrawnMarker(EntityId(id), ShapeStyle.ImagePattern(ImageRef.Bytes("k", data)), GeoPosition(1.0, 2.0))
        val message = assertFailsWith<DrawFormatException> {
            DrawJson.encode(listOf(marker("a", byteArrayOf(1)), marker("b", byteArrayOf(2))))
        }.message.orEmpty()
        assertTrue("\"k\"" in message, message)
    }

    @Test
    fun theSamePictureUnderOneKeyIsFine() {
        fun marker(id: String) = DrawnMarker(EntityId(id), ShapeStyle.ImagePattern(ImageRef.Bytes("k", byteArrayOf(1, 2))), GeoPosition(1.0, 2.0))
        assertEquals(2, roundTrip(listOf(marker("a"), marker("b"))).size)
    }

    @Test
    fun theDocumentSaysWhatItIs() {
        val text = DrawJson.encode(emptyList())
        assertTrue("\"format\":\"worldwind-draw-engine\"" in text)
        assertTrue("\"version\":${DrawJson.VERSION}" in text)
    }

    @Test
    fun coordinatesAreNotRoundedOnTheWay() {
        val precise = DrawnMarker(EntityId("m"), red, GeoPosition(39.93123456789012, -122.000000000001, 0.1 + 0.2))
        assertEquals(precise, roundTrip(listOf(precise)).single())
    }

    // ---- properties ----

    @Test
    fun propertiesComeBackOnEveryKindOfShape() {
        val data = mapOf("population" to "5000000", "code" to "TR", "quote" to "a \"b\" <c> & d", "empty" to "")
        val shapes = listOf(
            simplePolygon("p").copy(properties = data),
            DrawnPolyline(EntityId("l"), red, listOf(v("x", 0.0, 0.0), v("y", 1.0, 1.0)), properties = data),
            DrawnMarker(EntityId("m"), red, GeoPosition(1.0, 2.0), properties = data),
        )
        assertEquals(shapes.map { data }, roundTrip(shapes).map { it.properties })
    }

    @Test
    fun noPropertiesAreNotWritten() {
        assertFalse("properties" in DrawJson.encode(listOf(simplePolygon())))
        assertEquals(emptyMap(), roundTrip(listOf(simplePolygon())).single().properties)
    }

    @Test
    fun aFileWithoutPropertiesReadsAsNone() {
        val text = """{"format":"worldwind-draw-engine","version":1,"entities":[
            {"type":"marker","id":"m","style":{"type":"solid","color":"#FFFFFF"},"position":{"lat":1,"lon":2}}]}"""
        assertEquals(emptyMap(), DrawJson.decode(text).single().properties)
    }
}
