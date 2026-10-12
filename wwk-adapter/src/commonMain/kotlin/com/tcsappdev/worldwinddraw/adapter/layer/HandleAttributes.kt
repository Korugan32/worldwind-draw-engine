package com.tcsappdev.worldwinddraw.adapter.layer

import earth.worldwind.render.Color
import earth.worldwind.render.image.ImageSource
import earth.worldwind.shape.PlacemarkAttributes

/**
 * Attributes for a handle-style dot whose visible disc is [diameter] pixels across: [image]
 * scaled so its disc comes out at that size or — where the platform has no handle image (see
 * `HandleImages.kt`) — a plain [fallbackColor] square [diameter] pixels on a side.
 */
internal fun handleAttributes(image: ImageSource?, fallbackColor: Color, diameter: Float): PlacemarkAttributes =
    if (image != null) PlacemarkAttributes.createWithImage(image).apply { imageScale = diameter / HANDLE_IMAGE_DISC_DIAMETER }
    else PlacemarkAttributes().apply { imageColor = fallbackColor; imageScale = diameter.toDouble() }
