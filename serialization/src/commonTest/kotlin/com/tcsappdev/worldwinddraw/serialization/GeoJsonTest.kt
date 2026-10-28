package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoJsonTest {
    private fun collection(vararg features: String) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private fun feature(geometry: String, properties: String = "{}", id: String? = null) =
        """{"type":"Feature",${id?.let { "\"id\":$it," } ?: ""}"properties":$properties,"geometry":$geometry}"""

    private val square = "[[0,0],[10,0],[10,10],[0,10],[0,0]]"
    private val inner = "[[2,2],[2,4],[4,4],[4,2],[2,2]]"

    private fun v(id: String, lat: Double, lon: Double) = Vertex(VertexId(id), GeoPosition(lat, lon))

    private fun encoded(entity: DrawableEntity): JsonObject =
        Json.parseToJsonElement(GeoJson.encode(listOf(entity))).jsonObject["features"]!!.jsonArray.single().jsonObject

    private fun coordinates(feature: JsonObject): JsonArray = feature["geometry"]!!.jsonObject["coordinates"]!!.jsonArray

    private fun ring(array: JsonElement): List<Pair<Double, Double>> = array.jsonArray.map { it.jsonArray[0].jsonPrimitive.doubleOrNull!! to it.jsonArray[1].jsonPrimitive.doubleOrNull!! }

    // ---- reading ----

    @Test
    fun aPolygonWithAHoleBecomesOnePolygonOfTwoRingsWithoutTheClosingPoints() {
        val result = GeoJson.decode(collection(feature("""{"type":"Polygon","coordinates":[$square,$inner]}""", id = "\"tr\"")))
        val polygon = result.entities.single() as DrawnPolygon
        assertEquals("tr", polygon.id.id)
        assertEquals(2, polygon.rings.size)
        assertEquals(4, polygon.rings[0].size)
        assertEquals(4, polygon.rings[1].size)
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    @Test
    fun coordinatesAreLongitudeThenLatitude() {
        val polygon = GeoJson.decode(feature("""{"type":"Polygon","coordinates":[[[30,40,5],[31,40],[31,41],[30,40,5]]]}""")).entities.single() as DrawnPolygon
        assertEquals(GeoPosition(latitude = 40.0, longitude = 30.0, altitude = 5.0), polygon.vertices[0].position)
        assertEquals(GeoPosition(latitude = 40.0, longitude = 31.0), polygon.vertices[1].position)
    }

    @Test
    fun vertexIdsAreMadeUpFromTheShapeRingAndIndex() {
        val polygon = GeoJson.decode(feature("""{"type":"Polygon","coordinates":[$square,$inner]}""", id = "\"a\"")).entities.single() as DrawnPolygon
        assertEquals(listOf("a-0-0", "a-0-1", "a-0-2", "a-0-3"), polygon.rings[0].map { it.id.id })
        assertEquals("a-1-0", polygon.rings[1][0].id.id)
    }

    @Test
    fun aMultiPolygonBecomesOnePolygonOfAllItsRings() {
        val text = """{"type":"MultiPolygon","coordinates":[[$square,$inner],[[[20,20],[30,20],[30,30],[20,20]]]]}"""
        val polygon = GeoJson.decode(feature(text)).entities.single() as DrawnPolygon
        assertEquals(3, polygon.rings.size)
        assertEquals(listOf(4, 4, 3), polygon.rings.map { it.size })
    }

    @Test
    fun aPointAndALineAreAMarkerAndAPolyline() {
        val result = GeoJson.decode(collection(
            feature("""{"type":"Point","coordinates":[32.86,39.93,850]}"""),
            feature("""{"type":"LineString","coordinates":[[0,0],[1,1],[2,0]]}"""),
        ))
        val marker = result.entities[0] as DrawnMarker
        assertEquals(GeoPosition(39.93, 32.86, 850.0), marker.position)
        assertEquals(3, (result.entities[1] as DrawnPolyline).vertices.size)
    }

    @Test
    fun multiPointsAndMultiLinesAndCollectionsMakeOneShapePerMember() {
        val result = GeoJson.decode(collection(
            feature("""{"type":"MultiPoint","coordinates":[[1,1],[2,2],[3,3]]}""", id = "\"pts\""),
            feature("""{"type":"MultiLineString","coordinates":[[[0,0],[1,1]],[[2,2],[3,3]]]}""", id = "\"lines\""),
            feature("""{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[5,5]},{"type":"LineString","coordinates":[[0,0],[1,0]]}]}""", id = "\"mix\""),
        ))
        assertEquals(listOf("pts-1", "pts-2", "pts-3", "lines-1", "lines-2", "mix-1", "mix-2"), result.entities.map { it.id.id })
        assertTrue(result.entities.take(3).all { it is DrawnMarker })
        assertTrue(result.entities[5] is DrawnMarker && result.entities[6] is DrawnPolyline)
    }

    @Test
    fun aSingleFeatureAndABareGeometryAreReadToo() {
        assertTrue(GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""")).entities.single() is DrawnMarker)
        val bare = GeoJson.decode("""{"type":"LineString","coordinates":[[0,0],[1,1]]}""")
        assertTrue(bare.entities.single() is DrawnPolyline)
    }

    @Test
    fun theFeatureIdIsTheShapeIdAndANumberWorksToo() {
        val result = GeoJson.decode(collection(feature("""{"type":"Point","coordinates":[1,2]}""", id = "42")))
        assertEquals("42", result.entities.single().id.id)
    }

    @Test
    fun aFeatureWithoutAnIdGetsOneFromItsPlace() {
        val result = GeoJson.decode(collection(
            feature("""{"type":"Point","coordinates":[1,2]}"""),
            feature("""{"type":"Point","coordinates":[3,4]}"""),
        ))
        assertEquals(listOf("feature-1", "feature-2"), result.entities.map { it.id.id })
    }

    @Test
    fun idsThatRepeatAreMadeUnique() {
        val result = GeoJson.decode(collection(
            feature("""{"type":"Point","coordinates":[1,2]}""", id = "\"x\""),
            feature("""{"type":"Point","coordinates":[3,4]}""", id = "\"x\""),
            feature("""{"type":"Point","coordinates":[5,6]}""", id = "\"x\""),
        ))
        assertEquals(3, result.entities.map { it.id.id }.toSet().size)
        assertEquals("x", result.entities[0].id.id)
    }

    @Test
    fun theDescriptionIsTheDescriptionPropertyOrElseTheName() {
        val result = GeoJson.decode(collection(
            feature("""{"type":"Point","coordinates":[1,2]}""", """{"name":"Ankara","description":"capital"}"""),
            feature("""{"type":"Point","coordinates":[1,2]}""", """{"name":"Izmir"}"""),
            feature("""{"type":"Point","coordinates":[1,2]}""", """{"other":1}"""),
        ))
        assertEquals(listOf("capital", "Izmir", null), result.entities.map { it.description })
    }

    @Test
    fun simplestylePropertiesGiveTheColoursWidthsAndOpacities() {
        val polygon = GeoJson.decode(feature(
            """{"type":"Polygon","coordinates":[$square]}""",
            """{"fill":"#ff0000","fill-opacity":0.5,"stroke":"#0f0","stroke-width":3,"stroke-opacity":0.25}""",
        )).entities.single() as DrawnPolygon
        assertEquals(ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128)), polygon.style)
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0, 255, 0, 64)), polygon.outlineStyle)
        assertEquals(3f, polygon.outlineWidth)
    }

    @Test
    fun missingStyleIsTheSpecsDefault() {
        val polygon = GeoJson.decode(feature("""{"type":"Polygon","coordinates":[$square]}""")).entities.single() as DrawnPolygon
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0x55, 0x55, 0x55, 153)), polygon.style) // fill-opacity 0.6
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0x55, 0x55, 0x55)), polygon.outlineStyle)
        assertEquals(2f, polygon.outlineWidth)
        val marker = GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""")).entities.single() as DrawnMarker
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0x7e, 0x7e, 0x7e)), marker.style)
        assertEquals(16f, marker.size)
    }

    @Test
    fun aLineTakesItsColourFromStroke() {
        val line = GeoJson.decode(feature("""{"type":"LineString","coordinates":[[0,0],[1,1]]}""", """{"stroke":"#ffdc00","stroke-width":5}""")).entities.single() as DrawnPolyline
        assertEquals(ShapeStyle.SolidColor(ColorRgba(255, 220, 0)), line.style)
        assertEquals(5f, line.lineWidth)
    }

    @Test
    fun markerColourAndSize() {
        val marker = GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""", """{"marker-color":"#00ff78","marker-size":"large"}""")).entities.single() as DrawnMarker
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), marker.style)
        assertEquals(24f, marker.size)
    }

    @Test
    fun aBadColourIsAWarningAndTheDefaultIsUsed() {
        val result = GeoJson.decode(feature("""{"type":"Polygon","coordinates":[$square]}""", """{"fill":"red"}"""))
        assertTrue(result.warnings.single().contains("fill") && result.warnings.single().contains("red"), result.warnings.toString())
        assertEquals(ColorRgba(0x55, 0x55, 0x55, 153), ((result.entities.single() as DrawnPolygon).style as ShapeStyle.SolidColor).color)
    }

    @Test
    fun theOrderOfTheFeaturesIsTheOrderOfTheShapes() {
        val result = GeoJson.decode(collection(
            feature("""{"type":"Point","coordinates":[1,2]}""", id = "\"c\""),
            feature("""{"type":"Point","coordinates":[1,2]}""", id = "\"a\""),
            feature("""{"type":"Point","coordinates":[1,2]}""", id = "\"b\""),
        ))
        assertEquals(listOf("c", "a", "b"), result.entities.map { it.id.id })
    }

    @Test
    fun whatCannotBeUsedIsLeftOutWithAWarningAndTheRestIsKept() {
        val result = GeoJson.decode(collection(
            """{"type":"Feature","properties":{},"geometry":null}""",
            feature("""{"type":"Point","coordinates":[1,200]}"""),
            feature("""{"type":"Polygon","coordinates":[[[0,0],[1,1],[0,0]]]}"""),
            feature("""{"type":"LineString","coordinates":[[0,0]]}"""),
            feature("""{"type":"Triangle","coordinates":[]}"""),
            feature("""{"type":"Point","coordinates":[7,8]}""", id = "\"ok\""),
        ))
        assertEquals(listOf("ok"), result.entities.map { it.id.id })
        assertEquals(5, result.warnings.size, result.warnings.toString())
        assertTrue(result.warnings.any { "feature 1" in it && "no geometry" in it })
        assertTrue(result.warnings.any { "feature 2" in it })
    }

    @Test
    fun aBadRingIsLeftOutButTheRestOfThePolygonStays() {
        val result = GeoJson.decode(feature("""{"type":"Polygon","coordinates":[$square,[[1,1],[2,2],[1,1]]]}"""))
        assertEquals(1, (result.entities.single() as DrawnPolygon).rings.size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun aPolygonWhoseOuterRingIsUnusableButHasUsableHolesKeepsTheOnesThatAreUsable() {
        // The first usable ring is taken as the outline, as the model's rings have no other order: what matters is that nothing is invented.
        val result = GeoJson.decode(feature("""{"type":"Polygon","coordinates":[[[0,0],[1,1],[0,0]],$square]}"""))
        assertEquals(1, (result.entities.single() as DrawnPolygon).rings.size)
    }

    @Test
    fun zIndexIsReadFromItsProperty() {
        val shape = GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""", """{"zIndex":4}""")).entities.single()
        assertEquals(4, shape.zIndex)
    }

    @Test
    fun textThatIsNotJsonIsRefused() {
        assertTrue("not JSON" in assertFailsWith<DrawFormatException> { GeoJson.decode("{") }.message.orEmpty())
        assertFailsWith<DrawFormatException> { GeoJson.decode("hello") }
        assertFailsWith<DrawFormatException> { GeoJson.decode("") }
    }

    @Test
    fun jsonThatIsNotGeoJsonIsRefused() {
        assertTrue("type" in assertFailsWith<DrawFormatException> { GeoJson.decode("""{"hello":1}""") }.message.orEmpty())
        assertFailsWith<DrawFormatException> { GeoJson.decode("[1,2]") }
        assertFailsWith<DrawFormatException> { GeoJson.decode("""{"type":"FeatureCollection"}""") }
        assertFailsWith<DrawFormatException> { GeoJson.decode("""{"type":"Topology"}""") }
    }

    @Test
    fun aDocumentOfThisLibrarysOwnFormatIsNotGeoJson() {
        assertFailsWith<DrawFormatException> { GeoJson.decode(DrawJson.encode(emptyList())) }
    }

    // ---- writing ----

    private fun polygon(id: String = "p", vararg rings: List<Vertex>, style: ShapeStyle = ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128))) =
        DrawnPolygon(EntityId(id), style, rings.first(), extraRings = rings.drop(1))

    @Test
    fun aPolygonIsWrittenClosedAndCounterClockwise() {
        // given clockwise: (0,0) north to (0,10), east to (10,10), south to (10,0)
        val clockwise = listOf(v("a", 0.0, 0.0), v("b", 10.0, 0.0), v("c", 10.0, 10.0), v("d", 0.0, 10.0))
        val feature = encoded(polygon("p", clockwise))
        assertEquals("Polygon", feature["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val points = ring(coordinates(feature)[0])
        assertEquals(5, points.size)
        assertEquals(points.first(), points.last())
        // counter-clockwise: the signed area is positive
        val area = points.zipWithNext().sumOf { (a, b) -> a.first * b.second - b.first * a.second } / 2
        assertTrue(area > 0, "area $area")
    }

    @Test
    fun aHoleIsWrittenClockwise() {
        val outer = listOf(v("a", 0.0, 0.0), v("b", 0.0, 10.0), v("c", 10.0, 10.0), v("d", 10.0, 0.0))
        val hole = listOf(v("e", 2.0, 2.0), v("f", 2.0, 4.0), v("g", 4.0, 4.0), v("h", 4.0, 2.0)) // counter-clockwise on purpose
        val rings = coordinates(encoded(polygon("p", outer, hole))).map { ring(it) }
        fun area(points: List<Pair<Double, Double>>) = points.zipWithNext().sumOf { (a, b) -> a.first * b.second - b.first * a.second } / 2
        assertEquals(2, rings.size)
        assertTrue(area(rings[0]) > 0)
        assertTrue(area(rings[1]) < 0)
    }

    @Test
    fun separatePartsAreAMultiPolygonAndAnIslandInALakeIsAPartOfItsOwn() {
        val country = listOf(v("a", 0.0, 0.0), v("b", 0.0, 80.0), v("c", 80.0, 80.0), v("d", 80.0, 0.0))
        val lake = listOf(v("e", 10.0, 10.0), v("f", 10.0, 70.0), v("g", 70.0, 70.0), v("h", 70.0, 10.0))
        val island = listOf(v("i", 30.0, 30.0), v("j", 30.0, 50.0), v("k", 50.0, 50.0), v("l", 50.0, 30.0))
        val feature = encoded(polygon("p", country, lake, island))
        assertEquals("MultiPolygon", feature["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val parts = coordinates(feature)
        assertEquals(2, parts.size)
        assertEquals(2, parts[0].jsonArray.size) // country with the lake as a hole
        assertEquals(1, parts[1].jsonArray.size) // the island
    }

    @Test
    fun altitudeIsWrittenOnlyWhenThereIsSome() {
        val marker = DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0))
        assertEquals(2, coordinates(encoded(marker)).size)
        val high = marker.copy(position = GeoPosition(1.0, 2.0, 30.0))
        assertEquals(3, coordinates(encoded(high)).size)
    }

    @Test
    fun theColoursAreWrittenAsSimplestyleWithTheAlphaAsOpacity() {
        val shape = DrawnPolygon(
            EntityId("p"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128)),
            listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)),
            outlineStyle = ShapeStyle.SolidColor(ColorRgba(255, 255, 255)), outlineWidth = 4f,
            description = "note", zIndex = 2,
        )
        val properties = encoded(shape)["properties"]!!.jsonObject
        assertEquals("#FF0000", properties["fill"]!!.jsonPrimitive.content)
        assertEquals(0.502, properties["fill-opacity"]!!.jsonPrimitive.doubleOrNull)
        assertEquals("#FFFFFF", properties["stroke"]!!.jsonPrimitive.content)
        assertNull(properties["stroke-opacity"])
        assertEquals(4.0, properties["stroke-width"]!!.jsonPrimitive.doubleOrNull)
        assertEquals("note", properties["description"]!!.jsonPrimitive.content)
        assertEquals(2.0, properties["zIndex"]!!.jsonPrimitive.doubleOrNull)
    }

    @Test
    fun aShapeFilledWithAPictureHasNoFill() {
        val shape = DrawnPolygon(
            EntityId("p"), ShapeStyle.ImageFill(ImageRef.Url("https://e.com/a.png")),
            listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)),
        )
        assertFalse("fill" in encoded(shape)["properties"]!!.jsonObject.keys)
    }

    @Test
    fun theFeatureIdIsTheShapeId() {
        assertEquals("tr", encoded(polygon("tr", listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0))))["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun whatIsWrittenReadsBackAsTheSameGeometryAndLook() {
        val outer = listOf(v("a", 0.0, 0.0), v("b", 0.0, 10.0), v("c", 10.0, 10.0), v("d", 10.0, 0.0))
        val hole = listOf(v("e", 2.0, 2.0), v("f", 2.0, 4.0), v("g", 4.0, 4.0), v("h", 4.0, 2.0))
        val before = listOf(
            DrawnPolygon(EntityId("p"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128)), outer, ShapeStyle.SolidColor(ColorRgba(0, 0, 255)), 3f, listOf(hole), "a note", 5),
            DrawnPolyline(EntityId("l"), ShapeStyle.SolidColor(ColorRgba(255, 220, 0)), listOf(v("x", 1.0, 1.0), v("y", 2.0, 3.0)), 5f),
            DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), GeoPosition(39.93, 32.86, 850.0), size = 16f),
        )
        val after = GeoJson.decode(GeoJson.encode(before)).entities
        assertEquals(listOf("p", "l", "m"), after.map { it.id.id })
        val polygon = after[0] as DrawnPolygon
        assertEquals(before[0].style.let { (it as ShapeStyle.SolidColor).color.copy(alpha = 128) }, ((polygon.style) as ShapeStyle.SolidColor).color)
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0, 0, 255)), polygon.outlineStyle)
        assertEquals(3f, polygon.outlineWidth)
        assertEquals("a note", polygon.description)
        assertEquals(5, polygon.zIndex)
        assertEquals(2, polygon.rings.size)
        // the same points, though the writing may have turned a ring round: compare as sets
        assertEquals(outer.map { it.position }.toSet(), polygon.rings[0].map { it.position }.toSet())
        assertEquals(hole.map { it.position }.toSet(), polygon.rings[1].map { it.position }.toSet())
        val line = after[1] as DrawnPolyline
        assertEquals((before[1] as DrawnPolyline).vertices.map { it.position }, line.vertices.map { it.position })
        assertEquals(GeoPosition(39.93, 32.86, 850.0), (after[2] as DrawnMarker).position)
    }

    @Test
    fun noShapesIsAnEmptyCollection() {
        assertTrue(GeoJson.decode(GeoJson.encode(emptyList())).entities.isEmpty())
        assertEquals(0, Json.parseToJsonElement(GeoJson.encode(emptyList())).jsonObject["features"]!!.jsonArray.size)
    }

    @Test
    fun theReadableFormHasTheSameContent() {
        val shapes = listOf(polygon("p", listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0))))
        assertEquals(Json.parseToJsonElement(GeoJson.encode(shapes)), Json.parseToJsonElement(GeoJson.encode(shapes, pretty = true)))
    }

    // ---- properties ----

    @Test
    fun thePropertiesOfAFeatureAreKeptAsText() {
        val shape = GeoJson.decode(feature(
            """{"type":"Point","coordinates":[1,2]}""",
            """{"name":"Ankara","population":5663322,"area":3.50,"capital":true,"note":null,"tags":["a","b"],"extra":{"k":1}}""",
        )).entities.single()
        assertEquals(
            mapOf("name" to "Ankara", "population" to "5663322", "area" to "3.50", "capital" to "true", "tags" to """["a","b"]""", "extra" to """{"k":1}"""),
            shape.properties,
        )
    }

    @Test
    fun whatTheLibraryTakesOutOfThePropertiesIsNotKeptTwice() {
        val shape = GeoJson.decode(feature(
            """{"type":"Polygon","coordinates":[$square]}""",
            """{"fill":"#ff0000","fill-opacity":0.5,"stroke":"#0f0","stroke-width":3,"stroke-opacity":1,"marker-color":"#000","marker-size":"large","zIndex":4,"description":"a lake","kept":"yes"}""",
        )).entities.single()
        assertEquals(mapOf("kept" to "yes"), shape.properties)
        assertEquals("a lake", shape.description)
    }

    @Test
    fun aDescriptionThatIsNotTextIsNotUsedAndSoIsKept() {
        val shape = GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""", """{"description":42}""")).entities.single()
        assertNull(shape.description)
        assertEquals(mapOf("description" to "42"), shape.properties)
    }

    @Test
    fun everyShapeOfAMultiGeometryGetsTheFeaturesProperties() {
        val result = GeoJson.decode(feature("""{"type":"MultiPoint","coordinates":[[1,1],[2,2]]}""", """{"year":"1914"}"""))
        assertEquals(listOf(mapOf("year" to "1914"), mapOf("year" to "1914")), result.entities.map { it.properties })
    }

    @Test
    fun propertiesAreWrittenAndReadBack() {
        val shape = DrawnMarker(
            EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0),
            description = "d", zIndex = 2, properties = mapOf("name" to "Ankara", "population" to "5663322", "empty" to ""),
        )
        val properties = encoded(shape)["properties"]!!.jsonObject
        assertEquals("Ankara", properties["name"]!!.jsonPrimitive.content)
        assertEquals("5663322", properties["population"]!!.jsonPrimitive.content)
        assertEquals(mapOf("name" to "Ankara", "population" to "5663322", "empty" to ""), GeoJson.decode(GeoJson.encode(listOf(shape))).entities.single().properties)
    }

    @Test
    fun aPropertyCalledLikeAWrittenKeyDoesNotOverwriteIt() {
        val shape = DrawnPolygon(
            EntityId("p"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0)), listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)),
            description = "the real one", zIndex = 3, properties = mapOf("fill" to "#000000", "description" to "sneaky", "zIndex" to "99", "ok" to "1"),
        )
        val properties = encoded(shape)["properties"]!!.jsonObject
        assertEquals("#FF0000", properties["fill"]!!.jsonPrimitive.content)
        assertEquals("the real one", properties["description"]!!.jsonPrimitive.content)
        assertEquals(3.0, properties["zIndex"]!!.jsonPrimitive.doubleOrNull)
        assertEquals("1", properties["ok"]!!.jsonPrimitive.content)
    }

    // ---- what was left out ----

    @Test
    fun theCountOfWhatWasLeftOutIsByKind() {
        val result = GeoJson.decode(collection(
            """{"type":"Feature","properties":{},"geometry":null}""",
            """{"type":"Feature","properties":{}}""",
            feature("""{"type":"Triangle","coordinates":[]}"""),
            feature("""{"type":"Triangle","coordinates":[]}"""),
            feature("""{"type":"LineString","coordinates":[[0,0]]}"""),
            feature("""{"type":"Point","coordinates":[1,200]}"""),
            feature("""{"type":"Point","coordinates":[7,8]}"""),
        ))
        assertEquals(1, result.entities.size)
        assertEquals(2, result.leftOut["feature without geometry"])
        assertEquals(2, result.leftOut["geometry type Triangle"])
        assertEquals(1, result.leftOut["line of fewer than 2 points"])
        assertEquals(1, result.leftOut["unusable geometry"])
    }

    @Test
    fun aGuessedColourIsAWarningButNotLeftOut() {
        val result = GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""", """{"marker-color":"red"}"""))
        assertEquals(1, result.warnings.size)
        assertTrue(result.leftOut.isEmpty())
    }

    @Test
    fun nothingLeftOutIsAnEmptyCount() {
        assertTrue(GeoJson.decode(feature("""{"type":"Point","coordinates":[1,2]}""")).leftOut.isEmpty())
    }

    @Test
    fun aPropertyCalledLikeAKeyThatIsNotWrittenForThisShapeIsNotWrittenEither() {
        // no description and no solid fill to write: a property with those names would come back as a description and a colour
        val shape = DrawnPolygon(
            EntityId("p"), ShapeStyle.ImageFill(ImageRef.Url("https://e.com/a.png")), listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)),
            properties = mapOf("fill" to "#00FF00", "description" to "leaked", "stroke-width" to "9", "ok" to "1"),
        )
        val properties = encoded(shape)["properties"]!!.jsonObject
        assertNull(properties["fill"])
        assertNull(properties["description"])
        assertEquals("1", properties["ok"]!!.jsonPrimitive.content)
        assertNull(GeoJson.decode(GeoJson.encode(listOf(shape))).entities.single().description)
    }
}
