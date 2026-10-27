package com.tcsappdev.worldwinddraw.core.benchmark

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PathSimplifier
import com.tcsappdev.worldwinddraw.core.geometry.PolygonCenter
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.geometry.Visvalingam
import com.tcsappdev.worldwinddraw.core.history.HistoryManager
import com.tcsappdev.worldwinddraw.core.raster.ImageFillRasterizer
import com.tcsappdev.worldwinddraw.core.raster.PixelImage
import com.tcsappdev.worldwinddraw.core.repository.EntityRepository
import com.tcsappdev.worldwinddraw.core.state.DrawEvent
import com.tcsappdev.worldwinddraw.core.state.DrawMode
import com.tcsappdev.worldwinddraw.core.state.DrawStateMachine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ImageFit
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Repeatable micro-measurements of the work `core` does on big shapes (Faz 11). Each test prints its numbers and asserts only a generous
 * upper bound, so a real regression (an order of magnitude) fails the build and ordinary noise does not.
 */
class CoreBenchmarks {
    /** A wobbly circle of [count] vertices around (40, 30), about 8 degrees across: what a detailed coastline looks like to the algorithms. */
    private fun coastline(count: Int, seed: Int = 1, lat: Double = 40.0, lon: Double = 30.0): List<GeoPosition> {
        val random = Random(seed)
        return List(count) { i ->
            val angle = 2 * PI * i / count
            val radius = 4.0 + 0.3 * sin(angle * 7) + 0.01 * (random.nextDouble() - 0.5)
            GeoPosition(lat + radius * sin(angle), lon + radius * cos(angle))
        }
    }

    private fun polygon(count: Int) = DrawnPolygon(
        EntityId("P"), ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
        coastline(count).mapIndexed { i, p -> Vertex(VertexId("v$i"), p) },
    )

    private fun picture() = PixelImage(300, 200, IntArray(300 * 200) { 0xFFE30A17.toInt() })

    @Test
    fun rasterizingAnImageFill() {
        val bench = Benchmark("core")
        for (count in listOf(374, 5_000, 100_000)) {
            val ring = coastline(count)
            val source = picture()
            val stretch = bench.measure("ImageFill rasterize, Stretch, $count vertices, 1024 px", runs = if (count > 10_000) 3 else 5) {
                ImageFillRasterizer.render(source, listOf(ring), ImageFit.Stretch, 1f, 0.8f, 1024)
            }
            val inscribed = bench.measure("ImageFill rasterize, Inscribed, $count vertices, 1024 px", runs = if (count > 10_000) 3 else 5) {
                ImageFillRasterizer.render(source, listOf(ring), ImageFit.Inscribed, 1f, 0.8f, 1024)
            }
            if (count == 374) assertTrue(stretch < 1_000 && inscribed < 1_000, "a 374 vertex shape should rasterize well under a second, took $stretch and $inscribed ms")
            if (count == 100_000) assertTrue(stretch < 20_000, "even 100,000 vertices should not take 20 seconds, took $stretch ms")
        }
        bench.write()
    }

    @Test
    fun theVisualCentreOfBigShapes() {
        val bench = Benchmark("core")
        for (count in listOf(374, 5_000, 100_000)) {
            val ring = coastline(count)
            val ms = bench.measure("PolygonCenter, $count vertices", runs = if (count > 10_000) 3 else 5) { PolygonCenter.visualCenter(ring) }
            if (count == 374) assertTrue(ms < 500, "took $ms ms")
        }
        val withHoles = listOf(coastline(5_000), coastline(500, seed = 2, lat = 40.0, lon = 30.0).map { GeoPosition(40 + (it.latitude - 40) * 0.2, 30 + (it.longitude - 30) * 0.2) })
        bench.measure("PolygonCenter, 5,000 vertices with a hole of 500") { PolygonCenter.visualCenterOfRings(withHoles) }
        bench.write()
    }

    @Test
    fun simplifyingBigShapes() {
        val bench = Benchmark("core")
        for (count in listOf(5_000, 100_000)) {
            val ring = coastline(count)
            bench.measure("PathSimplifier.simplifyRing, $count vertices (10 km)", runs = 3) { PathSimplifier.simplifyRing(ring, 10_000.0) }
            val ms = bench.measure("Visvalingam to 500 vertices, $count vertices", runs = 3) { Visvalingam.simplify(ring, true, 500) }
            if (count == 100_000) assertTrue(ms < 20_000, "took $ms ms")
        }
        bench.write()
    }

