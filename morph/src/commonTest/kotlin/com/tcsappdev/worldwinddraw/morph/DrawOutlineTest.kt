package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DrawOutlineTest {
    // Three corners round the equator, 120 degrees apart, so a third of the way is exactly a corner.
    private val ring = listOf(vertex("a", 0.0, 0.0), vertex("b", 0.0, 120.0), vertex("c", 0.0, -120.0))
    private val border = solid(10, 20, 30)
    private val polygon = DrawnPolygon(EntityId("p"), solid(255, 0, 0), ring, outlineStyle = border, outlineWidth = 3f)

    // A plain line, of one colour and with no light: what the rest is added to.
    private val options = DrawOutlineOptions(drawShare = 0.5f, trailShare = 0f, glow = 0f)

    private fun List<DrawableEntity>.lines() = filterIsInstance<DrawnPolyline>()
    private fun List<DrawableEntity>.fills() = filterIsInstance<DrawnPolygon>().filter { it.id.id == "p" }
    private fun ShapeStyle.rgb() = (this as ShapeStyle.SolidColor).color.let { Triple(it.red, it.green, it.blue) }
    private fun ShapeStyle.alpha() = (this as ShapeStyle.SolidColor).color.alpha
    private fun near(a: GeoPosition, b: GeoPosition) = abs(a.latitude - b.latitude) < 1e-9 && abs(a.longitude - b.longitude) < 1e-9

    @Test
    fun atTheEndItIsExactlyThePolygon() {
        assertEquals(listOf<Any>(polygon), DrawOutline.at(polygon, 1f, options))
        assertEquals(listOf<Any>(polygon), DrawOutline.at(polygon, 2f, options))
        assertEquals(listOf<Any>(polygon), DrawOutline.at(polygon, 1f))
    }

    @Test
    fun atTheStartOfAPlainLineNothingIsDrawnYet() {
        assertEquals(emptyList(), DrawOutline.at(polygon, 0f, options))
    }

    @Test
    fun whileTheOutlineIsDrawnThereIsALineToThePenAndNoFill() {
        // drawShare 0.5: at t = 1/6 the outline is a third drawn, which is the second corner (a Float is a hair over a sixth).
        val shapes = DrawOutline.at(polygon, 1f / 6, options)
        val line = shapes.lines().single()
        assertEquals(ring[0].position, line.vertices[0].position)
        assertTrue(abs(line.vertices[1].position.longitude - 120.0) < 1e-3)
        assertTrue(abs(line.vertices.last().position.longitude - 120.0) < 1e-3)
        assertEquals(0, shapes.fills().size)
        assertEquals(3f, line.lineWidth)
        assertEquals(border, line.style)
    }

    @Test
    fun theLineOnlyGrows() {
        var before = 0
        for (i in 1..50) {
            val line = DrawOutline.at(polygon, i / 100f, options).lines().single()
            assertTrue(line.vertices.size >= before)
            before = line.vertices.size
        }
    }

    @Test
    fun whenTheOutlineIsDoneTheFillComesIn() {
        val half = DrawOutline.at(polygon, 0.5f, options)
        assertEquals(0, half.fills().size) // the fill has not started: its opacity is 0
        assertEquals(4, half.lines().single().vertices.size) // all three corners and back to the first

        val later = DrawOutline.at(polygon, 0.75f, options)
        val fill = later.fills().single()
        assertEquals(null, fill.outlineStyle)
        assertEquals(ShapeStyle.SolidColor(ColorRgba(255, 0, 0, 128)), fill.style) // half way through the fill: half the opacity
        assertEquals(1, later.lines().size)
    }

    @Test
    fun aPolygonWithoutAnOutlineHasTheDrawnOutlineFadeAsTheFillComes() {
        val plain = polygon.copy(outlineStyle = null)
        assertEquals(ShapeStyle.SolidColor(ColorRgba(0, 0, 0, 128)), DrawOutline.at(plain, 0.75f, options).lines().single().style)
        assertEquals(0, DrawOutline.at(plain, 0.999999f, options).lines().size) // nearly at the end the line is nearly gone
    }

    @Test
    fun everyRingHasItsOwnLine() {
        val hole = listOf(vertex("h1", 0.0, 1.0), vertex("h2", 0.0, 2.0), vertex("h3", 1.0, 1.5))
        val shapes = DrawOutline.at(polygon.copy(extraRings = listOf(hole)), 0.25f, options)
        assertEquals(listOf("p#draw0", "p#draw1"), shapes.lines().map { it.id.id })
    }

    @Test
    fun startingAtACornerStartsTheLineThere() {
        val shapes = DrawOutline.at(polygon, 0.2f, options.copy(startAt = VertexId("b")))
        assertEquals(ring[1].position, shapes.lines().single().vertices.first().position)
    }

    @Test
    fun aStartCornerThatIsInNoRingChangesNothing() {
        assertEquals(DrawOutline.at(polygon, 0.2f, options), DrawOutline.at(polygon, 0.2f, options.copy(startAt = VertexId("nowhere"))))
    }

    @Test
    fun goingTheOtherWayRoundDrawsTheOtherEdgeFirst() {
        val forward = DrawOutline.at(polygon, 0.2f, options).lines().single().vertices.last().position.longitude
        val backward = DrawOutline.at(polygon, 0.2f, options.copy(reversed = true)).lines().single().vertices.last().position.longitude
        assertTrue(forward > 0.0 && backward < 0.0, "forward $forward, backward $backward")
    }

    @Test
    fun whatItDrawsDoesNotChangeThePolygon() {
        val before = polygon.copy()
        DrawOutline.at(polygon, 0.6f, DrawOutlineOptions())
        assertEquals(before, polygon)
    }

    @Test
    fun theLinesVerticesKeepTheirIdsAsTheLineGrows() {
        val short = DrawOutline.at(polygon, 0.1f, options).lines().single().vertices
        val longer = DrawOutline.at(polygon, 0.4f, options).lines().single().vertices
        assertEquals(short.first().id, longer.first().id)
        assertEquals(longer.size, longer.map { it.id }.toSet().size)
    }

    @Test
    fun theLineHasItsOwnColourAndWidthAndTurnsIntoThePolygonsOutlineAsTheFillComes() {
        val orange = options.copy(lineColor = ColorRgba(250, 80, 0), lineWidth = 6f)
        val drawing = DrawOutline.at(polygon, 0.4f, orange).lines()
        assertTrue(drawing.all { it.lineWidth == 6f })
        assertEquals(Triple(250, 80, 0), drawing.single().style.rgb())

        // the polygon's own outline is (10, 20, 30): a little before the end the line is nearly that
        val (r, g, b) = DrawOutline.at(polygon, 0.999f, orange).lines().single().style.rgb()
        assertTrue(r < 20 && g < 30 && b < 40, "was $r $g $b")
    }

    // --- the trail ---

    private val trail = options.copy(trailShare = 0.25f, trailBands = 4, headColor = ColorRgba(255, 200, 200))
    private val red = ShapeStyle.SolidColor(ColorRgba(200, 0, 0))
    private val redPolygon = polygon.copy(outlineStyle = red)

    @Test
    fun theTrailIsLightestAtTheTipAndCoolsToTheOutlinesOwnColourBehindIt() {
        val shapes = DrawOutline.at(redPolygon, 0.4f, trail) // drawn 0.8 of the way round: the trail is a quarter of it
        val bands = shapes.lines().filter { "." in it.id.id }
        assertEquals(4, bands.size)
        val greens = bands.map { (it.style as ShapeStyle.SolidColor).color.green }
        assertEquals(greens.sorted(), greens, "the further along, the lighter")
        assertTrue(greens.first() > 0 && greens.last() < 200)
        assertEquals(Triple(200, 0, 0), shapes.lines().single { it.id.id == "p#draw0" }.style.rgb()) // behind it: the outline's own colour
    }

    @Test
    fun theTrailBandsJoinUpFromTheCooledLine() {
        val pieces = DrawOutline.at(redPolygon, 0.4f, trail).lines().sortedBy { it.id.id }
        pieces.zipWithNext { before, after -> assertTrue(near(before.vertices.last().position, after.vertices.first().position)) }
    }

    @Test
    fun theHeadColourIsALighterOutlineColourWhenNoneIsGiven() {
        val band = DrawOutline.at(redPolygon, 0.2f, trail.copy(headColor = null)).lines().last { "." in it.id.id }
        val (r, g, b) = band.style.rgb()
        assertTrue(r > 200 && g > 0 && b > 0)
    }

    @Test
    fun withNoTrailTheOutlineIsOneLineOfItsOwnColour() {
        val shapes = DrawOutline.at(redPolygon, 0.4f, trail.copy(trailShare = 0f))
        assertEquals(1, shapes.lines().size)
        assertEquals(Triple(200, 0, 0), shapes.lines().single().style.rgb())
    }

    // --- the light ---

    private val neon = DrawOutlineOptions(drawShare = 0.5f, trailShare = 0f, glow = 1f)

    @Test
    fun aLineHasAHaloAWiderBodyAndANarrowerHotCoreAndTheLightIsFainter() {
        val shapes = DrawOutline.at(redPolygon, 0.2f, neon).lines()
        val byId = shapes.associateBy { it.id.id }
        val halo = byId.getValue("p#draw0~h0")
        assertEquals(7, byId.keys.count { "~h" in it })
        val inner = byId.getValue("p#draw0~h6")
        val body = byId.getValue("p#draw0")
        val core = byId.getValue("p#draw0~c")
        assertTrue(halo.lineWidth > inner.lineWidth && inner.lineWidth > body.lineWidth && body.lineWidth > core.lineWidth)
        assertTrue(halo.style.alpha() < inner.style.alpha() && inner.style.alpha() < body.style.alpha())
        assertEquals(255, body.style.alpha())
        val (_, g, _) = core.style.rgb()
        assertTrue(g > body.style.rgb().second, "the core is whiter than the body")
    }

    @Test
    fun theLightIsUnderTheBodyAndTheCoreIsOnTopOfIt() {
        val byId = DrawOutline.at(redPolygon, 0.2f, neon).associateBy { it.id.id }
        assertTrue(byId.getValue("p#draw0~h0").zIndex < byId.getValue("p#draw0").zIndex)
        assertTrue(byId.getValue("p#draw0").zIndex < byId.getValue("p#draw0~c").zIndex)
    }

    @Test
    fun theLightCoolsToTheOutlineAsTheFillComesIn() {
        val after = DrawOutline.at(redPolygon, 0.75f, neon)
        val core = after.lines().single { it.id.id == "p#draw0~c" }
        val body = after.lines().single { it.id.id == "p#draw0" }
        val (r0, g0, _) = core.style.rgb()
        assertTrue(g0 < 255 && g0 > body.style.rgb().second && r0 >= 200)
        val nearly = DrawOutline.at(redPolygon, 0.9999f, neon).lines().single { it.id.id == "p#draw0~c" }
        assertTrue(nearly.style.rgb().second < 10, "the core has cooled to the outline's red: ${nearly.style.rgb()}")
    }

    @Test
    fun withNoGlowThereIsNoLight() {
        assertEquals(listOf("p#draw0"), DrawOutline.at(redPolygon, 0.2f, neon.copy(glow = 0f)).map { it.id.id })
    }

    @Test
    fun theIdsOfTheShapesItMakesAreUniqueAndNotThePolygonsOwn() {
        for (t in listOf(0.1f, 0.4f, 0.6f, 0.9f)) {
            val ids = DrawOutline.at(redPolygon, t, DrawOutlineOptions()).map { it.id }
            assertEquals(ids.size, ids.toSet().size, "t $t")
        }
    }

    @Test
    fun theVerticesOfWhatItMakesAreUniqueAcrossItsShapesToo() {
        val vertices = DrawOutline.at(redPolygon, 0.4f, DrawOutlineOptions()).flatMap { e ->
            when (e) { is DrawnPolygon -> e.vertices; is DrawnPolyline -> e.vertices; else -> emptyList() }
        }
        assertEquals(vertices.size, vertices.map { it.id }.toSet().size)
        assertTrue(vertices.map { it.id }.toSet().size > 50)
    }

    @Test
    fun theLightFollowsTheLineWidthAndGlowStrength() {
        val strong = DrawOutline.at(redPolygon, 0.2f, neon.copy(glow = 2f)).lines().associateBy { it.id.id }
        val normal = DrawOutline.at(redPolygon, 0.2f, neon).lines().associateBy { it.id.id }
        assertTrue(strong.getValue("p#draw0~h0").lineWidth > normal.getValue("p#draw0~h0").lineWidth)
        assertTrue(strong.getValue("p#draw0~h6").style.alpha() > normal.getValue("p#draw0~h6").style.alpha())
    }

    @Test
    fun badOptionsAreRefused() {
        assertFailsWith<IllegalArgumentException> { DrawOutlineOptions(drawShare = 0f) }
        assertFailsWith<IllegalArgumentException> { DrawOutlineOptions(drawShare = 1f) }
        assertFailsWith<IllegalArgumentException> { DrawOutlineOptions(lineWidth = 0f) }
        assertFailsWith<IllegalArgumentException> { DrawOutlineOptions(trailShare = 1.5f) }
        assertFailsWith<IllegalArgumentException> { DrawOutlineOptions(trailBands = 0) }
        assertFailsWith<IllegalArgumentException> { DrawOutlineOptions(glow = -1f) }
        assertFailsWith<IllegalArgumentException> { DrawOutline.at(polygon, Float.NaN) }
    }
}
