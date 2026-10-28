package com.tcsappdev.worldwinddraw.serialization

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmlTest {
    private fun kml(body: String) = """<?xml version="1.0" encoding="UTF-8"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>$body</Document></kml>"""

    private fun placemark(geometry: String, extra: String = "", id: String? = null) =
        "<Placemark${id?.let { " id=\"$it\"" } ?: ""}>$extra$geometry</Placemark>"

    private val square = "0,0 10,0 10,10 0,10 0,0"
    private val inner = "2,2 2,4 4,4 4,2 2,2"

    private fun polygon(outer: String = square, vararg holes: String) =
        "<Polygon><outerBoundaryIs><LinearRing><coordinates>$outer</coordinates></LinearRing></outerBoundaryIs>" +
            holes.joinToString("") { "<innerBoundaryIs><LinearRing><coordinates>$it</coordinates></LinearRing></innerBoundaryIs>" } + "</Polygon>"

    private fun v(id: String, lat: Double, lon: Double) = Vertex(VertexId(id), GeoPosition(lat, lon))

    private fun solid(entity: DrawableEntity) = (entity.style as ShapeStyle.SolidColor).color

    // ---- colours ----

    @Test
    fun kmlColoursAreAlphaBlueGreenRed() {
        assertEquals(ColorRgba(0, 128, 255, 128), kmlColor("80ff8000"))
        assertEquals(ColorRgba(255, 0, 0), kmlColor("ff0000ff"))
        assertEquals(ColorRgba(1, 2, 3, 4), kmlColor("04030201"))
        assertEquals("ff0000ff", kmlHex(ColorRgba(255, 0, 0)))
        assertEquals("80ff8000", kmlHex(ColorRgba(0, 128, 255, 128)))
    }

    @Test
    fun aTextThatIsNotAKmlColourIsNull() {
        for (bad in listOf("", "ff0000", "#ff0000ff", "ff0000fg", "ff0000ff00", "+f0000ff")) assertNull(kmlColor(bad), bad)
    }

    // ---- reading ----

    @Test
    fun aPolygonWithHolesIsOnePolygonOfAllItsRingsWithoutTheClosingPoints() {
        val result = Kml.decode(kml(placemark(polygon(square, inner, "6,6 6,8 8,8 8,6 6,6"), id = "tr")))
        val shape = result.entities.single() as DrawnPolygon
        assertEquals("tr", shape.id.id)
        assertEquals(listOf(4, 4, 4), shape.rings.map { it.size })
        assertEquals(listOf("tr-0-0", "tr-0-1", "tr-0-2", "tr-0-3"), shape.rings[0].map { it.id.id })
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    @Test
    fun coordinatesAreLongitudeLatitudeAltitude() {
        val shape = Kml.decode(kml(placemark(polygon("30,40,5 31,40,5 31,41,5 30,40,5")))).entities.single() as DrawnPolygon
        assertEquals(GeoPosition(latitude = 40.0, longitude = 30.0, altitude = 5.0), shape.vertices[0].position)
    }

    @Test
    fun coordinatesMayBeSpreadOverLinesAndTabs() {
        val shape = Kml.decode(kml(placemark("<LineString><coordinates>\n  0,0\n\t1,1,3\r\n  2,0 </coordinates></LineString>"))).entities.single() as DrawnPolyline
        assertEquals(3, shape.vertices.size)
        assertEquals(3.0, shape.vertices[1].position.altitude)
    }

    @Test
    fun aPointIsAMarkerWhoseNameIsItsLabelAndWhoseDescriptionIsItsDescription() {
        val marker = Kml.decode(kml(placemark(
            "<Point><coordinates>32.86,39.93,850</coordinates></Point>",
            "<name>Ankara</name><description>capital</description>", id = "m",
        ))).entities.single() as DrawnMarker
        assertEquals(GeoPosition(39.93, 32.86, 850.0), marker.position)
        assertEquals(MarkerLabel("Ankara"), marker.label)
        assertEquals("capital", marker.description)
    }

    @Test
    fun aPolygonOrLineUsesItsNameWhenItHasNoDescription() {
        val result = Kml.decode(kml(
            placemark(polygon(), "<name>Turkey</name>") +
                placemark("<LineString><coordinates>0,0 1,1</coordinates></LineString>", "<name>Road</name><description>old road</description>"),
        ))
        assertEquals(listOf("Turkey", "old road"), result.entities.map { it.description })
    }

    @Test
    fun placemarksAreFoundInFoldersAtAnyDepthInOrder() {
        val point = { n: String -> placemark("<Point><coordinates>1,2</coordinates></Point>", id = n) }
        val result = Kml.decode(kml(point("a") + "<Folder>${point("b")}<Folder>${point("c")}</Folder>${point("d")}</Folder>" + point("e")))
        assertEquals(listOf("a", "b", "c", "d", "e"), result.entities.map { it.id.id })
    }

    @Test
    fun aMultiGeometryIsOnePolygonOfAllItsPolygonsAndAShapeForEachPointAndLine() {
        val body = placemark(
            "<MultiGeometry>" + polygon(square, inner) + "<Point><coordinates>1,2</coordinates></Point>" +
                polygon("20,20 30,20 30,30 20,20") + "<LineString><coordinates>0,0 1,1</coordinates></LineString></MultiGeometry>",
            id = "mg",
        )
        val result = Kml.decode(kml(body))
        assertEquals(3, result.entities.size)
        val polygon = result.entities.filterIsInstance<DrawnPolygon>().single()
        assertEquals(listOf(4, 4, 3), polygon.rings.map { it.size })
        assertEquals(1, result.entities.filterIsInstance<DrawnMarker>().size)
        assertEquals(1, result.entities.filterIsInstance<DrawnPolyline>().size)
        assertEquals(3, result.entities.map { it.id.id }.toSet().size)
    }

    @Test
    fun aPlacemarkWithoutAnIdGetsOneFromItsPlace() {
        val point = placemark("<Point><coordinates>1,2</coordinates></Point>")
        assertEquals(listOf("placemark-1", "placemark-2"), Kml.decode(kml(point + point)).entities.map { it.id.id })
    }

    @Test
    fun idsThatRepeatAreMadeUnique() {
        val point = placemark("<Point><coordinates>1,2</coordinates></Point>", id = "x")
        assertEquals(3, Kml.decode(kml(point + point + point)).entities.map { it.id.id }.toSet().size)
    }

    @Test
    fun theLookOfAPolygonIsItsPolyStyleAndLineStyle() {
        val style = "<Style><PolyStyle><color>800000ff</color></PolyStyle><LineStyle><color>ffff0000</color><width>3</width></LineStyle></Style>"
        val shape = Kml.decode(kml(placemark(polygon(), style))).entities.single() as DrawnPolygon
        assertEquals(ColorRgba(255, 0, 0, 128), solid(shape))
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0, 0, 255)), shape.outlineStyle)
        assertEquals(3f, shape.outlineWidth)
    }

    @Test
    fun missingStyleIsKmlsDefaultWhiteAndAWidthOfOne() {
        val shape = Kml.decode(kml(placemark(polygon()))).entities.single() as DrawnPolygon
        assertEquals(ColorRgba(255, 255, 255), solid(shape))
        assertEquals(ShapeStyle.SolidColor(ColorRgba(255, 255, 255)), shape.outlineStyle)
        assertEquals(1f, shape.outlineWidth)
    }

    @Test
    fun fillZeroAndOutlineZeroMakeThoseInvisible() {
        val shape = Kml.decode(kml(placemark(polygon(), "<Style><PolyStyle><fill>0</fill><outline>0</outline></PolyStyle></Style>"))).entities.single() as DrawnPolygon
        assertEquals(0, solid(shape).alpha)
        assertEquals(0, (shape.outlineStyle as ShapeStyle.SolidColor).color.alpha)
    }

    @Test
    fun aLineTakesItsColourAndWidthFromLineStyle() {
        val line = Kml.decode(kml(placemark(
            "<LineString><coordinates>0,0 1,1</coordinates></LineString>",
            "<Style><LineStyle><color>ff00dcff</color><width>5</width></LineStyle></Style>",
        ))).entities.single() as DrawnPolyline
        assertEquals(ColorRgba(255, 220, 0), solid(line))
        assertEquals(5f, line.lineWidth)
    }

    @Test
    fun aMarkerTakesItsColourAndSizeFromIconStyle() {
        val marker = Kml.decode(kml(placemark(
            "<Point><coordinates>1,2</coordinates></Point>",
            "<Style><IconStyle><color>ff78ff00</color><scale>2</scale></IconStyle></Style>",
        ))).entities.single() as DrawnMarker
        assertEquals(ColorRgba(0, 255, 120), solid(marker))
        assertEquals(32f, marker.size)
    }

    @Test
    fun aWebIconIsTheMarkersPictureAndAnyOtherIconIsAWarning() {
        val web = Kml.decode(kml(placemark(
            "<Point><coordinates>1,2</coordinates></Point>",
            "<Style><IconStyle><Icon><href>https://e.com/pin.png</href></Icon></IconStyle></Style>",
        ))).entities.single() as DrawnMarker
        assertEquals(ShapeStyle.ImagePattern(ImageRef.Url("https://e.com/pin.png")), web.style)
        val local = Kml.decode(kml(placemark(
            "<Point><coordinates>1,2</coordinates></Point>",
            "<Style><IconStyle><Icon><href>files/pin.png</href></Icon></IconStyle></Style>",
        )))
        assertTrue(local.entities.single().style is ShapeStyle.SolidColor)
        assertTrue(local.warnings.single().contains("files/pin.png"))
    }

    @Test
    fun aSharedStyleIsFoundByItsIdEvenWhenItComesAfterThePlacemark() {
        val result = Kml.decode(kml(
            placemark(polygon(), "<styleUrl>#red</styleUrl>") +
                "<Style id=\"red\"><PolyStyle><color>ff0000ff</color></PolyStyle></Style>",
        ))
        assertEquals(ColorRgba(255, 0, 0), solid(result.entities.single()))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun aStyleMapUsesItsNormalPair() {
        val map = """<StyleMap id="m"><Pair><key>highlight</key><styleUrl>#hi</styleUrl></Pair><Pair><key>normal</key><styleUrl>#lo</styleUrl></Pair></StyleMap>
            <Style id="lo"><PolyStyle><color>ff00ff00</color></PolyStyle></Style><Style id="hi"><PolyStyle><color>ff0000ff</color></PolyStyle></Style>"""
        val result = Kml.decode(kml(map + placemark(polygon(), "<styleUrl>#m</styleUrl>")))
        assertEquals(ColorRgba(0, 255, 0), solid(result.entities.single()))
    }

    @Test
    fun aStyleThatIsNotDefinedOrIsInAnotherFileIsAWarningAndTheDefaultIsUsed() {
        val result = Kml.decode(kml(
            placemark(polygon(), "<styleUrl>#nope</styleUrl>") + placemark(polygon(), "<styleUrl>other.kml#x</styleUrl>"),
        ))
        assertEquals(2, result.entities.size)
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings[0].contains("#nope") && result.warnings[1].contains("other.kml#x"), result.warnings.toString())
    }

    @Test
    fun anInlineStyleBeatsASharedOne() {
        val result = Kml.decode(kml(
            "<Style id=\"s\"><PolyStyle><color>ff0000ff</color></PolyStyle></Style>" +
                placemark(polygon(), "<styleUrl>#s</styleUrl><Style><PolyStyle><color>ff00ff00</color></PolyStyle></Style>"),
        ))
        assertEquals(ColorRgba(0, 255, 0), solid(result.entities.single()))
    }

    @Test
    fun zIndexComesFromExtendedData() {
        val shape = Kml.decode(kml(placemark(
            polygon(), "<ExtendedData><Data name=\"other\"><value>9</value></Data><Data name=\"zIndex\"><value>4</value></Data></ExtendedData>",
        ))).entities.single()
        assertEquals(4, shape.zIndex)
    }

    @Test
    fun descriptionsInCdataAndEntitiesAreDecoded() {
        val result = Kml.decode(kml(
            placemark(polygon(), "<description><![CDATA[<b>bold</b> & more]]></description>") +
                placemark("<Point><coordinates>1,2</coordinates></Point>", "<name>Tom &amp; Jerry</name>"),
        ))
        assertEquals("<b>bold</b> & more", result.entities[0].description)
        assertEquals("Tom & Jerry", (result.entities[1] as DrawnMarker).label!!.text)
    }

    @Test
    fun overlaysAndLinksAreLeftOutWithAWarningAndTheRestIsKept() {
        val result = Kml.decode(kml(
            "<GroundOverlay><name>map</name></GroundOverlay><NetworkLink><Link><href>x</href></Link></NetworkLink>" +
                placemark("<Point><coordinates>1,2</coordinates></Point>", id = "ok"),
        ))
        assertEquals(listOf("ok"), result.entities.map { it.id.id })
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.any { "GroundOverlay" in it })
    }

    @Test
    fun whatCannotBeUsedIsLeftOutWithAWarning() {
        val result = Kml.decode(kml(
            placemark("", "<name>no geometry</name>") +
                placemark("<Point><coordinates>1,200</coordinates></Point>") +
                placemark("<LineString><coordinates>0,0</coordinates></LineString>") +
                placemark("<Point><coordinates>abc,def</coordinates></Point>") +
                placemark(polygon("0,0 1,1 0,0")) +
                placemark("<Point><coordinates>7,8</coordinates></Point>", id = "ok"),
        ))
        assertEquals(listOf("ok"), result.entities.map { it.id.id })
        // every placemark but the last is named once or more; the last one, which is fine, is not
        assertEquals(setOf(1, 2, 3, 4, 5), result.warnings.map { it.substringAfter("placemark ").substringBefore(":").toInt() }.toSet())
    }

    @Test
    fun aBadRingIsLeftOutButTheRestOfThePolygonStays() {
        val result = Kml.decode(kml(placemark(polygon(square, "1,1 2,2 1,1"))))
        assertEquals(1, (result.entities.single() as DrawnPolygon).rings.size)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun aBarePlacemarkOrDocumentOrFolderIsAcceptedAsTheRoot() {
        val point = placemark("<Point><coordinates>1,2</coordinates></Point>")
        assertEquals(1, Kml.decode(point).entities.size)
        assertEquals(1, Kml.decode("<Document>$point</Document>").entities.size)
        assertEquals(1, Kml.decode("<Folder>$point</Folder>").entities.size)
    }

    @Test
    fun namespacePrefixesDoNotMatter() {
        val text = """<kml:kml xmlns:kml="http://www.opengis.net/kml/2.2"><kml:Document><kml:Placemark id="p"><kml:Point><kml:coordinates>1,2</kml:coordinates></kml:Point></kml:Placemark></kml:Document></kml:kml>"""
        assertEquals("p", Kml.decode(text).entities.single().id.id)
    }

    @Test
    fun textThatIsNotXmlIsRefused() {
        assertTrue("not well-formed" in assertFailsWith<DrawFormatException> { Kml.decode("hello") }.message.orEmpty())
        assertFailsWith<DrawFormatException> { Kml.decode("<kml><Document></kml>") }
    }

    @Test
    fun xmlThatIsNotKmlIsRefused() {
        assertTrue("html" in assertFailsWith<DrawFormatException> { Kml.decode("<html><body/></html>") }.message.orEmpty())
        assertFailsWith<DrawFormatException> { Kml.decode("""<gpx version="1.1"/>""") }
    }

    @Test
    fun aGeoJsonOrDrawJsonTextIsNotKml() {
        assertFailsWith<DrawFormatException> { Kml.decode(GeoJson.encode(emptyList())) }
        assertFailsWith<DrawFormatException> { Kml.decode(DrawJson.encode(emptyList())) }
    }

    // ---- writing ----

    private fun written(entity: DrawableEntity, pretty: Boolean = false) = Kml.encode(listOf(entity), pretty)

    private fun roundTrip(entities: List<DrawableEntity>) = Kml.decode(Kml.encode(entities)).entities

    private fun xml(entity: DrawableEntity) = Xml.parse(written(entity))

    private fun placemarkOf(entity: DrawableEntity): XmlNode = xml(entity).child("Document")!!.child("Placemark")!!

    private fun ringPoints(ring: XmlNode): List<Pair<Double, Double>> =
        ring.child("coordinates")!!.text().split(" ").map { c -> c.split(",").let { it[0].toDouble() to it[1].toDouble() } }

    private fun area(points: List<Pair<Double, Double>>) = points.zipWithNext().sumOf { (a, b) -> a.first * b.second - b.first * a.second } / 2

    private fun polygonShape(id: String = "p", vararg rings: List<Vertex>, style: ShapeStyle = ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128))) =
        DrawnPolygon(EntityId(id), style, rings.first(), extraRings = rings.drop(1))

    @Test
    fun theDocumentIsKmlAndPlacemarksCarryTheIds() {
        val root = xml(polygonShape("tr", listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0))))
        assertEquals("kml", root.name)
        assertEquals("tr", root.child("Document")!!.child("Placemark")!!.attributes["id"])
    }

    @Test
    fun aPolygonIsWrittenClosedWithItsOutlineCounterClockwiseAndHolesClockwise() {
        val outer = listOf(v("a", 0.0, 0.0), v("b", 10.0, 0.0), v("c", 10.0, 10.0), v("d", 0.0, 10.0)) // clockwise
        val hole = listOf(v("e", 2.0, 2.0), v("f", 2.0, 4.0), v("g", 4.0, 4.0), v("h", 4.0, 2.0)) // counter-clockwise
        val polygon = placemarkOf(polygonShape("p", outer, hole)).child("Polygon")!!
        val outerPoints = ringPoints(polygon.child("outerBoundaryIs")!!.child("LinearRing")!!)
        val holePoints = ringPoints(polygon.child("innerBoundaryIs")!!.child("LinearRing")!!)
        assertEquals(5, outerPoints.size)
        assertEquals(outerPoints.first(), outerPoints.last())
        assertTrue(area(outerPoints) > 0)
        assertTrue(area(holePoints) < 0)
    }

    @Test
    fun separatePartsAreAMultiGeometryAndAnIslandInALakeIsAPartOfItsOwn() {
        val country = listOf(v("a", 0.0, 0.0), v("b", 0.0, 80.0), v("c", 80.0, 80.0), v("d", 80.0, 0.0))
        val lake = listOf(v("e", 10.0, 10.0), v("f", 10.0, 70.0), v("g", 70.0, 70.0), v("h", 70.0, 10.0))
        val island = listOf(v("i", 30.0, 30.0), v("j", 30.0, 50.0), v("k", 50.0, 50.0), v("l", 50.0, 30.0))
        val parts = placemarkOf(polygonShape("p", country, lake, island)).child("MultiGeometry")!!.children("Polygon")
        assertEquals(2, parts.size)
        assertEquals(1, parts[0].children("innerBoundaryIs").size)
        assertEquals(0, parts[1].children("innerBoundaryIs").size)
    }

    @Test
    fun colourWidthDescriptionAndZIndexAreWritten() {
        val shape = DrawnPolygon(
            EntityId("p"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128)),
            listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)),
            outlineStyle = ShapeStyle.SolidColor(ColorRgba(0, 0, 255)), outlineWidth = 4f, description = "a < b & \"c\"", zIndex = 3,
        )
        val placemark = placemarkOf(shape)
        val style = placemark.child("Style")!!
        assertEquals("800000ff", style.child("PolyStyle")!!.childText("color"))
        assertEquals("ffff0000", style.child("LineStyle")!!.childText("color"))
        assertEquals("4", style.child("LineStyle")!!.childText("width"))
        assertEquals("a < b & \"c\"", placemark.childText("description"))
        assertEquals("3", placemark.child("ExtendedData")!!.child("Data")!!.childText("value"))
    }

    @Test
    fun aShapeFilledWithAPictureHasNoFill() {
        val shape = polygonShape("p", listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)), style = ShapeStyle.ImageFill(ImageRef.Url("https://e.com/a.png")))
        assertEquals("0", placemarkOf(shape).child("Style")!!.child("PolyStyle")!!.childText("fill"))
    }

    @Test
    fun aMarkersLabelIsItsNameAndItsSizeIsItsScale() {
        val marker = DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), GeoPosition(39.93, 32.86), size = 32f, label = MarkerLabel("Ankara"))
        val placemark = placemarkOf(marker)
        assertEquals("Ankara", placemark.childText("name"))
        assertEquals("2", placemark.child("Style")!!.child("IconStyle")!!.childText("scale"))
        assertEquals(listOf(32.86, 39.93), placemark.child("Point")!!.childText("coordinates")!!.split(",").map { it.toDouble() })
    }

    @Test
    fun altitudeIsWrittenOnlyWhenThereIsSome() {
        val marker = DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0, 30.0))
        // compared as numbers: JavaScript writes 30.0 as "30", the JVM as "30.0", and both are the same coordinate
        fun numbers(m: DrawnMarker) = placemarkOf(m).child("Point")!!.childText("coordinates")!!.split(",").map { it.toDouble() }
        assertEquals(listOf(2.0, 1.0, 30.0), numbers(marker))
        assertEquals(listOf(2.0, 1.0), numbers(marker.copy(position = GeoPosition(1.0, 2.0))))
    }

    @Test
    fun aMarkersWebPictureIsWrittenAsItsIcon() {
        val marker = DrawnMarker(EntityId("m"), ShapeStyle.ImagePattern(ImageRef.Url("https://e.com/pin.png")), GeoPosition(1.0, 2.0))
        assertEquals("https://e.com/pin.png", placemarkOf(marker).child("Style")!!.child("IconStyle")!!.child("Icon")!!.childText("href"))
    }

    @Test
    fun anIdWithMarkupInItIsEscaped() {
        val marker = DrawnMarker(EntityId("a\"<b>&"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0))
        assertEquals("a\"<b>&", placemarkOf(marker).attributes["id"])
    }

    @Test
    fun whatIsWrittenReadsBackAsTheSameGeometryAndLook() {
        val outer = listOf(v("a", 0.0, 0.0), v("b", 0.0, 10.0), v("c", 10.0, 10.0), v("d", 10.0, 0.0))
        val hole = listOf(v("e", 2.0, 2.0), v("f", 2.0, 4.0), v("g", 4.0, 4.0), v("h", 4.0, 2.0))
        val before = listOf(
            DrawnPolygon(EntityId("p"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128)), outer, ShapeStyle.SolidColor(ColorRgba(0, 0, 255)), 3f, listOf(hole), "a note", 5),
            DrawnPolyline(EntityId("l"), ShapeStyle.SolidColor(ColorRgba(255, 220, 0)), listOf(v("x", 1.0, 1.0), v("y", 2.0, 3.0)), 5f, "a road"),
            DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), GeoPosition(39.93, 32.86, 850.0), size = 32f, label = MarkerLabel("Ankara"), description = "capital", zIndex = -1),
            DrawnMarker(EntityId("pin"), ShapeStyle.ImagePattern(ImageRef.Url("https://e.com/pin.png")), GeoPosition(1.0, 2.0)),
        )
        val after = roundTrip(before)
        assertEquals(listOf("p", "l", "m", "pin"), after.map { it.id.id })
        val polygon = after[0] as DrawnPolygon
        assertEquals(before[0].style, polygon.style)
        assertEquals(before[0].let { (it as DrawnPolygon).outlineStyle }, polygon.outlineStyle)
        assertEquals(3f, polygon.outlineWidth)
        assertEquals("a note", polygon.description)
        assertEquals(5, polygon.zIndex)
        assertEquals(outer.map { it.position }.toSet(), polygon.rings[0].map { it.position }.toSet())
        assertEquals(hole.map { it.position }.toSet(), polygon.rings[1].map { it.position }.toSet())
        val line = after[1] as DrawnPolyline
        assertEquals((before[1] as DrawnPolyline).vertices.map { it.position }, line.vertices.map { it.position })
        assertEquals(before[1].style, line.style)
        assertEquals(5f, line.lineWidth)
        assertEquals("a road", line.description)
        val marker = after[2] as DrawnMarker
        assertEquals((before[2] as DrawnMarker).position, marker.position)
        assertEquals(before[2].style, marker.style)
        assertEquals(32f, marker.size)
        assertEquals(MarkerLabel("Ankara"), marker.label)
        assertEquals("capital", marker.description)
        assertEquals(-1, marker.zIndex)
        assertEquals(before[3].style, after[3].style)
    }

    @Test
    fun descriptionsWithMarkupSurviveTheTrip() {
        val text = "<b>x</b> & \"y\" 'z' ]]> é ü 日本"
        val marker = DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0), description = text)
        assertEquals(text, roundTrip(listOf(marker)).single().description)
    }

    @Test
    fun noShapesIsAnEmptyDocument() {
        assertTrue(Kml.decode(Kml.encode(emptyList())).entities.isEmpty())
        assertEquals(0, xml(DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0))).let { Xml.parse(Kml.encode(emptyList())).child("Document")!!.children.size })
    }

    @Test
    fun theReadableFormHasTheSameContent() {
        val shapes = listOf(
            polygonShape("p", listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0))),
            DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0), label = MarkerLabel("n"), description = "d"),
        )
        fun dump(node: XmlNode): String = node.name + node.attributes + node.text() + node.children.joinToString("", "[", "]") { dump(it) }
        assertEquals(dump(Xml.parse(Kml.encode(shapes))), dump(Xml.parse(Kml.encode(shapes, pretty = true))))
        assertTrue('\n' in Kml.encode(shapes, pretty = true))
        assertTrue('\n' !in Kml.encode(shapes).substringAfter("?>"))
    }

    // ---- properties, time, folders, the label's look ----

    @Test
    fun extendedDataAndSchemaDataAreTheProperties() {
        val extended = """<ExtendedData>
            <Data name="population"><value>5000000</value></Data>
            <Data name="zIndex"><value>2</value></Data>
            <Data name="empty"><value></value></Data>
            <SchemaData schemaUrl="#s"><SimpleData name="code">TR</SimpleData></SchemaData>
        </ExtendedData>"""
        val shape = Kml.decode(kml(placemark(polygon(), extended))).entities.single()
        assertEquals(mapOf("population" to "5000000", "empty" to "", "code" to "TR"), shape.properties)
        assertEquals(2, shape.zIndex)
    }

    @Test
    fun theNameIsAPropertyOnEveryKindOfShapeAndAMarkersLabelToo() {
        val result = Kml.decode(kml(
            placemark(polygon(), "<name>Turkey</name>") +
                placemark("<Point><coordinates>1,2</coordinates></Point>", "<name>Ankara</name>"),
        ))
        assertEquals(listOf("Turkey", "Ankara"), result.entities.map { it.properties["name"] })
        assertEquals("Ankara", (result.entities[1] as DrawnMarker).label!!.text)
    }

    @Test
    fun theTimeAPlacemarkHoldsForIsKeptInTheWordsKmlHasIt() {
        val result = Kml.decode(kml(
            placemark(polygon(), "<TimeSpan><begin>1914-07-28</begin><end>1918-11-11</end></TimeSpan>") +
                placemark(polygon(), "<TimeSpan><begin>1914</begin></TimeSpan>") +
                placemark(polygon(), "<TimeStamp><when>1919-06-28T12:00:00Z</when></TimeStamp>"),
        ))
        assertEquals(mapOf("timeBegin" to "1914-07-28", "timeEnd" to "1918-11-11"), result.entities[0].properties)
        assertEquals(mapOf("timeBegin" to "1914"), result.entities[1].properties)
        assertEquals(mapOf("timeStamp" to "1919-06-28T12:00:00Z"), result.entities[2].properties)
    }

    @Test
    fun theFoldersAPlacemarkLiesInAreItsFolderPropertyOutermostFirst() {
        val point = placemark("<Point><coordinates>1,2</coordinates></Point>")
        val result = Kml.decode(kml(
            "<name>the file</name>" + point +
                "<Folder><name>Europe</name>$point<Folder><name>Balkans</name>$point</Folder>$point</Folder><Folder>$point</Folder>",
        ))
        assertEquals(listOf(null, "Europe", "Europe / Balkans", "Europe", null), result.entities.map { it.properties["folder"] })
    }

    @Test
    fun aLabelTakesItsColourAndSizeFromLabelStyle() {
        fun label(style: String) = (Kml.decode(kml(placemark("<Point><coordinates>1,2</coordinates></Point>", "<name>x</name><Style>$style</Style>"))).entities.single() as DrawnMarker).label!!
        assertEquals(MarkerLabel("x", ColorRgba(255, 0, 0, 128), 28), label("<LabelStyle><color>800000ff</color><scale>2</scale></LabelStyle>"))
        assertEquals(MarkerLabel("x"), label(""))
        assertEquals(96, label("<LabelStyle><scale>100</scale></LabelStyle>").size)
        assertEquals(6, label("<LabelStyle><scale>0.1</scale></LabelStyle>").size)
    }

    @Test
    fun propertiesTimeAndFoldersAreWrittenAndReadBack() {
        val shape = DrawnPolygon(
            EntityId("p"), ShapeStyle.SolidColor(ColorRgba(255, 0, 0)), listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0)),
            zIndex = 2,
            properties = mapOf(
                "name" to "Ottoman Empire", "population" to "5663322", "folder" to "Europe / Balkans", "timeBegin" to "1914-07-28",
                "timeEnd" to "1918-11-11", "markup" to "<a> & \"b\"", "empty" to "",
            ),
        )
        val back = roundTrip(listOf(shape)).single()
        assertEquals(shape.properties, back.properties)
        assertEquals(2, back.zIndex)
    }

    @Test
    fun aTimeStampIsWrittenAsOne() {
        val shape = DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(1, 2, 3)), GeoPosition(1.0, 2.0), properties = mapOf("timeStamp" to "1919-06-28"))
        val placemark = placemarkOf(shape)
        assertEquals("1919-06-28", placemark.child("TimeStamp")!!.childText("when"))
        assertNull(placemark.child("ExtendedData"))
    }

    @Test
    fun whatKmlHasElementsForIsNotWrittenAsExtendedDataToo() {
        val shape = polygonShape("p", listOf(v("a", 0.0, 0.0), v("b", 0.0, 1.0), v("c", 1.0, 1.0))).copy(
            zIndex = 3, properties = mapOf("name" to "N", "timeBegin" to "1914", "zIndex" to "99", "other" to "o"),
        )
        val placemark = placemarkOf(shape)
        assertEquals("N", placemark.childText("name"))
        assertEquals("1914", placemark.child("TimeSpan")!!.childText("begin"))
        val names = placemark.child("ExtendedData")!!.children("Data").map { it.attributes["name"] }
        assertEquals(listOf("zIndex", "other"), names)
        assertEquals("3", placemark.child("ExtendedData")!!.children("Data")[0].childText("value"))
    }

    @Test
    fun aMarkerWithALabelWritesItsLabelsLook() {
        val marker = DrawnMarker(EntityId("m"), ShapeStyle.SolidColor(ColorRgba(0, 255, 120)), GeoPosition(1.0, 2.0), label = MarkerLabel("x", ColorRgba(255, 0, 0, 128), 28))
        val style = placemarkOf(marker).child("Style")!!.child("LabelStyle")!!
        assertEquals("800000ff", style.childText("color"))
        assertEquals("2", style.childText("scale"))
        val back = roundTrip(listOf(marker)).single() as DrawnMarker
        assertEquals(MarkerLabel("x", ColorRgba(255, 0, 0, 128), 28), back.label)
    }

    // ---- what was left out ----

    @Test
    fun theCountOfWhatWasLeftOutIsByKind() {
        val result = Kml.decode(kml(
            "<GroundOverlay/><GroundOverlay/><NetworkLink/>" +
                placemark("", "<name>none</name>") +
                placemark("<Point><coordinates>1,200</coordinates></Point>") +
                placemark("<LineString><coordinates>0,0</coordinates></LineString>") +
                placemark("<Point><coordinates>7,8</coordinates></Point>", id = "ok"),
        ))
        assertEquals(listOf("ok"), result.entities.map { it.id.id })
        assertEquals(2, result.leftOut["GroundOverlay"])
        assertEquals(1, result.leftOut["NetworkLink"])
        assertEquals(1, result.leftOut["placemark without geometry"])
        assertEquals(1, result.leftOut["unusable geometry"])
        assertEquals(1, result.leftOut["line of fewer than 2 points"])
    }

    @Test
    fun aStyleThatIsNotDefinedIsAWarningButNotLeftOut() {
        val result = Kml.decode(kml(placemark(polygon(), "<styleUrl>#nope</styleUrl>")))
        assertEquals(1, result.warnings.size)
        assertTrue(result.leftOut.isEmpty())
    }
}
