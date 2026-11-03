plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    // The public API is written down in api/ and checked on every build: a change to it shows up as a difference to review (checkKotlinAbi),
    // and is accepted on purpose with updateKotlinAbi. What is marked as internal to the modules is not part of it.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("com.tcsappdev.worldwinddraw.core.InternalWorldWindDrawApi")
            }
        }
    }

    // The library's own modules use what is marked as internal to them from one another; a host app does not (it has to opt in itself).
    compilerOptions {
        optIn.add("com.tcsappdev.worldwinddraw.core.InternalWorldWindDrawApi")
    }

    // wwk-adapter only targets the platforms earth.worldwind:worldwind actually publishes
    // variants for (checked against its Gradle module metadata): android, iosArm64,
    // iosSimulatorArm64, js, jvm, wasmJs. Notably no iosX64 (Intel simulator dropped upstream).
    android {
        namespace = "com.tcsappdev.worldwinddraw.adapter"
        compileSdk {
            version = release(37) {
                minorApiLevel = 0
            }
        }
        minSdk = 29

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    val xcfName = "wwk-adapterKit"

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    jvm()

    js {
        browser()
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // core is exposed as api: adapter facade types build on core's domain models,
                // so consumers of wwk-adapter transitively get core's public API too.
                api(project(":core"))
                api(libs.worldwind)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.runner)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosArm64) depends on as
                // part of KMP's default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }
    }

}
