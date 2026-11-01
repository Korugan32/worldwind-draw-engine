package com.tcsappdev.worldwinddraw.demomp

import earth.worldwind.draw.DrawableLambda
import earth.worldwind.layer.AbstractLayer
import earth.worldwind.render.RenderContext
import kotlin.time.TimeSource

/**
 * Counts the frames the engine really draws (FpsMeter counts those of Compose, and the engine draws nothing while the scene is still) and
 * times them in three parts: setting the scene up (the layers' render, on the CPU), the start of the draw and the rest of the draw.
 * Put it twice, first and last (see [Last]), so the draw between the two is what it times.
 */
class FrameCounter(private val peer: FrameCounter? = null) : AbstractLayer("Frame counter") {
    var frames = 0
        private set

    // Pick frames are drawn too; they are counted apart, since a tap or a drag draws one inline.
    var pickFrames = 0
        private set

    var lastDrawMs = 0L
        private set
    var lastRenderGapMs = 0L
        private set
    var worstDrawMs = 0L
        private set

    private var renderStart = TimeSource.Monotonic.markNow()
    private var drawStart = TimeSource.Monotonic.markNow()
    private var lastFrameEnd = TimeSource.Monotonic.markNow()

    private val drawable = DrawableLambda { dc ->
        if (dc.isPickMode) pickFrames++ else frames++
        drawStart = TimeSource.Monotonic.markNow()
        if (peer != null) {
            // Last in the draw: the time since the first one is the draw's.
            peer.lastDrawMs = peer.drawStart.elapsedNow().inWholeMilliseconds
            if (peer.lastDrawMs > peer.worstDrawMs) peer.worstDrawMs = peer.lastDrawMs
        }
    }

    override fun doRender(rc: RenderContext) {
        if (peer == null) {
            lastRenderGapMs = renderStart.elapsedNow().inWholeMilliseconds
            renderStart = TimeSource.Monotonic.markNow()
        }
        rc.offerBackgroundDrawable(drawable)
    }
}
