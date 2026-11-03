package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnMarker
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.GreatCircle
import com.tcsappdev.worldwinddraw.core.geometry.OutlineWalk
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How a vehicle's trip along a route looks, for [RouteMotion].
 *
 * @property lineColor the colour of the line left behind the vehicle.
 * @property headColor the colour of the line right behind the vehicle; null for [lineColor] made lighter.
 * @property lineWidth the width of that line, in screen pixels.
 * @property trailShare how much of the way (0..1 of the route's length) the line behind the vehicle takes to cool from [headColor] to
 * [lineColor]; 0 for a line of one colour.
 * @property trailBands the number of pieces the cooling part is cut into; more is smoother and costs more shapes.
 * @property glow how much the line shines, like a neon tube: 0 for a plain line, 1 for a halo round it and a hot core, more for stronger.
 * @property vehicle the look of the vehicle (an [ShapeStyle.ImagePattern] with a picture of it, usually); null for none, only the line.
 * @property vehicleSize the size of the vehicle, in screen pixels.
 * @property vehicleTurn degrees added to the direction the vehicle goes in, for a picture that does not point north (a picture of a car
 * that points to the right has 90 here: pointing east is north turned 90).
 * @property height how high each leg arches up into the air, as a share of the leg's length (0.15: a leg of 1,000 km goes up to 150 km in
 * the middle, up from the ground at its first point and down to it at the last); 0 for a trip along the ground. It is the altitude of the
 * line and of the vehicle, so it is seen when the map is tilted, and the vehicle is nearer the eye (bigger) up there. Flights are a lot
 * lower than this against their length: the arch is made big to be seen.
 * @property arc how much each leg of the route bulges to the side, as a share of the leg's length (0.25: the middle of the leg is a quarter
 * of its length off the straight line); 0 for a straight (great-circle) leg, negative to bulge to the other side.
 * @property arcSteps how many pieces each arching or bulging leg is drawn in (more is smoother and costs more points).
 * @property lift how much bigger the vehicle is drawn the higher it is, on top of what being nearer makes it: 0.4 is 40% bigger at the top of
 * an arch; 0 for none.
 * @property shadow the look of the vehicle's shadow on the ground, which stays on the ground under it while the vehicle rises, moving off
 * to one side the higher it goes; null for none. It is turned like the vehicle, so it wants the same picture in a dark, see-through colour.
 * @property keepTrail whether the line is still there when the trip is over.
 * @property keepVehicle whether the vehicle is still there, at the end of the route, when the trip is over.
 * @property zIndex the drawing order of what is made, over the other shapes with a bigger number.
 */
public data class RouteOptions(
    val lineColor: ColorRgba = ColorRgba(255, 60, 20),
    val headColor: ColorRgba? = null,
    val lineWidth: Float = 3f,
    val trailShare: Float = 0.3f,
    val trailBands: Int = 10,
    val glow: Float = 1.5f,
    val vehicle: ShapeStyle? = null,
    val vehicleSize: Float = 32f,
    val vehicleTurn: Float = 0f,
    val height: Float = 0.15f,
    val arc: Float = 0f,
    val arcSteps: Int = 32,
    val lift: Float = 0f,
    val shadow: ShapeStyle? = null,
    val keepTrail: Boolean = true,
    val keepVehicle: Boolean = false,
    val zIndex: Int = 100,
) {
    init {
        require(lineWidth > 0f) { "lineWidth must be greater than 0" }
        require(trailShare >= 0f && trailShare <= 1f) { "trailShare must be between 0 and 1" }
        require(trailBands >= 1) { "trailBands must be at least 1" }
        require(glow >= 0f) { "glow must not be negative" }
        require(vehicleSize > 0f) { "vehicleSize must be greater than 0" }
        require(vehicleTurn.isFinite()) { "vehicleTurn must be a number" }
        require(height.isFinite() && height >= 0f) { "height must not be negative" }
        require(arc.isFinite() && arc >= -1f && arc <= 1f) { "arc must be between -1 and 1" }
        require(arcSteps >= 2) { "arcSteps must be at least 2" }
        require(lift.isFinite() && lift >= 0f) { "lift must not be negative" }
    }
}

/**
 * A vehicle (a plane, a car, a ship) going along a route, and the line it leaves behind: what the screen shows at a moment of the trip.
 *
 * The route goes through its points in order. Each leg is the great-circle arc between its two points (as WorldWind draws the edges of a
 * shape) arching up into the air ([RouteOptions.height]: the altitude of the line and the vehicle, which are on the ground at the points
 * of the route and highest in the middle of a leg), as a flight is drawn on a map; it can be bent sideways too ([RouteOptions.arc]). The
 * vehicle goes at an even speed along the whole of it: the same on a long leg as on a short one. It turns to the direction it is going in
 * (see [DrawnMarker.rotation]), which changes along a leg that is bent sideways, and at each point where the route turns. Its shadow
 * ([RouteOptions.shadow]) is on the ground, under it, and moves off to one side the higher it is.
 *
 * The line it leaves is drawn like a neon tube that is light just behind the vehicle and cools the further back it is (see
 * [RouteOptions]). When the trip is over (`t` is 1) there is the line left, with no cooling, and the vehicle if it is kept: what
 * [RouteOptions.keepTrail] and [RouteOptions.keepVehicle] say.
 *
 * It is a function of `t` (0 at the first point, 1 at the last) and not of time, like [Morph], so a host can play it, rewind it or scrub
 * it. What it makes has ids made from the route's `id`: `<id>#trail` for the line (and `<id>#trail.<band>`, with `~...` added for its light, see
 * [DrawOutline]), `<id>#vehicle` for the vehicle and `<id>#shadow` for its shadow.
 */
