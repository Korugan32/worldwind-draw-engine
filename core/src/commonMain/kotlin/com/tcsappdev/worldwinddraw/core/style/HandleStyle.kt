package com.tcsappdev.worldwinddraw.core.style

/**
 * How the editing handles look: the round dots on each vertex of a shape being edited, and
 * the smaller "+" dots on the middle of each edge that add a new vertex when dragged. Also
 * used for the dots shown on a shape while it is still being drawn.
 *
 * Both diameters are the **visible diameter in screen pixels** (not dp — multiply by the
 * display density yourself if you want a physical size). The area that actually reacts to a
 * finger is somewhat larger than the visible dot, so growing a diameter makes a handle both
 * easier to see and easier to grab.
 *
 * [touchTargetDiameter] is the size of the invisible area that actually grabs a handle. A
 * fingertip is much bigger than a 24px dot, and the map's pick only reacts to what is really
 * drawn (measured: ~12px around the dot's centre), so each handle gets a transparent square this
 * wide to be grabbed by. A handle is never grabbed by less than its own visible diameter.
 *
 * Keep [midpointDiameter] a bit smaller than [vertexDiameter]: a midpoint handle sits on the
 * shape's edge and its "+" is easy to confuse with a real vertex if the two look alike.
 *
 * Colours: [fillColor] is the disc, [ringColor] its outline, [plusColor] the "+" on a
 * midpoint handle. The defaults (white disc, near-black ring and "+") are chosen to stay
 * readable over any map imagery *and* over the highlight outline a handle sits on — if you
 * pick a [ringColor] close to your [HighlightStyle] colours, the dots will blend into the
 * outline and stop reading as separate points.
 *
 * Colours are applied where the platform can draw handle images (Android today); elsewhere
 * handles are plain [fillColor] squares and [ringColor]/[plusColor] have no effect.
 */
public data class HandleStyle(
    val vertexDiameter: Float = 27f,
    val midpointDiameter: Float = 24f,
    val touchTargetDiameter: Float = 48f,
    val fillColor: ColorRgba = ColorRgba(red = 255, green = 255, blue = 255),
    val ringColor: ColorRgba = ColorRgba(red = 16, green = 16, blue = 16),
    val plusColor: ColorRgba = ColorRgba(red = 16, green = 16, blue = 16),
    /** The fill of the handle of the vertex that is selected (the one [com.tcsappdev.worldwinddraw.core.state.DrawEvent.DeleteSelectedVertex] would remove). */
    val selectedFillColor: ColorRgba = ColorRgba(red = 255, green = 149, blue = 0),
    /** How many of a shape's vertices get a handle; see [HandleDetail]. */
    val detail: HandleDetail = HandleDetail.Adaptive,
) {
    init {
        require(vertexDiameter > 0f) { "vertexDiameter must be positive, was $vertexDiameter" }
        require(midpointDiameter > 0f) { "midpointDiameter must be positive, was $midpointDiameter" }
        require(touchTargetDiameter > 0f) { "touchTargetDiameter must be positive, was $touchTargetDiameter" }
    }

    /** The side of the grab area for a handle drawn [visibleDiameter] wide: [touchTargetDiameter], but never smaller than the handle itself. */
    public fun touchSizeFor(visibleDiameter: Float): Float = maxOf(touchTargetDiameter, visibleDiameter)
}

/** How many of a shape's vertices get an editing handle. */
public enum class HandleDetail {
    /**
     * Every vertex of a shape with few points; for one with many, seen from far away, only the vertices that matter at that
     * zoom (about 14 screen pixels apart), with the hidden ones between them following when a handle is dragged. More
     * handles appear as the camera comes closer, down to every vertex. Keeps a detailed outline editable.
     */
    Adaptive,

    /** A handle on every vertex whatever the zoom, so every point can be grabbed (and none moves unseen), at the price of clutter and speed on a detailed outline. */
    All,
}
