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

    private fun fill(key: String, fit: ImageFit = ImageFit.Contain, scale: Float = 1f, opacity: Float = 1f) =
        ShapeStyle.ImageFill(ImageRef.Bytes(key, png()), fit, scale, opacity)

    // -- the renderable

    @Test
    fun aPictureIsLaidOverExactlyTheShapesBoundingBoxAndIsNotPickable() {
        val surface = assertNotNull(imageFillSurface(polygon(fill("box")), fill("box"))) as SurfaceImage

        assertEquals(10.0, surface.sector.minLatitude.inDegrees)
        assertEquals(14.0, surface.sector.maxLatitude.inDegrees)
        assertEquals(20.0, surface.sector.minLongitude.inDegrees)
        assertEquals(28.0, surface.sector.maxLongitude.inDegrees)
        assertFalse(surface.isPickEnabled, "the polygon on top is what gets picked")
    }

    @Test
    fun theSameInputsReuseTheSamePictureAndAChangeMakesANewOne() {
        val first = imageFillSurface(polygon(fill("reuse")), fill("reuse")) as SurfaceImage
        val again = imageFillSurface(polygon(fill("reuse")), fill("reuse")) as SurfaceImage
        val fainter = imageFillSurface(polygon(fill("reuse", opacity = 0.5f)), fill("reuse", opacity = 0.5f)) as SurfaceImage

        // WWK compares an in-memory image by reference: reusing the instance is what keeps its texture cache working.
        assertSame(first.imageSource, again.imageSource)
        assertNotSame(first.imageSource, fainter.imageSource)
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
