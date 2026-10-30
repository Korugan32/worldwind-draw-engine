package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.MarkerLabel
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.HatchPattern
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpolationTest {
    @Test
    fun aNumberIsTheFractionOfTheWayBetweenTheTwo() {
        assertEquals(10.0, lerp(10.0, 50.0, 0f))
        assertEquals(20.0, lerp(10.0, 50.0, 0.25f))
        assertEquals(50.0, lerp(10.0, 50.0, 1f))
        assertEquals(5f, lerp(10f, 0f, 0.5f))
    }

    @Test
    fun aLongitudeThatDoesNotCrossTheAntimeridianGoesStraight() {
        assertEquals(20.0, lerpLongitude(10.0, 50.0, 0.25f))
        assertEquals(-30.0, lerpLongitude(-10.0, -50.0, 0.5f))
        assertEquals(0.0, lerpLongitude(-90.0, 90.0, 0.5f))
    }

    @Test
    fun aLongitudeTakesTheShortWayAcrossTheAntimeridian() {
        assertEquals(180.0, lerpLongitude(170.0, -170.0, 0.5f)) // 20 degrees apart, not 340
        assertEquals(-175.0, lerpLongitude(170.0, -170.0, 0.75f)) // wrapped back into -180..180
        assertEquals(175.0, lerpLongitude(-170.0, 170.0, 0.75f))
    }

    @Test
    fun aPositionBlendsLatitudeLongitudeAndAltitude() {
        val middle = lerpPosition(GeoPosition(0.0, 10.0, 100.0), GeoPosition(40.0, 20.0, 300.0), 0.5f)
        assertEquals(GeoPosition(20.0, 15.0, 200.0), middle)
    }

    @Test
    fun aColourBlendsEveryChannelAndRounds() {
        assertEquals(ColorRgba(50, 50, 150, 205), lerpColor(ColorRgba(0, 100, 200, 255), ColorRgba(100, 0, 100, 155), 0.5f))
        assertEquals(ColorRgba(10, 20, 30, 40), lerpColor(ColorRgba(10, 20, 30, 40), ColorRgba(250, 250, 250, 250), 0f))
        assertEquals(ColorRgba(250, 250, 250, 250), lerpColor(ColorRgba(10, 20, 30, 40), ColorRgba(250, 250, 250, 250), 1f))
        assertEquals(ColorRgba(2, 2, 2, 2), lerpColor(ColorRgba(0, 0, 0, 0), ColorRgba(3, 3, 3, 3), 0.5f)) // 1.5 rounds up
    }

    @Test
    fun whatHasNoMiddleChangesAtTheHalfWayPoint() {
        assertEquals("a", step("a", "b", 0.49f))
        assertEquals("b", step("a", "b", 0.5f))
    }

    @Test
    fun twoColoursBlend() {
        assertEquals(solid(50, 100, 25, 150), lerpStyle(solid(0, 0, 0, 100), solid(100, 200, 50, 200), 0.5f))
    }

    @Test
    fun twoPatternsOfTheSameImageBlendTheirOpacity() {
        val a = ShapeStyle.ImagePattern(ImageRef.Url("x"), 0.2f)
        val b = ShapeStyle.ImagePattern(ImageRef.Url("x"), 1f)
        val blended = lerpStyle(a, b, 0.5f) as ShapeStyle.ImagePattern
        assertEquals(ImageRef.Url("x"), blended.image)
        assertEquals(0.6f, blended.opacity, 1e-6f) // not exact: on JS 0.2 + 0.8 * 0.5 comes out as 0.6000000000000001
    }

    @Test
    fun twoPatternsOfDifferentImagesChangeAtTheHalfWayPoint() {
        val a = ShapeStyle.ImagePattern(ImageRef.Url("x"), 0.2f)
        val b = ShapeStyle.ImagePattern(ImageRef.Url("y"), 1f)
        assertEquals(a, lerpStyle(a, b, 0.49f))
        assertEquals(b, lerpStyle(a, b, 0.5f))
    }

    @Test
    fun twoImageFillsThatDifferOnlyInOpacityAndBackgroundBlend() {
        val a = ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 0f, backgroundColor = ColorRgba(0, 0, 0, 0))
        val b = ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 1f, backgroundColor = ColorRgba(100, 100, 100, 100))
        assertEquals(ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 0.5f, backgroundColor = ColorRgba(50, 50, 50, 50)), lerpStyle(a, b, 0.5f))
    }

    @Test
    fun twoImageFillsThatDifferInHowTheyFitChangeAtTheHalfWayPoint() {
        val a = ShapeStyle.ImageFill(ImageRef.Url("x"), fit = ImageFit.Contain, opacity = 0f)
        val b = ShapeStyle.ImageFill(ImageRef.Url("x"), fit = ImageFit.Cover, opacity = 1f)
        assertEquals(a, lerpStyle(a, b, 0.25f))
        assertEquals(b, lerpStyle(a, b, 0.75f))
    }

    @Test
    fun aMissingBackgroundChangesAtTheHalfWayPointWhileTheRestBlends() {
        val a = ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 0f, backgroundColor = null)
        val b = ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 1f, backgroundColor = ColorRgba(9, 9, 9))
        assertEquals(a.copy(opacity = 0.25f), lerpStyle(a, b, 0.25f))
        assertEquals(ShapeStyle.ImageFill(ImageRef.Url("x"), opacity = 0.75f, backgroundColor = ColorRgba(9, 9, 9)), lerpStyle(a, b, 0.75f))
    }

    @Test
    fun twoHatchesOfTheSamePatternBlendTheirColours() {
        val a = ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(0, 0, 0))
        val b = ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(100, 200, 50))
        assertEquals(a.copy(color = ColorRgba(50, 100, 25)), lerpStyle(a, b, 0.5f))
    }

    @Test
    fun twoHatchesOfDifferentPatternsChangeAtTheHalfWayPoint() {
        val a = ShapeStyle.Hatch(HatchPattern.Cross, ColorRgba(0, 0, 0))
        val b = ShapeStyle.Hatch(HatchPattern.Dots, ColorRgba(100, 200, 50))
        assertEquals(a, lerpStyle(a, b, 0.4f))
        assertEquals(b, lerpStyle(a, b, 0.6f))
    }

    @Test
    fun aLookThatChangesKindChangesAtTheHalfWayPoint() {
        val a = solid(1, 2, 3)
        val b = ShapeStyle.ImagePattern(ImageRef.Url("x"))
        assertEquals(a, lerpStyle(a, b, 0.49f))
        assertEquals(b, lerpStyle(a, b, 0.5f))
    }

    @Test
    fun aMissingOutlineAppearsAtTheHalfWayPointAndTwoOutlinesBlend() {
        val a = solid(0, 0, 0)
        val b = solid(100, 100, 100)
        assertNull(lerpOptionalStyle(null, b, 0.49f))
        assertEquals(b, lerpOptionalStyle(null, b, 0.5f))
        assertNull(lerpOptionalStyle(null, null, 0.3f))
        assertEquals(solid(50, 50, 50), lerpOptionalStyle(a, b, 0.5f))
    }

    @Test
    fun verticesMoveAndKeepTheirIds() {
        val a = listOf(Vertex(VertexId("p"), GeoPosition(0.0, 0.0)), Vertex(VertexId("q"), GeoPosition(10.0, 10.0), mapOf("k" to "old")))
        val b = listOf(Vertex(VertexId("p"), GeoPosition(4.0, 8.0)), Vertex(VertexId("q"), GeoPosition(20.0, 20.0), mapOf("k" to "new")))
        val middle = lerpVertices(a, b, 0.5f)
        assertEquals(listOf(VertexId("p"), VertexId("q")), middle.map { it.id })
        assertEquals(listOf(GeoPosition(2.0, 4.0), GeoPosition(15.0, 15.0)), middle.map { it.position })
        assertEquals(mapOf("k" to "new"), middle[1].metadata)
        assertEquals(mapOf("k" to "old"), lerpVertices(a, b, 0.4f)[1].metadata)
    }

    @Test
    fun twoLabelsOfTheSameTextBlendTheirColoursAndOthersChangeAtTheHalfWayPoint() {
        val a = MarkerLabel("Ankara", ColorRgba(0, 0, 0), outlineColor = null)
        val b = MarkerLabel("Ankara", ColorRgba(100, 100, 100), outlineColor = null)
        assertEquals(a.copy(color = ColorRgba(50, 50, 50)), lerpLabel(a, b, 0.5f))
        val other = b.copy(text = "Izmir")
        assertEquals(a, lerpLabel(a, other, 0.4f))
        assertEquals(other, lerpLabel(a, other, 0.5f))
        assertNull(lerpLabel(null, null, 0.5f))
        assertEquals(b, lerpLabel(null, b, 0.5f))
        assertNull(lerpLabel(null, b, 0.1f))
    }
}
