/*
 * Designed and developed by 2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.skydoves.cloudy.Arch
import com.skydoves.cloudy.Configuration
import com.skydoves.cloudy.activeArch

plugins {
  id(libs.plugins.kotlin.multiplatform.get().pluginId)
  id(libs.plugins.android.kotlin.multiplatform.library.get().pluginId)
  id(libs.plugins.compose.multiplatform.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
  id(libs.plugins.nexus.plugin.get().pluginId)
  id(libs.plugins.roborazzi.get().pluginId)
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

mavenPublishing {
  val artifactId = "cloudy"
  project.group = Configuration.artifactGroup
  coordinates(
    artifactId = artifactId,
    version = rootProject.extra.get("libVersion").toString()
  )

  pom {
    name.set(artifactId)
    description.set("Jetpack Compose blur effect library, which falls back onto a CPU-based implementation to support older API levels.")
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      listOf(
        "-Xexplicit-api=strict",
        "-Xexpect-actual-classes"
      )
    )
  }

  // Built-in ABI validation; covers desktop + klib. Android target not covered yet (KT-78025).
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
  abiValidation {
    // Keep opt-in experimental API out of the committed dump so it stays free to change.
    filters {
      exclude {
        annotatedWith.add("com.skydoves.cloudy.ExperimentalMirage")
        annotatedWith.add("com.skydoves.cloudy.ExperimentalLiquidGlassMotion")
      }
    }
  }

  // The com.android.kotlin.multiplatform.library plugin registers the "android" target itself;
  // adding androidTarget() is a hard error.

  // JVM Desktop target
  jvm("desktop") {
    compilations.all {
      compileTaskProvider.configure {
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
      }
    }
  }

  // WebAssembly target
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      testTask {
        enabled = false // Kotest doesn't support wasmJs yet
      }
    }
  }

  // iOS targets
  when (activeArch) {
    Arch.ARM -> {
      iosSimulatorArm64()
      iosArm64()
    }

    Arch.ARM_SIMULATOR_DEBUG -> {
      iosSimulatorArm64()
    }

    Arch.X86_64 -> iosSimulatorArm64()
    Arch.ALL -> {
      iosArm64()
      iosSimulatorArm64()
    }
  }

  // macOS native target
  macosArm64()

  // Configure skikoMain intermediate source set
  @Suppress("OPT_IN_USAGE")
  applyDefaultHierarchyTemplate {
    common {
      group("skiko") {
        withJvm()
        withIos()
        withMacos()
        withWasmJs()
      }
    }
  }

  androidLibrary {
    namespace = "com.skydoves.cloudy"
    compileSdk = Configuration.compileSdk
    minSdk = Configuration.minSdk

    // Floor consumers at the highest API this library calls (33, RenderEffect.createRuntimeShaderEffect),
    // so our compileSdk 37 doesn't force them up.
    aarMetadata {
      minCompileSdk = 33
    }

    packaging {
      resources {
        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
      }
    }

    withHostTest {
      isIncludeAndroidResources = true

      // The KMP-library plugin defaults host-test targetSdk to compileSdk (37); Robolectric 4.16.1
      // rejects targetSdk > 36. Pin it to 36 (affects only the test manifest, not the published aar).
      targetSdk {
        version = release(36)
      }
    }

    withDeviceTest {
      // androidx.benchmark's runner (a superset of AndroidJUnitRunner) is required by BenchmarkRule;
      // BackgroundBlurBenchmark throws at runtime under the plain runner. The @Ignore'd screenshot
      // specs in this source set still run fine under it. suppressErrors (EMULATOR/UNLOCKED/etc.) is
      // passed per-run from the CLI via -Pandroid.testInstrumentationRunnerArguments.* so CI/device
      // runs stay strict by default.
      instrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }
  }

  sourceSets {
    val skikoMain by getting {
      dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.ui)
      }
    }

    androidMain.dependencies {
      // Native .so backend; must be a project() dep (an external-coordinate string dep is dropped
      // from published metadata).
      implementation(project(":cloudy-native"))
      implementation(libs.androidx.core.ktx)
      implementation(libs.androidx.compose.ui)
      implementation(libs.androidx.compose.runtime)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.kotlinx.coroutines.android)
    }

    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.tooling.preview)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.kotest.framework.engine)
      implementation(libs.kotest.assertions.core)
    }

    val desktopTest by getting {
      dependencies {
        implementation(libs.kotest.runner.junit5)
        // The OS/arch-specific skiko native runtime (the .dylib/.so/.dll). Present tests are pure
        // codegen and never touch native skiko, but MirageProgramCacheTest compiles real SKSL through
        // RuntimeEffect on the skiko backend, which needs the native lib loaded. currentOs resolves
        // the right classifier per host so this stays portable on CI.
        implementation(compose.desktop.currentOs)
      }
    }

    // Kotlin 2.4.0 has no typed accessor for these KMP-library source sets yet; reach them by name.
    getByName("androidHostTest").dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.androidx.compose.ui.test)
      implementation(libs.androidx.compose.ui.test.junit4)
      implementation(libs.junit4)
      implementation(libs.mockito)
      implementation(libs.mockito.inline)
      implementation(libs.robolectric)
      implementation(libs.roborazzi)
      implementation(libs.roborazzi.compose)
      implementation(libs.roborazzi.junit.rule)
    }

    // Instrumented (on-device/emulator) screenshot specs. Currently @Ignore'd Phase-2 placeholders
    // (backdrop + liquid glass) that need a real GPU/RenderThread; this set exists so they compile
    // and are ready to run on the Phase-2 emulator job. Pixel assertions there use
    // android.graphics.Bitmap (NOT java.awt/javax.imageio, which are absent on Dalvik).
    getByName("androidDeviceTest").dependencies {
      implementation(libs.androidx.compose.ui)
      implementation(libs.androidx.compose.foundation)
      implementation(libs.androidx.compose.runtime)
      implementation(libs.androidx.compose.ui.test)
      implementation(libs.androidx.compose.ui.test.junit4)
      implementation(libs.androidx.test.runner)
      implementation(libs.androidx.test.junit)
      implementation(libs.junit4)
      // Microbenchmark for the native backgroundBlur hot path (BackgroundBlurBenchmark). Uses
      // implementation (not androidTestImplementation) so the benchmark manifest merges in.
      implementation(libs.androidx.benchmark.junit4)
    }

    iosTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}

// The KMP-library host-test DSL has no systemProperty hook, so set it here; omitting this silently
// renders GPU-blur Roborazzi goldens wrong while the build stays green.
tasks.withType<Test>().configureEach {
  systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}

tasks.named<Test>("desktopTest") {
  useJUnitPlatform()
}

// The Compose plugin creates this resource-copy task for the androidDeviceTest variant but never
// sets its outputDirectory (the KMP-library device-test component has no `assets` source to wire
// it to), so it fails config-time validation. This source set has no composeResources/, so give
// the task a throwaway output dir to make it a valid no-op. See compose-gradle-plugin
// AndroidResources.kt.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
  // The task type is internal to the Compose plugin, so set outputDirectory reflectively. Guard the
  // lookup: if a plugin bump renames/relocates the getter this becomes a no-op instead of failing
  // config-time for every device-test build (at worst the original validation error resurfaces,
  // signalling the workaround needs revisiting).
  val outputDir = layout.buildDirectory.dir("composeResources/androidDeviceTestAssets")
  runCatching {
    javaClass.getMethod("getOutputDirectory").invoke(this) as org.gradle.api.file.DirectoryProperty
  }.onSuccess { prop ->
    prop.convention(outputDir)
  }.onFailure { e ->
    logger.warn("Could not set outputDirectory on ${name} reflectively; Compose plugin API may have changed", e)
  }
}

// Kotest's runner is JVM-only, so the shared commonTest sources compile for the Kotlin/Native
// targets but no runner discovers them there. Gradle 9 fails a test task that finds zero tests
// (failOnNoDiscoveredTests defaults to true); allow the empty native test tasks to pass instead.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
  failOnNoDiscoveredTests = false
}
