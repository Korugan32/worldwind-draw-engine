package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolygon
import com.tcsappdev.worldwinddraw.core.geometry.OutlineWalk
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle
import kotlin.math.roundToInt

/**
 * How a polygon's outline is drawn by [DrawOutline].
 *
 * @property drawShare how much of the effect (0..1 of its time) the outline takes to be drawn; the rest is the fill coming in. It must be
 * more than 0 and less than 1.
 * @property startAt the corner the pen starts at, in the ring that has it (every ring starts at its first corner if this is null or is in
 * another ring).
 * @property reversed whether the pen goes the other way round, against the order of the corners.
 * @property lineColor the colour of the line the pen draws; null for the polygon's own outline colour (and [penColor] if it has none). As the
 * fill comes in the line takes the polygon's own outline colour, so the effect ends on exactly the polygon.
 * @property lineWidth the width of the line the pen draws, in screen pixels; null for the polygon's own outline width.
 * @property penColor the colour of the line the pen draws, when neither [lineColor] nor a plain outline colour of the polygon is given.
 * @property headColor the colour at the tip of the pen and in the line right behind it; null for the line's colour made lighter.
 * @property trailShare how much of the way round (0..1) the line behind the pen takes to cool from [headColor] to the line's own colour; the
 * line behind that is the line's own colour. 0 for a line of one colour.
 * @property trailBands the number of pieces the cooling part is cut into (each is one colour); more is smoother and costs more shapes.
 * @property glow how much the line shines, like a neon tube: 0 for a plain line, 1 for a halo round the line and a hot core;
 * more than 1 makes the light stronger and wider.
 */
public data class DrawOutlineOptions(
    val drawShare: Float = 0.7f,
    val startAt: VertexId? = null,
    val reversed: Boolean = false,
    val lineColor: ColorRgba? = null,
    val lineWidth: Float? = null,
    val penColor: ColorRgba = ColorRgba(0, 0, 0),
    val headColor: ColorRgba? = null,
    val trailShare: Float = 0.25f,
    val trailBands: Int = 10,
    val glow: Float = 1f,
) {
    init {
        require(drawShare > 0f && drawShare < 1f) { "drawShare must be more than 0 and less than 1" }
        require(lineWidth == null || lineWidth > 0f) { "lineWidth must be greater than 0" }
        require(trailShare >= 0f && trailShare <= 1f) { "trailShare must be between 0 and 1" }
        require(trailBands >= 1) { "trailBands must be at least 1" }
        require(glow >= 0f) { "glow must not be negative" }
    }
}

/**
 * A polygon that is drawn the way a pen would draw it: the outline is drawn round from a corner, and when it has gone all the way round
 * the fill comes in (fades in). What the screen shows at a moment of the effect.
 *
 * **The pen is a light:** the line is a neon tube (a wide soft halo, a coloured body and a hot, nearly white core) that is light at the pen
 * and cools to the outline's own colour the further behind it is (see [DrawOutlineOptions.glow] to turn it down or off, and
 * [DrawOutlineOptions.trailShare]).
 *
 * **While the outline is drawn** (the first [DrawOutlineOptions.drawShare] of the time) the polygon has no fill yet. Each of its rings has a
 * pen. All the pens are as far along as each other, so each goes at the speed that makes it come round when the others do (a long ring is
 * drawn faster than a short one, a hole is drawn with the outside). The distance is the length along the edges as WorldWind draws them,
 * see [OutlineWalk].
 *
 * **Then the fill fades in** under the full outline, until the last moment, when the shape is the polygon itself. A polygon with no outline
 * of its own has its drawn outline fade out while the fill comes in, so the effect ends on exactly the shape it started from.
 *
 * It is a function of `t` (0 the pen at the start, 1 the polygon) and not of time, like [Morph], so a host can play it, rewind it or
 * scrub it. The shapes it makes besides the polygon have ids made from the polygon's: `<id>#draw<ring>` for the cooled line (and
 * `<id>#draw<ring>.<band>` for each piece of the trail), with `~<layer>` added to the id of a layer of light round it. The vertices of a
 * line are `<its id>-<n>`.
 */
public object DrawOutline {
    public fun at(polygon: DrawnPolygon, t: Float, options: DrawOutlineOptions = DrawOutlineOptions()): List<DrawableEntity> {
        require(!t.isNaN()) { "t is not a number" }
        if (t >= 1f) return listOf(polygon)

        val drawn = (t / options.drawShare).coerceIn(0f, 1f).toDouble()
        val fill = ((t - options.drawShare) / (1f - options.drawShare)).coerceIn(0f, 1f)

        val ownColor = (polygon.outlineStyle as? ShapeStyle.SolidColor)?.color
        val lineColor = options.lineColor ?: ownColor ?: options.penColor
        // What the line turns into as the fill comes in: the polygon's own outline.
        val finalColor = ownColor ?: lineColor
        val style = NeonStyle(
            lineColor, options.headColor ?: lighter(lineColor, 0.55f), options.lineWidth ?: polygon.outlineWidth,
            options.trailShare, options.trailBands, options.glow,
        )
        // With no outline of its own the line is only there for the drawing, and goes as the fill comes.
        val lineFactor = if (polygon.outlineStyle == null) 1f - fill else 1f

        // The colour something has at this moment: once the outline is round, the light cools to the line's colour as the fill comes in.
        val shown = { color: ColorRgba, opacity: Float ->
            val c = lerpColor(color, finalColor, fill)
            ShapeStyle.SolidColor(c.copy(alpha = (c.alpha * lineFactor * opacity).roundToInt().coerceIn(0, 255)))
        }

        val result = ArrayList<DrawableEntity>()
        if (fill > 0f) result += polygon.copy(outlineStyle = null).faded(fill)
        val halos = ArrayList<DrawableEntity>()
        val bodies = ArrayList<DrawableEntity>()
        polygon.rings.forEachIndexed { index, ring ->
            val start = options.startAt?.let { id -> ring.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
            val walk = OutlineWalk(ring.map { it.position }, start, options.reversed)
            neonTrail("${polygon.id.id}#draw$index", walk, drawn, style, polygon.zIndex + 1, shown, halos, bodies)
        }
        result += halos
        result += bodies
        return result
    }
}
