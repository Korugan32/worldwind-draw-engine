package com.tcsappdev.worldwinddraw.measure

import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.PolygonParts
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.overlay.InvalidShapeException
import com.tcsappdev.worldwinddraw.core.overlay.OverlayEngine
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One share of a plot that has been divided: the rings it is made of (the outline of each part followed by its holes, like
 * [DrawnPolygon.rings]), its [area] and the area it was meant to have.
 *
 * @property rings the rings of the piece. The corners that were on the outline of the plot keep their [VertexId]; the ones the cuts made
 * have new ones.
 * @property area the area of the piece, in square metres (see [Measure.area]).
 * @property targetArea the area the share asked for, in square metres: the share's part of the area of the whole plot. [area] is that, to
 * within what the arithmetic allows (a millionth).
 */
public class Piece(
    public val rings: List<List<Vertex>>,
    public val area: Double,
    public val targetArea: Double,
)

/**
 * A plot divided into shares.
 *
 * @property pieces the pieces, in the order of the shares that were asked for: the first is on the side the cuts start from.
 * @property cutBearing the direction the cut lines run along, in degrees clockwise from north, from 0 up to (not including) 180.
 */
public class ShareSplitResult(
    public val pieces: List<Piece>,
    public val cutBearing: Double,
)

/**
 * Divides a plot of land into shares of given sizes: three heirs with a half, a quarter and a quarter get three pieces whose areas are
 * exactly in that proportion.
 *
 * **How:** the cuts are parallel lines. Sweeping a line across the plot, the area on its one side only ever grows, so for each share the
 * place where the area behind the line reaches that share's part of the whole is found by halving an interval (bisection), on the true
 * area of the ellipsoid (see [Measure]), not on a flat approximation of it. Where the cuts lie are then cut out of the plot by [OverlayEngine]
 * (which also gives the pieces of a plot that is not convex, or has a hole or a part of its own, whatever they look like).
 *
 * **For exact areas give an overlay that does not round**: `JtsOverlayEngine`'s default rounds every corner to a grid of 1e-7 degrees (a
 * centimetre), which is what lets two sources of the same border agree, but it moves the corners a cut makes by a few millimetres, and on a
 * cut of 100 m that is a few tenths of a square metre. With `JtsOverlayEngine(gridSize = 0.0)` the pieces have the areas asked for to a
 * ten-millionth or better.
 *
 * The plot is worked on a flat map in metres around its centre, which is right to a fifth of a percent of a distance at 10 km from the centre
 * at the latitude of Turkey: the areas are measured on the ellipsoid, so that error does not touch them; it only means that the cuts are
 * "parallel" on that flat map. A plot is expected to be a few kilometres across and not near a pole.
 */
public object ShareSplit {
    private const val BISECTIONS = 100
    private const val REFINEMENTS = 4

