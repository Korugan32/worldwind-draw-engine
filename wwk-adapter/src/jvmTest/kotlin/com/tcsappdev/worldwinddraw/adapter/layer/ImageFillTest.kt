package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import earth.worldwind.shape.Polygon
import earth.worldwind.shape.SurfaceImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// jvmTest: decoding a picture and building WWK renderables is platform code (the Android version was
// checked on an emulator, see docs/PLAN.md).
class ImageFillTest {
    private fun png(width: Int = 60, height: Int = 40): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply { color = Color.RED; fillRect(0, 0, width, height); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun polygon(style: ShapeStyle, id: String = "A") = DrawnPolygon(
        id = EntityId(id),
        style = style,
        vertices = listOf(
            Vertex(VertexId("v1"), GeoPosition(10.0, 20.0)),
            Vertex(VertexId("v2"), GeoPosition(14.0, 20.0)),
            Vertex(VertexId("v3"), GeoPosition(14.0, 28.0)),
            Vertex(VertexId("v4"), GeoPosition(10.0, 28.0)),
        ),
    )

    private fun fill(
        key: String,
        fit: ImageFit = ImageFit.Contain,
        scale: Float = 1f,
        opacity: Float = 1f,
        background: ColorRgba? = null,
        repeat: Boolean = false,
        anchorX: Float? = null,
        anchorY: Float? = null,
        extendEdges: Boolean = false,
        edgeSoftness: Float = 0f,
    ) = ShapeStyle.ImageFill(ImageRef.Bytes(key, png()), fit, scale, opacity, background, repeat, anchorX, anchorY, extendEdges, edgeSoftness)

    // -- the renderable

    @Test
    fun aPictureThatFillsTheShapeIsLaidOverExactlyItsBoundingBoxAndIsNotPickable() {
        val stretched = fill("box", fit = ImageFit.Stretch)
        val surface = assertNotNull(imageFillSurface(polygon(stretched), stretched)) as SurfaceImage

        assertEquals(10.0, surface.sector.minLatitude.inDegrees)
        assertEquals(14.0, surface.sector.maxLatitude.inDegrees)
        assertEquals(20.0, surface.sector.minLongitude.inDegrees)
        assertEquals(28.0, surface.sector.maxLongitude.inDegrees)
        assertFalse(surface.isPickEnabled, "the polygon on top is what gets picked")
    }

    @Test
    fun aPictureThatLeavesPartOfTheShapeEmptyIsLaidOverOnlyItsOwnRectangleOfTheBoundingBox() {
        val contained = fill("rect", fit = ImageFit.Contain, scale = 0.5f)
        val surface = assertNotNull(imageFillSurface(polygon(contained), contained)) as SurfaceImage

        val sector = surface.sector
        assertTrue(sector.minLatitude.inDegrees >= 10.0 && sector.maxLatitude.inDegrees <= 14.0, "inside the box")
        assertTrue(sector.minLongitude.inDegrees >= 20.0 && sector.maxLongitude.inDegrees <= 28.0)
        assertTrue(sector.deltaLongitude.inDegrees < 8.0 - 0.5, "and smaller than it, as the picture is")
        assertEquals(24.0, (sector.minLongitude.inDegrees + sector.maxLongitude.inDegrees) / 2, 0.01, "centred: the middle of 20..28")

        val withBackground = fill("rect", fit = ImageFit.Contain, scale = 0.5f, background = ColorRgba(0, 255, 0))
        val whole = assertNotNull(imageFillSurface(polygon(withBackground), withBackground)) as SurfaceImage
        assertEquals(20.0, whole.sector.minLongitude.inDegrees, "with a background the whole box is needed")
        assertEquals(28.0, whole.sector.maxLongitude.inDegrees)
    }

    @Test
    fun theSameInputsReuseTheSamePictureAndAChangeMakesANewOne() {
        val first = imageFillSurface(polygon(fill("reuse")), fill("reuse")) as SurfaceImage
        val again = imageFillSurface(polygon(fill("reuse")), fill("reuse")) as SurfaceImage
        val larger = imageFillSurface(polygon(fill("reuse", scale = 0.5f)), fill("reuse", scale = 0.5f)) as SurfaceImage

        // WWK compares an in-memory image by reference: reusing the instance is what keeps its texture cache working.
        assertSame(first.imageSource, again.imageSource)
        assertNotSame(first.imageSource, larger.imageSource)
    }

    @Test
    fun theOpacityIsAppliedWhenDrawnSoFadingAFillMakesNoNewPicture() {
        val opaque = imageFillSurface(polygon(fill("fade")), fill("fade")) as FadingSurfaceImage
        val half = imageFillSurface(polygon(fill("fade", opacity = 0.5f)), fill("fade", opacity = 0.5f)) as FadingSurfaceImage
        val clear = imageFillSurface(polygon(fill("fade", opacity = 0f)), fill("fade", opacity = 0f)) as FadingSurfaceImage

        assertSame(opaque.imageSource, half.imageSource, "one picture for every opacity")
        assertSame(opaque.imageSource, clear.imageSource)
        assertEquals(listOf(1f, 0.5f, 0f), listOf(opaque.opacity, half.opacity, clear.opacity))
    }

    @Test
    fun theBackgroundColourIsPartOfWhatMakesAPictureSoChangingItMakesANewOne() {
        val plain = imageFillSurface(polygon(fill("bg")), fill("bg")) as SurfaceImage
        val green = fill("bg", background = ColorRgba(0, 255, 0))
        val withGreen = imageFillSurface(polygon(green), green) as SurfaceImage
        val greenAgain = imageFillSurface(polygon(green), green) as SurfaceImage

        assertNotSame(plain.imageSource, withGreen.imageSource)
        assertSame(withGreen.imageSource, greenAgain.imageSource)
    }

    private fun withRings(style: ShapeStyle, vararg extra: List<Vertex>) = polygon(style).copy(extraRings = extra.toList())

    private fun squareRing(prefix: String, lat: Double, lon: Double, size: Double) = listOf(
        Vertex(VertexId("${prefix}1"), GeoPosition(lat, lon)),
        Vertex(VertexId("${prefix}2"), GeoPosition(lat + size, lon)),
        Vertex(VertexId("${prefix}3"), GeoPosition(lat + size, lon + size)),
        Vertex(VertexId("${prefix}4"), GeoPosition(lat, lon + size)),
    )

    @Test
    fun aPictureForAPolygonOfSeveralRingsIsLaidOverTheBoxOfThemAll() {
        val stretched = fill("rings", fit = ImageFit.Stretch)
        val polygon = withRings(stretched, squareRing("p", 30.0, 40.0, 2.0)) // another part, far from the first
        val surface = assertNotNull(imageFillSurface(polygon, stretched)) as SurfaceImage

        assertEquals(10.0, surface.sector.minLatitude.inDegrees)
        assertEquals(32.0, surface.sector.maxLatitude.inDegrees)
        assertEquals(20.0, surface.sector.minLongitude.inDegrees)
        assertEquals(42.0, surface.sector.maxLongitude.inDegrees)
    }

    @Test
    fun theRingsArePartOfWhatMakesAPictureSoAddingOrChangingOneMakesANewOne() {
        val stretched = fill("rings2", fit = ImageFit.Stretch)
        val one = imageFillSurface(polygon(stretched), stretched) as SurfaceImage
        val withHole = withRings(stretched, squareRing("h", 11.0, 22.0, 1.0))
        val holed = imageFillSurface(withHole, stretched) as SurfaceImage
        val holedAgain = imageFillSurface(withRings(stretched, squareRing("h", 11.0, 22.0, 1.0)), stretched) as SurfaceImage
        val biggerHole = imageFillSurface(withRings(stretched, squareRing("h", 11.0, 22.0, 2.0)), stretched) as SurfaceImage

        assertNotSame(one.imageSource, holed.imageSource)
        assertSame(holed.imageSource, holedAgain.imageSource)
        assertNotSame(holed.imageSource, biggerHole.imageSource)
    }

    @Test
    fun theSoftnessOfTheEdgeIsPartOfWhatMakesAPictureSoChangingItMakesANewOne() {
        val sharp = fill("soft", fit = ImageFit.Stretch)
        val soft = fill("soft", fit = ImageFit.Stretch, edgeSoftness = 0.5f)
        val sharpSurface = imageFillSurface(polygon(sharp), sharp) as SurfaceImage
        val softSurface = imageFillSurface(polygon(soft), soft) as SurfaceImage
        val softAgain = imageFillSurface(polygon(soft), soft) as SurfaceImage

        assertNotSame(sharpSurface.imageSource, softSurface.imageSource)
        assertSame(softSurface.imageSource, softAgain.imageSource)
    }

    @Test
    fun extendingTheEdgesIsPartOfWhatMakesAPictureSoChangingItMakesANewOne() {
        val plain = fill("ext", scale = 0.5f)
        val extended = fill("ext", scale = 0.5f, extendEdges = true)
        val plainSurface = imageFillSurface(polygon(plain), plain) as SurfaceImage
        val extendedSurface = imageFillSurface(polygon(extended), extended) as SurfaceImage
        val extendedAgain = imageFillSurface(polygon(extended), extended) as SurfaceImage

        assertNotSame(plainSurface.imageSource, extendedSurface.imageSource)
        assertSame(extendedSurface.imageSource, extendedAgain.imageSource)
    }

    @Test
    fun repeatAndTheAnchorArePartOfWhatMakesAPictureSoChangingThemMakesANewOne() {
        val plain = imageFillSurface(polygon(fill("rep")), fill("rep")) as SurfaceImage
        val tiled = fill("rep", scale = 0.5f, repeat = true)
        val moved = fill("rep", scale = 0.5f, repeat = true, anchorX = 0.25f)
        val tiledSurface = imageFillSurface(polygon(tiled), tiled) as SurfaceImage
        val movedSurface = imageFillSurface(polygon(moved), moved) as SurfaceImage

        assertNotSame(plain.imageSource, tiledSurface.imageSource)
        assertNotSame(tiledSurface.imageSource, movedSurface.imageSource)
    }

    @Test
    fun aUrlAndBrokenBytesAndAShapeTooWideForOneBoxProduceNothing() {
        val url = ShapeStyle.ImageFill(ImageRef.Url("https://example.com/flag.png"))
        val broken = ShapeStyle.ImageFill(ImageRef.Bytes("broken-fill", byteArrayOf(1, 2, 3)))
        val wide = DrawnPolygon(
            id = EntityId("wide"),
            style = fill("wide"),
            vertices = listOf(
                Vertex(VertexId("a"), GeoPosition(0.0, -170.0)),
                Vertex(VertexId("b"), GeoPosition(10.0, -170.0)),
                Vertex(VertexId("c"), GeoPosition(10.0, 170.0)),
            ),
        )

        assertNull(imageFillSurface(polygon(url), url))
        assertNull(imageFillSurface(polygon(broken), broken))
        assertNull(imageFillSurface(wide, fill("wide")))
    }

    // -- in the layer

    private fun layerWith(style: ShapeStyle, scope: kotlinx.coroutines.CoroutineScope): Triple<DrawLayer, DrawStateMachine, EntityRepository> {
        val repository = EntityRepository()
        repository.add(polygon(style))
        val stateMachine = DrawStateMachine(HistoryManager(), repository)
        return Triple(DrawLayer(repository, stateMachine, scope), stateMachine, repository)
    }

    @Test
    fun theLayerPutsThePictureBelowThePolygonWhichStaysTransparentAndPickable() = runTest {
        val (layer, _, _) = layerWith(fill("layer"), backgroundScope)
        delay(50)

        val renderables = layer.toList()
        assertEquals(2, renderables.size, "the picture and the polygon")
        assertTrue(renderables[0] is SurfaceImage, "the picture is first, so it is drawn below")
        val polygon = renderables[1] as Polygon
        assertEquals(0f, polygon.attributes.interiorColor.alpha, "the polygon's own fill is invisible")
        assertTrue(polygon.hasUserProperty(DrawLayer.ENTITY_ID_KEY))
        assertTrue(polygon.isPickEnabled)
    }

    @Test
    fun whenThePictureCannotBeProducedAGreyPlaceholderFillsTheShapeInstead() = runTest {
        val (layer, _, _) = layerWith(ShapeStyle.ImageFill(ImageRef.Url("https://example.com/flag.png")), backgroundScope)
        delay(50)

        val renderables = layer.toList()
        assertEquals(1, renderables.size, "just the polygon: no picture")
        val polygon = renderables.single() as Polygon
        assertTrue(polygon.attributes.interiorColor.alpha > 0f, "visible, so the shape isn't lost")
        assertTrue(kotlin.math.abs(polygon.attributes.interiorColor.red - 128 / 255f) < 1e-3, "a neutral grey")
    }

    @Test
    fun thePictureStepsAsideWhileAVertexOfThatShapeIsBeingDraggedAndComesBack() = runTest {
        val (layer, stateMachine, _) = layerWith(fill("drag"), backgroundScope)
        val id = EntityId("A")
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(12.0, 24.0), id))
        stateMachine.onEvent(DrawEvent.Click(GeoPosition(12.0, 24.0), id))
        assertEquals(DrawMode.Edit(id), stateMachine.state.value.mode)
        delay(50)
        assertTrue(layer.any { it is SurfaceImage }, "the picture is there while editing")

        stateMachine.onEvent(DrawEvent.DragStart(GeoPosition(10.0, 20.0), VertexId("v1"), id))
        delay(50)
        assertFalse(layer.any { it is SurfaceImage }, "left out during the drag: it would be redone on every step")

        stateMachine.onEvent(DrawEvent.DragEnd(GeoPosition(9.0, 19.0)))
        delay(50)
        assertTrue(layer.any { it is SurfaceImage }, "back once the shape has settled")
    }
}
