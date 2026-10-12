package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import earth.worldwind.render.image.ImageSource

// Not implemented on this platform yet, so DrawLayer falls back to plain square handles.
// Round "+" handles are Android-only until the other targets get verified (Faz 10).
internal actual fun vertexHandleImage(ring: ColorRgba, fill: ColorRgba): ImageSource? = null

internal actual fun midpointHandleImage(ring: ColorRgba, fill: ColorRgba, plus: ColorRgba): ImageSource? = null
