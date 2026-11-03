package com.tcsappdev.worldwinddraw.morph

import com.tcsappdev.worldwinddraw.core.geometry.DrawableEntity
import com.tcsappdev.worldwinddraw.core.geometry.DrawnPolyline
import com.tcsappdev.worldwinddraw.core.geometry.EntityId
import com.tcsappdev.worldwinddraw.core.geometry.GeoPosition
import com.tcsappdev.worldwinddraw.core.geometry.OutlineWalk
import com.tcsappdev.worldwinddraw.core.geometry.Vertex
import com.tcsappdev.worldwinddraw.core.geometry.VertexId
import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import com.tcsappdev.worldwinddraw.core.style.ShapeStyle

// The line a pen leaves behind it, drawn like a neon tube: shared by the outline effect and the route effect.

/** How the line looks: see [DrawOutlineOptions] for what each of these means. */
internal class NeonStyle(
    val lineColor: ColorRgba,
    val headColor: ColorRgba,
    val width: Float,
    val trailShare: Float,
    val trailBands: Int,
    val glow: Float,
)

// The light round a line is many wide strokes laid over each other, from the widest and faintest to the narrowest: where they all
// overlap (the middle) it is strongest and it fades smoothly out, as a glow does. How many times as wide as the line, and the opacity.
private val HALOS = List(7) { n -> (9.0f - 1.1f * n) to (0.03f + 0.014f * n) }
private const val CORE_WIDTH = 0.4f
private const val CORE_WHITENESS = 0.65f

/** [color] [towards] of the way to white: what a light is when it is hot. */
internal fun lighter(color: ColorRgba, towards: Float) = lerpColor(color, ColorRgba(255, 255, 255, color.alpha), towards)

/**
 * Adds to [halos] and [bodies] the line the pen of [walk] has left when it is [drawn] (0..1) of the way: the light round the whole line, a
 * body that is [NeonStyle.headColor] at the pen and cools to [NeonStyle.lineColor] behind it, and a hot core. [shown] turns a colour and an
 * opacity into what is put on the shape (the effect decides how it fades). The shapes are `<name>` for the cooled part of the body,
 * `<name>.<band>` for each band of the trail, `~c` added for a core and `<name>~h<layer>` for a layer of light; [zIndex] is for the lowest.
 */
internal fun neonTrail(
    name: String, walk: OutlineWalk, drawn: Double, style: NeonStyle, zIndex: Int,
    shown: (ColorRgba, Float) -> ShapeStyle.SolidColor,
    halos: MutableList<DrawableEntity>, bodies: MutableList<DrawableEntity>,
) {
    fun line(target: MutableList<DrawableEntity>, id: String, path: List<GeoPosition>, color: ShapeStyle.SolidColor, width: Float, z: Int) {
        if (path.size < 2 || color.color.alpha == 0) return
        target += DrawnPolyline(
            EntityId(id), color, path.mapIndexed { n, position -> Vertex(VertexId("$id-$n"), position) },
            lineWidth = width, zIndex = z,
        )
    }

    // The light round the whole line drawn so far, in the line's colour (it does not change along the trail).
    if (style.glow > 0f) {
        val soFar = walk.pathTo(drawn)
        HALOS.forEachIndexed { n, (times, opacity) ->
            line(halos, "$name~h$n", soFar, shown(style.lineColor, (opacity * style.glow).coerceAtMost(1f)), style.width * times * (0.6f + 0.4f * style.glow), zIndex)
        }
    }

    // A piece of the line: its body, and its hot core.
    fun piece(suffix: String, path: List<GeoPosition>, color: ColorRgba) {
        line(bodies, name + suffix, path, shown(color, 1f), style.width, zIndex + 1)
        if (style.glow > 0f) line(bodies, "$name$suffix~c", path, shown(lighter(color, CORE_WHITENESS), 1f), style.width * CORE_WIDTH, zIndex + 2)
    }

    // The part that has cooled is one line in the line's colour; the part behind the pen is cut into bands, hottest at the tip.
    val cooled = if (style.trailShare > 0f) (drawn - style.trailShare).coerceAtLeast(0.0) else drawn
    piece("", walk.pathTo(cooled), style.lineColor)
    if (cooled < drawn) {
        for (k in 0 until style.trailBands) {
            val from = cooled + (drawn - cooled) * k / style.trailBands
            val to = cooled + (drawn - cooled) * (k + 1) / style.trailBands
            piece(".$k", walk.pathBetween(from, to), lerpColor(style.lineColor, style.headColor, (k + 0.5f) / style.trailBands))
        }
    }
}
