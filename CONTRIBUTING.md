# Contributing

Thank you for looking. This is a small project, so the rules are short.

## What you need

- JDK 21 (Gradle can fetch one), the Android SDK, and **a Mac for the iOS targets** (the iOS Simulator and the iOS frameworks need Xcode's tools). Without a Mac you can still build and test the rest.
- Memory: the Gradle daemon is set to 6 GB (`gradle.properties`); linking the iOS release frameworks of the demo needs it. A machine with 16 GB is comfortable.

## Build and test

```bash
./gradlew build                      # everything, all targets (about 15 minutes the first time)
./gradlew :core:jvmTest              # one module's unit tests, in seconds
./gradlew :morph:jvmTest :wwk-adapter:jvmTest
```

Most logic is in `commonMain` and is tested with plain unit tests (`commonTest` / `jvmTest`); what needs a real map is tried in the demo.

### The demo

```bash
./gradlew :demo-multiplatform:wasmJsBrowserDevelopmentRun   # web, http://localhost:8080
./gradlew :demo:installDebug                                # Android
demo-multiplatform/scripts/ios-sim-run.sh                   # iOS Simulator
```

## The public API is closed on purpose

- Every module uses `explicitApi()`: a public declaration has to say `public` and its return type, and **every public declaration needs KDoc**.
- What the library's own modules share between themselves but a host has no use for is marked `@InternalWorldWindDrawApi` (it needs an explicit opt-in). What only one module uses is `internal`.
- The public API is written down in `<module>/api/` (a `.api` dump for the JVM and a `.klib.api` dump for iOS, JS and Wasm), and `checkKotlinAbi` runs as part of `check`. **If you change the public API the build fails until you run `./gradlew updateKotlinAbi`** and commit the changed dump: that makes the change visible in the review. Open something up only when it is needed: opening is not a breaking change, closing is.

## Documentation

`./gradlew dokkaGenerate` builds the API site into `build/dokka/html`. It should finish with no warnings (an unresolved `[link]` shows up there). A link to something that exists only on one platform (like `attachTo`) is written as `code`, not as a link.

## Things that go wrong

- **`Key ... is missing in the map` from `compileTestDevelopmentExecutableKotlinWasmJs`:** a Kotlin/Wasm incremental-compilation bug. Run that task again with `--rerun`.
- **Web dependencies changed and `kotlinStoreYarnLock` fails:** run `./gradlew kotlinUpgradeYarnLock` and commit `kotlin-js-store/yarn.lock`.
- **`OutOfMemoryError` while linking iOS:** raise `org.gradle.jvmargs` in `gradle.properties`.
- The iOS Simulator draws OpenGL on the CPU: it is slow and shows artefacts that a device does not (see the README). Do not judge performance on it.

## Style

- Write code that reads like the code around it: the same naming, the same density of comments. Comments say *why*, not what.
- Keep `core` free of any platform or map type. The only module that talks to WorldWind is `wwk-adapter`.
- A change comes with tests. Verify by running, not by reasoning: compile, run the tests, and try a visual change in the demo.

## Pull requests

- Branch from `master`, one topic per branch (`phase-N-topic` or `fix-...`), and open a pull request into `master`.
- Say what changed and why, how you checked it, and what is not covered.
- By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE).

## Releasing (for the maintainer)

1. Update `VERSION_NAME` in `gradle.properties` and `CHANGELOG.md`; the API dump (`updateKotlinAbi`) must be committed.
2. `./gradlew publishToMavenLocal` and try the result from an empty project (the artifacts are `io.github.korugan32:worldwind-draw-*`).
3. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`. The `Publish` workflow builds, tests, signs and uploads to the Central Portal.
4. Check the deployment in the Central Portal (Deployments) and release it. A release cannot be taken back.

One-time setup: a Central Portal account with the namespace `io.github.korugan32` verified, a user token, and a GPG key (its public part on a key server) whose private part and password are the repository secrets named in `.github/workflows/publish.yml`.