    /**
     * Divides [polygon] into pieces whose areas are in proportion to [shares] (only the proportion counts: `1, 1, 2` and `0.25, 0.25, 0.5` are
     * the same).
     *
     * @param overlay what cuts the pieces out (for example `JtsOverlayEngine(gridSize = 0.0)` of the `boolean-ops` module; see above for why
     * not its default).
     * @param newVertexId makes the ids of the corners the cuts create.
     * @param cutBearing the direction the cut lines run along, in degrees clockwise from north (any number: it is taken modulo 180); the
     * shares are then laid side by side across them. `null` to let the cuts run across the long way of the plot, so that the pieces come
     * out as compact as the plot allows: a long thin field is cut into short pieces and not into long strips.
     * @throws IllegalArgumentException if [shares] is empty or has a share that is not a positive number, the plot has no area, or it crosses the
     * antimeridian (longitude 180: the overlay works on longitude and latitude as a flat plane and cannot cut across that line; [Measure] can still
     * measure such a plot).
     * @throws InvalidShapeException if [polygon] is not a valid polygon (it crosses itself, a hole lies outside it...).
     */
    public fun split(
        polygon: DrawnPolygon,
        shares: List<Double>,
        overlay: OverlayEngine,
        newVertexId: () -> VertexId,
        cutBearing: Double? = null,
    ): ShareSplitResult {
        require(shares.isNotEmpty()) { "there has to be at least one share" }
        require(shares.all { it.isFinite() && it > 0.0 }) { "every share has to be a positive number" }
        // The overlay takes longitude and latitude for a flat plane, where an edge that jumps from +179.9 to -179.9 goes round the whole globe.
        require(polygon.rings.none { ring -> ring.indices.any { i -> abs(ring[i].position.longitude - ring[(i + 1) % ring.size].position.longitude) > 180.0 } }) {
            "a plot that crosses the antimeridian (longitude 180) cannot be divided"
        }
        overlay.validate(polygon)?.let { throw InvalidShapeException(it) }
        val total = Measure.area(polygon)
        require(total > 0.0) { "the plot has no area" }

        val ring = polygon.vertices.map { it.position }
        val frame = LocalFrame(GeoPosition(ring.sumOf { it.latitude } / ring.size, centreLongitude(ring)))
        val parts = PolygonParts.split(polygon.rings.map { r -> r.map { it.position } })
            .map { part -> part.map { r -> r.map { frame.toPlane(it) } } }

        // The sweep goes along the direction n = (east, north) and the cut lines run along the direction d across it.
        val sweepBearing = if (cutBearing != null) (cutBearing + 90.0) else longAxisBearing(parts.flatten().flatten())
        val sweep = Plane(sin(sweepBearing * PI / 180.0), cos(sweepBearing * PI / 180.0))
        val resultBearing = (((sweepBearing - 90.0) % 180.0) + 180.0) % 180.0

        val sum = shares.sum()
        val all = parts.flatten().flatten()
        val low = all.minOf { dot(it, sweep) }
        val high = all.maxOf { dot(it, sweep) }
        // Where each cut is, along the sweep: first by the area behind it as the plot's own area gives it ...
        var cumulativeShare = 0.0
        var offsets = List(shares.size - 1) { i ->
            cumulativeShare += shares[i]
            cutAt(parts, sweep, frame, low, high, total * cumulativeShare / sum)
        }

        // The pieces: the plot without what lies beyond the cut after it and without what lies before the cut before it.
        val reach = 2.0 * (high - low + all.maxOf { abs(dot(it, Plane(-sweep.y, sweep.x))) } * 2.0) + 100.0
        fun piecesFor(at: List<Double>): List<List<List<Vertex>>> = shares.indices.map { i ->
            var rings: List<List<Vertex>> = polygon.rings
            if (i < at.size) rings = cutOff(rings, polygon, halfPlane(frame, sweep, at[i], +1.0, reach), overlay, newVertexId)
            if (i > 0) rings = cutOff(rings, polygon, halfPlane(frame, sweep, at[i - 1], -1.0, reach), overlay, newVertexId)
            rings
        }
        fun areaOf(rings: List<List<Vertex>>) = if (rings.isEmpty()) 0.0 else Measure.area(shapeOf(rings, polygon))

        var pieces = piecesFor(offsets)

        // ... then corrected by the areas of the pieces that came out. The overlay works on longitude and latitude as if flat, so a corner a cut
        // makes on an edge lies on the straight line of the edge in them, and an area is measured with the edges as great-circle arcs: the two
        // differ by about a millimetre on an edge of 300 m, which is a few millionths of the area of a plot and would not add up. One or two
        // Newton steps on the pieces themselves make the shares agree with each other to ten millionths or better.
        for (round in 0 until REFINEMENTS) {
            val areas = pieces.map { areaOf(it) }
            val whole = areas.sum()
            var behind = 0.0
            var cumulative = 0.0
            var worst = 0.0
            val corrected = ArrayList<Double>(offsets.size)
            for (i in offsets.indices) {
                behind += areas[i]
                cumulative += shares[i]
                val error = whole * cumulative / sum - behind
                worst = maxOf(worst, abs(error) / whole)
                val step = (high - low) * 1e-4
                val slope = (areaBehind(parts, sweep, frame, offsets[i] + step) - areaBehind(parts, sweep, frame, offsets[i] - step)) / (2.0 * step)
                corrected += if (slope > 0.0) offsets[i] + error / slope else offsets[i]
            }
            if (worst < 1e-10) break
            offsets = corrected
            pieces = piecesFor(offsets)
        }

        val result = pieces.mapIndexed { i, rings -> Piece(rings, areaOf(rings), total * shares[i] / sum) }
        return ShareSplitResult(result, resultBearing)
    }

    /** The longitude of the middle of [ring], found by the offsets from its first corner so that a plot across the antimeridian is right. */
    private fun centreLongitude(ring: List<GeoPosition>): Double {
        val first = ring.first().longitude
        var sum = 0.0
        for (p in ring) {
            var d = p.longitude - first
            if (d > 180.0) d -= 360.0 else if (d < -180.0) d += 360.0
            sum += d
        }
        var lon = first + sum / ring.size
        if (lon > 180.0) lon -= 360.0 else if (lon < -180.0) lon += 360.0
        return lon
    }

    /**
     * The bearing (clockwise from north, in degrees) the sweep goes along when the cuts run across the long way of the plot: the long side of
     * the smallest box that fits the plot. (Not the direction in which the plot reaches furthest, which for a rectangle is its diagonal.)
     * The box is found by trying the directions a degree apart and then a twentieth of a degree apart round the best.
     */
    private fun longAxisBearing(points: List<Plane>): Double {
        fun boxArea(degrees: Double) = extent(points, degrees) * extent(points, degrees + 90.0)
        var best = 0.0
        var bestArea = Double.MAX_VALUE
        for (degrees in 0 until 90) {
            val area = boxArea(degrees.toDouble())
            if (area < bestArea - 1e-9) { bestArea = area; best = degrees.toDouble() }
        }
        val coarse = best
        for (step in -20..20) {
            val degrees = coarse + step * 0.05
            val area = boxArea(degrees)
            if (area < bestArea - 1e-9) { bestArea = area; best = degrees }
        }
        // along the longer side of that box
        return if (extent(points, best) >= extent(points, best + 90.0)) best else best + 90.0
    }

