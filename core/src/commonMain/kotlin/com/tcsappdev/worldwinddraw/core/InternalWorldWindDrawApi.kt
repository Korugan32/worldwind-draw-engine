package com.tcsappdev.worldwinddraw.core

/**
 * Marks what is public only because the library's own modules use it from one another (the image rasteriser that `core` has and
 * `wwk-adapter` calls, the polygon helpers `boolean-ops` and `serialization` share). It is not part of the library's API: it can change or
 * go in any release, and a host app has no reason to use it. Using it needs an explicit opt-in (`@OptIn(InternalWorldWindDrawApi::class)`),
 * which is the sign that it is being used at one's own risk.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is internal to the world-wind-draw-engine modules: it is not a stable API and can change or go without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class InternalWorldWindDrawApi