    /** Every drag step moves one vertex and the repository publishes the changed shape: what costs the finger's frame time on a big shape. */
    @Test
    fun draggingOneVertexOfABigShape() {
        val bench = Benchmark("core")
        for (count in listOf(1_000, 10_000, 100_000)) {
            val repository = EntityRepository().apply { add(polygon(count)) }
            val history = HistoryManager()
            val machine = DrawStateMachine(history, repository)
            val id = EntityId("P")
            machine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
            machine.onEvent(DrawEvent.DragStart(GeoPosition(44.0, 30.0), VertexId("v${count / 4}"), id))
            var step = 0
            val ms = bench.measure("one drag step (reduce + preview command + publish), $count vertices", warmup = 20, runs = 50) {
                step++
                machine.onEvent(DrawEvent.DragMove(GeoPosition(44.0 + step * 1e-5, 30.0)))
            }
            if (count == 10_000) assertTrue(ms < 25, "a drag step on 10,000 vertices should cost a fraction of a frame, took $ms ms")
        }
        bench.write()
    }

    @Test
    fun whatTheUndoHistoryHolds() {
        val bench = Benchmark("core")
        val count = 20_000
        val megabytes = bench.retainedMegabytes("history of 200 vertex moves on a $count vertex shape (excluding the shape)") {
            val repository = EntityRepository().apply { add(polygon(count)) }
            val history = HistoryManager()
            val machine = DrawStateMachine(history, repository)
            val id = EntityId("P")
            machine.onEvent(DrawEvent.ModeChange(DrawMode.Edit(id)))
            repeat(200) { i ->
                machine.onEvent(DrawEvent.DragStart(GeoPosition(44.0, 30.0), VertexId("v$i"), id))
                machine.onEvent(DrawEvent.DragMove(GeoPosition(44.0 + i * 1e-3, 30.0)))
                machine.onEvent(DrawEvent.DragEnd(GeoPosition(44.0 + i * 1e-3, 30.0)))
            }
            Triple(repository, history, machine)
        }
        // The shape itself is about 20,000 vertices; 200 steps must not hold a copy of it each (that would be hundreds of MB).
        assertTrue(megabytes < 200, "the history holds $megabytes MB")
        bench.write()
    }

    /** An archipelago: [count] small islands on a grid, each a ring of [vertices] points, a lake in every tenth. */
    private fun archipelago(count: Int, vertices: Int): List<List<GeoPosition>> {
        val rings = ArrayList<List<GeoPosition>>()
        val perRow = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()
        for (i in 0 until count) {
            val lat = 10.0 + (i / perRow) * 0.1
            val lon = 20.0 + (i % perRow) * 0.1
            rings += coastline(vertices, seed = i, lat = lat, lon = lon).map { GeoPosition(lat + (it.latitude - lat) * 0.01, lon + (it.longitude - lon) * 0.01) }
            if (i % 10 == 0) rings += coastline(vertices, seed = -i, lat = lat, lon = lon).map { GeoPosition(lat + (it.latitude - lat) * 0.003, lon + (it.longitude - lon) * 0.003) }
        }
        return rings
    }

    @Test
    fun aShapeOfManyParts() {
        val bench = Benchmark("core")
        for (count in listOf(100, 1_000)) {
            val rings = archipelago(count, 50)
            val ms = bench.measure("PolygonParts.split, $count islands of 50 vertices (${rings.size} rings)", runs = 3) { PolygonParts.split(rings) }
            if (count == 1_000) assertTrue(ms < 5_000, "took $ms ms")
            val source = picture()
            bench.measure("ImageFill Inscribed, $count islands (a picture each)", warmup = 1, runs = 3) {
                ImageFillRasterizer.render(source, rings, ImageFit.Inscribed, 1f, 0.8f, 1024)
            }
            bench.measure("ImageFill Stretch, $count islands (one picture)", warmup = 1, runs = 3) {
                ImageFillRasterizer.render(source, rings, ImageFit.Stretch, 1f, 0.8f, 1024)
            }
        }
        bench.write()
    }
}