    /** How far [points] reach along the bearing [degrees] (clockwise from north), from the first to the last. */
    private fun extent(points: List<Plane>, degrees: Double): Double {
        val n = Plane(sin(degrees * PI / 180.0), cos(degrees * PI / 180.0))
        return points.maxOf { dot(it, n) } - points.minOf { dot(it, n) }
    }

    private fun dot(p: Plane, n: Plane): Double = p.x * n.x + p.y * n.y

    /**
     * The place along the sweep, between [low] and [high], where the area of the plot behind the line is [target]. The area grows with the
     * place, so it is found by halving the interval it is in.
     */
    private fun cutAt(parts: List<List<List<Plane>>>, sweep: Plane, frame: LocalFrame, low: Double, high: Double, target: Double): Double {
        var lo = low
        var hi = high
        repeat(BISECTIONS) {
            val middle = (lo + hi) / 2.0
            if (areaBehind(parts, sweep, frame, middle) < target) lo = middle else hi = middle
            if (hi - lo < 1e-10) return (lo + hi) / 2.0
        }
        return (lo + hi) / 2.0
    }

    /** The area, on the ellipsoid, of the plot that lies behind the line at [offset] along [sweep]: a part's outline cut off there, less its holes cut off there. */
    private fun areaBehind(parts: List<List<List<Plane>>>, sweep: Plane, frame: LocalFrame, offset: Double): Double {
        var total = 0.0
        for (part in parts) {
            total += areaOf(clip(part.first(), sweep, offset), frame)
            for (hole in part.drop(1)) total -= areaOf(clip(hole, sweep, offset), frame)
        }
        return total
    }

    private fun areaOf(ring: List<Plane>, frame: LocalFrame): Double = if (ring.size < 3) 0.0 else Measure.area(ring.map { frame.toGeo(it) })

    /**
     * [ring] cut off along the line at [offset] (Sutherland-Hodgman, one line): what is on the side where `dot(point, sweep) <= offset`. For a
     * ring that is not convex the result can have edges that run back along the line itself, which enclose nothing and so add nothing to the area.
     */
    private fun clip(ring: List<Plane>, sweep: Plane, offset: Double): List<Plane> {
        val out = ArrayList<Plane>(ring.size + 2)
        for (i in ring.indices) {
            val current = ring[i]
            val previous = ring[(i + ring.size - 1) % ring.size]
            val currentIn = dot(current, sweep) <= offset
            val previousIn = dot(previous, sweep) <= offset
            if (currentIn) {
                if (!previousIn) out += crossing(previous, current, sweep, offset)
                out += current
            } else if (previousIn) {
                out += crossing(previous, current, sweep, offset)
            }
        }
        return out
    }

    private fun crossing(a: Plane, b: Plane, sweep: Plane, offset: Double): Plane {
        val da = dot(a, sweep)
        val db = dot(b, sweep)
        val s = if (db == da) 0.0 else (offset - da) / (db - da)
        return Plane(a.x + (b.x - a.x) * s, a.y + (b.y - a.y) * s)
    }

    /**
     * A big rectangle that lies beyond the cut line at [offset] on the side [side] (+1 after it along the sweep, -1 before it), in latitude and
     * longitude: what is cut out of the plot to leave what is on the other side.
     */
    private fun halfPlane(frame: LocalFrame, sweep: Plane, offset: Double, side: Double, reach: Double): DrawnPolygon {
        val along = Plane(-sweep.y, sweep.x) // the way the cut line runs
        val centre = Plane(sweep.x * offset, sweep.y * offset)
        fun corner(a: Double, depth: Double) =
            frame.toGeo(Plane(centre.x + along.x * a + sweep.x * side * depth, centre.y + along.y * a + sweep.y * side * depth))
        val corners = listOf(corner(-reach, 0.0), corner(reach, 0.0), corner(reach, reach), corner(-reach, reach))
        return DrawnPolygon(
            EntityId("cutter"), ShapeStyle.SolidColor(ColorRgba(0, 0, 0)),
            corners.mapIndexed { i, p -> Vertex(VertexId("cutter-$i"), p) },
        )
    }

    private fun cutOff(
        rings: List<List<Vertex>>, like: DrawnPolygon, cutter: DrawnPolygon, overlay: OverlayEngine, newVertexId: () -> VertexId,
    ): List<List<Vertex>> = if (rings.isEmpty()) rings else overlay.difference(shapeOf(rings, like), cutter, newVertexId)

    /** The polygon that has [rings] (the outline of each part followed by its holes) and the look of [like]. */
    private fun shapeOf(rings: List<List<Vertex>>, like: DrawnPolygon): DrawnPolygon =
        like.copy(vertices = rings.first(), extraRings = rings.drop(1))
}
