# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows [Semantic Versioning](https://semver.org/) (until 1.0, a minor version may change the API).

## [0.2.0] - 2026-11-06

### Added
- **`measure`** (new module, `io.github.korugan32:worldwind-draw-measure`):
  - `Measure`: geodesic distance (Vincenty), length, perimeter and **area on the WGS84 ellipsoid** (a ball of the mean radius is out by 0.1% in Turkey and 0.4% at the equator), for a ring or for a polygon with holes and parts. `AreaUnit`: square metre, dönüm (1,000 m²), hectare, acre, square kilometre.
  - `ShareSplit`: divides a plot into shares of given areas ("a half and two quarters"): parallel cuts found by bisection on the true area, pieces cut by an `OverlayEngine` (so a plot that is not convex, or has holes or parts, is divided right); the shares agree with each other to ten millionths. The cuts run across the long way of the plot by default.
  - `Setback.buildable`: the part of a plot where a building may stand for a given setback distance (a hole grows by the same distance).
  - `Zoning`: taks (taban alanı katsayısı) and emsal (kaks) arithmetic, as a simulation.
- **`core`**: `InsetEngine` (a new interface that takes a strip of a given width off the inside of a polygon). `Wgs84` and `LocalFrame` are shared internals (`@InternalWorldWindDrawApi`).
- **`boolean-ops`**: `JtsOverlayEngine` is also an `InsetEngine` (`PolygonOverlay.inset`), done on a flat map in metres with mitred corners.

### Notes
- For exact shares give `ShareSplit` a `JtsOverlayEngine(gridSize = 0.0)`: the default rounds corners to 1e-7 degrees (about a centimetre).
- A plot that crosses the antimeridian (longitude 180) cannot be divided or set back (the overlay works on longitude and latitude as a flat plane); `Measure` still measures it.
- An id maker that keeps giving the same id now makes `inset` throw instead of looping for ever.
- No public API was removed.

## [0.1.0] - 2026-11-04

First release, on Maven Central as `io.github.korugan32:worldwind-draw-{core,adapter,serialization,boolean-ops,morph}:0.1.0`.

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
