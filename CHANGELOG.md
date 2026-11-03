# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows [Semantic Versioning](https://semver.org/) (until 1.0, a minor version may change the API).

## [0.1.0] - not released yet

First release.

### Added
- **Drawing and editing** polygons (with holes and separate parts), polylines and markers on a WorldWind Kotlin globe: place points, drag corners, add a corner on an edge, move and resize markers; undo and redo that follow a drawing or editing session.
- **`WorldWindDrawEngine`**: one object that builds and connects the repository, history, state machine, map layer and touch input; `attachTo(window)` for Android, JVM, JS, Wasm and iOS.
- **Styling**: plain colours, hatch patterns, repeating images, images that fill a shape, outlines, marker labels, z-order.
- **Merge and cut** polygons (`boolean-ops`), simplify a shape.
- **Import and export**: JSON, GeoJSON, KML (`serialization`).
- **Morphing** between snapshots of a drawing (`morph`).
- **Effects**: `DrawOutline` (a polygon outline drawn by a neon pen, then the fill fades in) and `RouteMotion` (a vehicle along a route that arches into the air, with a trail and a shadow).
- `DrawnMarker.rotation`; an altitude above 0 on a line or a marker is that many metres above the ground.
- Platforms: Android (minSdk 29), iOS, web (`wasmJs`, and `js` without image fills), JVM.
- A closed public API (`explicitApi()`, `@InternalWorldWindDrawApi`) with an API dump checked on every build, and KDoc for all of it.

### Known limits
- Only tried on the iOS Simulator, not on a physical iPhone.
- No image fills on the `js` target; plain square edit handles on iOS and the web.
