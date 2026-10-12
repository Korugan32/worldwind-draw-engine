package com.tcsappdev.worldwinddraw.adapter.layer

import com.tcsappdev.worldwinddraw.core.style.ColorRgba
import earth.worldwind.render.image.ImageSource

/**
 * The icons [DrawLayer] draws its edit handles with: [vertexHandleImage] is a round vertex handle,
 * [midpointHandleImage] a round midpoint handle with a "+" in it. Each returns `null` where this platform doesn't provide one,
 * in which case [DrawLayer] falls back to a plain coloured square.
 *
 * This is an `expect`/`actual` because WWK's [ImageSource] has no cross-platform way to hand
 * it pixels: `fromUrlString` is http-only on Android (no `data:` URIs), and the in-memory
 * variants (`Bitmap`, `ImageFactory`, ...) are each a different platform type. Each image is
 * built once per colour combination and shared — WWK compares an [ImageSource] by reference,
 * so a fresh one per rebuild would defeat its texture cache.
 *
 * Both are drawn at twice their on-screen size so they stay crisp; [DrawLayer] scales them
 * back down with `imageScale`.
 */
internal expect fun vertexHandleImage(ring: ColorRgba, fill: ColorRgba): ImageSource?

/** See [vertexHandleImage]. [plus] is the "+" in the middle. */
internal expect fun midpointHandleImage(ring: ColorRgba, fill: ColorRgba, plus: ColorRgba): ImageSource?

/**
 * The diameter, in the handle image's own pixels, of the visible disc inside each handle
 * image. The images are 48px squares with a 34px disc, so [DrawLayer] scales them by
 * `desiredDiameter / HANDLE_IMAGE_DISC_DIAMETER` to make the disc come out at exactly the
 * diameter [com.tcsappdev.worldwinddraw.core.style.HandleStyle] asks for.
 */
internal const val HANDLE_IMAGE_DISC_DIAMETER = 34.0
