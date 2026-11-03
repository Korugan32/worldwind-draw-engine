package com.tcsappdev.worldwinddraw.adapter.engine

import earth.worldwind.WorldWindow

/**
 * Puts the engine on [window]: adds its layer, gives it the touches on the map and lets it ask the window to draw again. WorldWind's
 * `WorldWindow` is a different class on each platform, so this is a function of each platform and the host's code on that platform calls it.
 */
fun WorldWindDrawEngine.attachTo(window: WorldWindow) {
    window.engine.layers.addLayer(layer)
    window.selectDragDetector.callback = input
    onRedrawNeeded(window::requestRedraw)
}
