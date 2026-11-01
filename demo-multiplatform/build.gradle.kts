plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

// Faz 14: runs the library on iOS and in the browser (Compose Multiplatform), to prove it works there and to find what does not.
// Not published and not part of the library: it is a consumer, like :demo.
kotlin {
    iosArm64 {
        binaries.executable {
            entryPoint = "com.tcsappdev.worldwinddraw.demomp.main"
        }
    }

    iosSimulatorArm64 {
        binaries.executable {
            entryPoint = "com.tcsappdev.worldwinddraw.demomp.main"
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":wwk-adapter"))
                implementation(project(":boolean-ops"))
                implementation(libs.worldwind.compose)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
            }
        }
    }
}

// WorldWind's data files (the geoid, the base imagery, the stars) are moko-resources inside its klibs. An Android or Xcode build puts
// them where they are looked for; for the browser they have to be plain files at the root of the site, so they are taken out here.
val wwkWebAssets = tasks.register<Sync>("wwkWebAssets") {
    val klibs = kotlin.wasmJs().compilations.getByName("main").compileDependencyFiles
    from({ klibs.filter { it.name.startsWith("worldwind-assets") }.map { zipTree(it) } }) {
        include("default/resources/moko-resources-js/**")
        eachFile { path = path.removePrefix("default/resources/moko-resources-js/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/wwk-web-assets"))
}

kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(wwkWebAssets) }

// WorldWind's Compose `WorldWindow` exists once per platform (there is no common declaration), so the shared metadata compilations (common and the
// iOS and web groups), which only a library that other modules depend on needs, cannot see the app code that calls it. The iOS and browser compilations do, and those are the ones this app is built from.
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("KotlinMetadata") }.configureEach { enabled = false }
