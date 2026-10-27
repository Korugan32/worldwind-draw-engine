package com.tcsappdev.worldwinddraw.adapter.benchmark

import com.tcsappdev.worldwinddraw.adapter.layer.DrawLayer
import com.tcsappdev.worldwinddraw.adapter.layer.imageFillSurface
import com.tcsappdev.worldwinddraw.adapter.mapper.toWwkPolygon
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ImageRef
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Repeatable micro-measurements of what the WWK adapter does with many and with big shapes (Faz 11); see [Benchmark] and the core
 * benchmarks for how they are used. What WWK itself does on the render thread (tessellating, drawing, picking on the GPU) is not here: that
 * is measured on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdapterBenchmarks {
    private val style = ShapeStyle.SolidColor(ColorRgba(0, 0, 0))

    private fun polygon(id: String, count: Int, lat: Double = 40.0, lon: Double = 30.0, radius: Double = 0.5, style: ShapeStyle = this.style): DrawnPolygon {
        val random = Random(id.hashCode())
        return DrawnPolygon(
            EntityId(id), style,
            List(count) { i ->
                val angle = 2 * PI * i / count
                val r = radius * (1 + 0.05 * sin(angle * 7)) + 1e-4 * (random.nextDouble() - 0.5)
                Vertex(VertexId("$id-$i"), GeoPosition(lat + r * sin(angle), lon + r * cos(angle)))
            },
        )
    }

    private fun flagPng(): ByteArray {
        val image = BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 200) for (x in 0 until 300) image.setRGB(x, y, if (x < 150) 0xFFE30A17.toInt() else 0xFFFFFFFF.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    @Test
    fun turningAShapeIntoAWwkPolygon() {
        val bench = Benchmark("adapter")
        for (count in listOf(374, 10_000, 100_000)) {
            val shape = polygon("P", count, radius = 3.0)
            val ms = bench.measure("toWwkPolygon, $count vertices", runs = if (count > 10_000) 3 else 5) { shape.toWwkPolygon() }
            if (count == 10_000) assertTrue(ms < 200, "took $ms ms")
        }
        bench.write()
    }

    @Test
    fun theImageFillOfABigShapeEndToEnd() {
        val bench = Benchmark("adapter")
        val flag = ShapeStyle.ImageFill(ImageRef.Bytes("flag-bench", flagPng()), ImageFit.Inscribed, opacity = 0.8f)
        for (count in listOf(374, 5_000, 100_000)) {
            // A new id each time: the picture is cached under the shape's rings, so measuring a hit would measure nothing.
            var round = 0
            val ms = bench.measure("imageFillSurface (raster + texture), Inscribed, $count vertices", warmup = 1, runs = 3) {
                round++
                imageFillSurface(polygon("F", count, radius = 3.0 + round * 1e-6), flag)
            }
            if (count == 374) assertTrue(ms < 1_000, "took $ms ms")
        }
        bench.write()
    }

    private fun layerWith(scope: kotlinx.coroutines.CoroutineScope, repository: EntityRepository): DrawLayer =
        DrawLayer(repository, DrawStateMachine(HistoryManager(), repository), scope)

    @Test
    fun theLayerWithManyShapes() = runTest {
        val bench = Benchmark("adapter")
        val repository = EntityRepository()
        val shapes = List(200) { i -> polygon("S$i", 500, lat = 30.0 + (i / 20), lon = 10.0 + (i % 20) * 1.5, radius = 0.4) }
        val layer = layerWith(backgroundScope, repository)
        testScheduler.runCurrent()

        val build = System.nanoTime()
        shapes.forEach(repository::add) // every add makes the layer look at all of them again
        testScheduler.runCurrent()
        bench.record("%-62s %10.2f ms".format("layer: add 200 shapes of 500 vertices one by one (total)", (System.nanoTime() - build) / 1e6))

        var move = 0
        val moveMs = bench.measure("layer: one vertex of one of 200 shapes moves (publish + update)", warmup = 5, runs = 20) {
            move++
            val shape = repository.entities.value[7] as DrawnPolygon
            val moved = shape.vertices.toMutableList().also { it[10] = it[10].copy(position = GeoPosition(31.0 + move * 1e-6, 20.0)) }
            repository.update(shape.copy(vertices = moved))
            testScheduler.runCurrent()
        }
        assertTrue(moveMs < 100, "moving one vertex among 200 shapes took $moveMs ms")
        bench.write()
    }

    @Test
    fun theLayerWithOneHugeShape() = runTest {
        val bench = Benchmark("adapter")
        val repository = EntityRepository()
        val layer = layerWith(backgroundScope, repository)
        testScheduler.runCurrent()
        val shape = polygon("H", 100_000, radius = 5.0)

        val build = System.nanoTime()
        repository.add(shape)
        testScheduler.runCurrent()
        bench.record("%-62s %10.2f ms".format("layer: add one shape of 100,000 vertices", (System.nanoTime() - build) / 1e6))
        layer.applyLod(10)
        testScheduler.runCurrent()
        val lod = System.nanoTime()
        layer.applyLod(14)
        bench.record("%-62s %10.2f ms".format("layer: bring that shape to a coarser level of detail", (System.nanoTime() - lod) / 1e6))

        var move = 0
        val ms = bench.measure("layer: one vertex of the 100,000 vertex shape moves", warmup = 3, runs = 10) {
            move++
            val current = repository.entities.value.single() as DrawnPolygon
            val moved = current.vertices.toMutableList().also { it[40] = it[40].copy(position = GeoPosition(40.0 + move * 1e-6, 30.0)) }
            repository.update(current.copy(vertices = moved))
            testScheduler.runCurrent()
        }
        assertTrue(ms < 2_000, "took $ms ms")
        bench.write()
    }

    @Test
    fun theLayerWhileOneBigShapeIsEdited() = runTest {
        val bench = Benchmark("adapter")
        val repository = EntityRepository().apply { add(polygon("E", 2_000, radius = 2.0)) }
        val machine = DrawStateMachine(HistoryManager(), repository)
        DrawLayer(repository, machine, backgroundScope)
        testScheduler.runCurrent()
        val start = System.nanoTime()
        machine.onEvent(com.tcsappdev.worldwinddraw.core.state.DrawEvent.ModeChange(DrawMode.Edit(EntityId("E"))))
        testScheduler.runCurrent()
        bench.record("%-62s %10.2f ms".format("layer: start editing a shape of 2,000 vertices (handles built)", (System.nanoTime() - start) / 1e6))
        bench.write()
    }

    /** The size of the 1914 demo world: 300 polygons, about 25,000 vertices, all in view while the camera zooms through a level. */
    @Test
    fun aLevelChangeOverAWorldOfShapes() = runTest {
        val bench = Benchmark("adapter")
        val repository = EntityRepository()
        val layer = layerWith(backgroundScope, repository)
        testScheduler.runCurrent()
        val shapes = List(300) { i -> polygon("W$i", 85, lat = -30.0 + (i / 20) * 5.0, lon = -60.0 + (i % 20) * 6.0, radius = 2.0) }
        shapes.forEach(repository::add)
        testScheduler.runCurrent()
        var level = 3
        val build = bench.measure("layer: every one of 300 shapes (25,000 vertices) goes to the next level of detail", warmup = 2, runs = 8) {
            level = if (level == 3) 4 else 3
            // The budget builds a part per call: carry on until every shape is at the level, as the frames after a change do.
            do { layer.applyLod(level) } while (layer.hasPendingLod)
        }
        bench.record("%-62s %10.2f ms".format("(that is the most one camera step can cost the app thread, all at once)", build))
        bench.write()
    }
}
