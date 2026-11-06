# WorldWind Draw Engine

Draw, edit and animate shapes on a 3D globe, on Android, iOS and the web, from one Kotlin Multiplatform codebase.

It is a drawing layer for [WorldWind Kotlin](https://github.com/WorldWindEarth/WorldWindKotlin): a person taps the globe to draw polygons, lines and markers, drags their corners, merges and cuts them, undoes what they did, and the host app gets the shapes back as plain data (or as GeoJSON / KML). On top of that there are animation effects: a polygon outline drawn by a glowing pen, a plane flying a route, one drawing morphing into another.

> **Status: 0.2.0, on Maven Central.** The API is closed and checked on every build (see [Contributing](CONTRIBUTING.md)), but it has had little real-world use and has not been tried on a physical iPhone (see [Known limits](#known-limits)). Expect changes before 1.0: until then a minor version may change the API.

## What you get

- **Drawing and editing** of polygons (with holes and separate parts), polylines and markers: place points, drag corners, add a corner on the middle of an edge, move and resize markers.
- **Undo / redo** that follows a drawing or editing session.
- **Styling:** plain colours, hatch patterns, repeating images, images that fill a shape (a flag in a country), outlines, marker labels, z-order.
- **Merge and cut** polygons (boolean operations), and simplify a shape to fewer points.
- **Measuring and dividing land:** lengths and areas on the WGS84 ellipsoid (with the dönüm), a plot divided into shares of given areas, the part left by a setback, taks and emsal.
- **Import / export:** JSON, GeoJSON and KML.
- **Morphing:** take a snapshot of a drawing, change it, and play the change as an animation (shapes move, grow, shrink or cross-fade).
- **Effects:** draw a polygon's outline with a neon pen and fade the fill in; fly a vehicle along a route that arches into the air, with a trail and a shadow.
- **One object to set it up:** `WorldWindDrawEngine` builds and connects everything below.

## Modules

| Module | What it is | Depends on |
|---|---|---|
| `core` | The model (shapes, styles), the engine's state and events, undo history, geometry. No map, no UI. | nothing |
| `wwk-adapter` | `WorldWindDrawEngine`, the map layer and the touch input, on top of WorldWind Kotlin. | `core`, WorldWind |
| `serialization` | JSON, GeoJSON and KML. | `core` |
| `boolean-ops` | Merging and cutting polygons. | `core`, [kts-core](https://github.com/mipastgt/kts) |
| `morph` | Snapshots, transitions and the effects. | `core` |
| `measure` | Lengths, areas and units on the WGS84 ellipsoid; dividing a plot into shares; setbacks; taks and emsal. | `core` |

Artifacts (group `io.github.korugan32`, from Maven Central): `worldwind-draw-core`, `worldwind-draw-adapter`, `worldwind-draw-serialization`, `worldwind-draw-boolean-ops`, `worldwind-draw-morph`, `worldwind-draw-measure`.

```kotlin
dependencies {
    implementation("io.github.korugan32:worldwind-draw-adapter:0.2.0")
    implementation("io.github.korugan32:worldwind-draw-boolean-ops:0.2.0")   // only to merge and cut polygons
    implementation("io.github.korugan32:worldwind-draw-serialization:0.2.0") // only for GeoJSON / KML / JSON
    implementation("io.github.korugan32:worldwind-draw-morph:0.2.0")         // only for morphing and effects
    implementation("io.github.korugan32:worldwind-draw-measure:0.2.0")       // only for lengths, areas, dividing land, setbacks
}
```

## Quick start

```kotlin
// A Compose Multiplatform screen (what the demo does). A host without Compose does the same three things.
val engine = remember { WorldWindDrawEngine(scope, overlay = JtsOverlayEngine()) }   // 1. build it

LaunchedEffect(engine) {                                                             // 2. give it the clock
    var last = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        engine.tick((now - last) / 1_000_000)
        last = now
    }
}

WorldWindow(
    state = rememberWorldWindowState { /* your own layers */ },
    onCreated = { window -> engine.attachTo(window) },                               // 3. put it on the map
)

// Your own buttons:
val state by engine.state.collectAsState()
when (val mode = state.mode) {
    is DrawMode.Draw -> { Button("Finish") { engine.finishDrawing() }; Button("Cancel") { engine.cancel() } }
    is DrawMode.Edit -> Button("Done") { engine.stopEditing() }
    is DrawMode.Select -> { Button("Edit") { engine.edit(mode.entityId) }; Button("Delete") { engine.deleteSelected() } }
    else -> Button("Draw") { engine.startDrawing(DrawableEntityType.Polygon) }
}
```

A person then taps the globe to place points, taps **Finish**, taps a shape to select it, drags its corners to edit it. `engine.undo()` / `engine.redo()` and the flows `engine.state`, `engine.entities`, `engine.canUndo`, `engine.canRedo` are for the host's UI.

The engine has no clock of its own: call `engine.tick(deltaMs)` once per frame, or the selected shape does not pulse and effects do not move.

Playing an effect:

```kotlin
var effect: DrawOutlineEffect? = null
effect = DrawOutlineEffect(engine.repository, polygon, AnimationSpec(6000, easing = { it })) { effect?.let(engine::stop) }
engine.play(effect)
```

Reading and writing files:

```kotlin
val json = DrawJson.encode(engine.entities.value)
val imported = Kml.decode(kmlText)       // imported.entities, imported.warnings
```

Measuring and dividing land (the `measure` module):

```kotlin
val area = Measure.area(plot)                                  // square metres, on the WGS84 ellipsoid
val donum = AreaUnit.Donum.fromSquareMeters(area)             // 1 dönüm = 1,000 m²
val metres = Measure.perimeter(plot.vertices.map { it.position })

// Three heirs, a half and two quarters; the cuts run across the long way of the plot.
val shares = ShareSplit.split(plot, listOf(0.5, 0.25, 0.25), JtsOverlayEngine(gridSize = 0.0), newVertexId = { VertexId(uuid()) })
shares.pieces.forEach { println("${it.area} m² (wanted ${it.targetArea})") }

// Where a building may stand with a 5 m setback, and what the zoning allows (taks 0.30, emsal 0.60):
val buildable = Setback.buildable(plot, 5.0, JtsOverlayEngine(), newVertexId = { VertexId(uuid()) })
val allowance = Zoning.allowance(area, ZoningLimits(taks = 0.30, kaks = 0.60), buildableArea = buildable.area)
```

The full API is documented in the KDoc: [the API reference](https://korugan32.github.io/worldwind-draw-engine/), or build it yourself with `./gradlew dokkaGenerate` and open `build/dokka/html/index.html`.

## Platforms

| Target | Library | Checked |
|---|---|---|
| Android (minSdk 29) | yes | on a phone |
| Web (`wasmJs`) | yes | Chromium and Safari on an iPhone |
| iOS (`iosArm64`, `iosSimulatorArm64`) | yes | on the iOS Simulator only |
| JVM | yes | unit tests |
| Web (`js`) | yes, without image fills | compiles only |

Built with Kotlin 2.4, WorldWind Kotlin 2.1.1 and Compose Multiplatform 1.12 (the demo). `worldwind-draw-adapter` brings WorldWind Kotlin in by itself.

## Known limits

- **iOS has only been tried on the Simulator.** There WorldWind's OpenGL ES runs on the CPU, so it is slow (about 2 fps while a shape is selected) and the atmosphere layer draws black triangles; that is the Simulator, not the library, but how it behaves on a real iPhone is **not measured yet**.
- **Image fills are missing on the `js` target.**
- On iOS and the web the edit handles are plain squares (Android has proper handle images).
- **`attachTo(window)` is a function of each platform**, because WorldWind's `WorldWindow` is a different class on each platform. In shared Compose code the IDE may show it red; the Gradle build is fine.
- An altitude above 0 on a line or a marker means that many metres **above the ground** (this is how a flying route arches); at 0 they lie on the ground.
- **On the web** the WorldWind data files must be served at the root of the site, and Safari and Firefox need a small workaround for `navigator.deviceMemory`: both are done in [the demo's `index.html`](demo-multiplatform/src/webMain/resources/index.html) and its `build.gradle.kts`.
- A tile server for zooming in is the host's choice. `tile.openstreetmap.org` is not meant for apps (see its [usage policy](https://operations.osmfoundation.org/policies/tiles/)); use a provider with a key, or your own server.

## Try the demo

```bash
./gradlew :demo-multiplatform:wasmJsBrowserDevelopmentRun        # the web, at http://localhost:8080
./gradlew :demo:installDebug                                     # Android
demo-multiplatform/scripts/ios-sim-run.sh                        # the iOS Simulator (see the script)
```

The demo has Draw / Edit / Delete / Undo, an Outline effect, a Route effect (a plane), and three maps to zoom in on.

## License

[Apache License 2.0](LICENSE). It depends on [WorldWind Kotlin](https://github.com/WorldWindEarth/WorldWindKotlin) (Apache 2.0), and `boolean-ops` on [kts-core](https://github.com/mipastgt/kts), a port of JTS, which is dual-licensed EPL 2.0 / EDL 1.0. See [NOTICE](NOTICE).