public object RouteMotion {
    /** The radius of the globe the heights are measured against, in meters. */
    private const val EARTH_RADIUS = 6_371_000.0

    /**
     * The route as the vehicle goes it: every leg bent into its arch and bulge, as a list of points close enough to look smooth, and where in
     * that list each point of [route] is (the first is 0). With no [arc] and no [height] the legs stay the straight great-circle arcs, which
     * is the route itself.
     */
    internal fun curve(route: List<GeoPosition>, arc: Float, height: Float, steps: Int): Pair<List<GeoPosition>, List<Int>> {
        if (arc == 0f && height == 0f) return route to route.indices.toList()
        val points = ArrayList<GeoPosition>()
        val corners = ArrayList<Int>()
        points += route.first()
        corners += 0
        for (leg in 0 until route.size - 1) {
            val from = route[leg]
            val to = route[leg + 1]
            val length = GreatCircle.angleBetween(from, to)
            if (length > 1e-9) {
                for (n in 1 until steps) {
                    val share = n.toDouble() / steps
                    val bend = sin(PI * share)
                    var place = GreatCircle.pointAt(from, to, share)
                    if (arc != 0f) {
                        // To the right of the way the vehicle is going (to the left for a negative arc), most in the middle of the leg.
                        place = GreatCircle.destination(place, GreatCircle.bearing(place, to) + PI / 2, arc * length * bend)
                    }
                    // Up in the air, most in the middle of the leg.
                    points += place.copy(altitude = place.altitude + height * length * EARTH_RADIUS * bend)
                }
            }
            points += to
            corners += points.lastIndex
        }
        return points to corners
    }

    /** The route's shapes at [t] (0 the vehicle at the first point, 1 the trip is over); [route] needs at least 2 points. */
    public fun at(id: EntityId, route: List<GeoPosition>, t: Float, options: RouteOptions = RouteOptions()): List<DrawableEntity> {
        require(route.size >= 2) { "a route needs at least 2 points" }
        require(!t.isNaN()) { "t is not a number" }
        val (points, corners) = curve(route, options.arc, options.height, options.arcSteps)
        val walk = OutlineWalk(points, closed = false)
        val done = t >= 1f
        val drawn = t.coerceIn(0f, 1f).toDouble()

        val style = NeonStyle(
            options.lineColor, options.headColor ?: lighter(options.lineColor, 0.55f), options.lineWidth,
            if (done) 0f else options.trailShare, options.trailBands, options.glow,
        )
        val shown = { color: ColorRgba, opacity: Float ->
            ShapeStyle.SolidColor(color.copy(alpha = (color.alpha * opacity).roundToInt().coerceIn(0, 255)))
        }

        val result = ArrayList<DrawableEntity>()
        if (!done || options.keepTrail) {
            val halos = ArrayList<DrawableEntity>()
            val bodies = ArrayList<DrawableEntity>()
            neonTrail("${id.id}#trail", walk, drawn, style, options.zIndex, shown, halos, bodies)
            result += halos
            result += bodies
        }
        val vehicle = options.vehicle
        if (vehicle != null && (!done || options.keepVehicle)) {
            val heading = walk.bearingAt(drawn) * 180.0 / PI + options.vehicleTurn
            val turn = (((heading % 360.0) + 360.0) % 360.0).toFloat()
            val position = walk.pointAt(drawn)
            // How high it is: 0 at each point of the route, 1 half way along a leg.
            val rise = rise(walk, corners, drawn)
            val shadow = options.shadow
            if (shadow != null) {
                // The shadow is on the ground, to the south-east, further from the vehicle the higher it is.
                val away = position.altitude / EARTH_RADIUS * 0.5
                result += DrawnMarker(
                    EntityId("${id.id}#shadow"), shadow, GreatCircle.destination(position.copy(altitude = 0.0), 3 * PI / 4, away), options.vehicleSize,
                    zIndex = options.zIndex + 9, rotation = turn,
                )
            }
            result += DrawnMarker(
                EntityId("${id.id}#vehicle"), vehicle, position, options.vehicleSize * (1f + options.lift * rise.toFloat()),
                zIndex = options.zIndex + 10, rotation = turn,
            )
        }
        return result
    }

    /** The leg the vehicle is on at [drawn], as its first and last corner in the walk. */
    private fun legAt(corners: List<Int>, walk: OutlineWalk, drawn: Double): Pair<Int, Int> {
        var leg = 0
        while (leg < corners.size - 2 && walk.fractionAtCorner(corners[leg + 1]) <= drawn) leg++
        return corners[leg] to corners[leg + 1]
    }

    private fun rise(walk: OutlineWalk, corners: List<Int>, drawn: Double): Double {
        val (first, last) = legAt(corners, walk, drawn)
        val start = walk.fractionAtCorner(first)
        val end = walk.fractionAtCorner(last)
        if (end <= start) return 0.0
        return sin(PI * ((drawn - start) / (end - start)).coerceIn(0.0, 1.0))
    }
}
