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
import com.skydoves.cloudy.Configuration
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  id(libs.plugins.kotlin.multiplatform.get().pluginId)
  id(libs.plugins.android.kotlin.multiplatform.library.get().pluginId)
  id(libs.plugins.compose.multiplatform.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
  alias(libs.plugins.kotlinx.serialization)
}

kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }

  targets
    .filterIsInstance<KotlinNativeTarget>()
    .forEach { target ->
      target.binaries {
        framework {
          baseName = "CloudyApp"
          isStatic = true
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
    browser()
    binaries.executable()
  }

  // iOS targets
  listOf(
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "CloudyApp"
      isStatic = true
    }
  }

  // macOS target
  macosArm64 {
    binaries.executable {
      entryPoint = "main"
    }
  }

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
    // Must differ from :app-android's com.skydoves.cloudydemo; two modules cannot share a namespace.
    namespace = "com.skydoves.cloudydemo.shared"
    compileSdk = Configuration.compileSdk
    minSdk = Configuration.minSdk
    // The android @Preview functions render Res.drawable.poster; the KMP-library plugin leaves
    // Compose-resource generation for the Android target off by default.
    androidResources.enable = true
  }

  sourceSets {
    val desktopMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.slf4j.simple)
        implementation(libs.ktor.client.okhttp)
      }
    }

    val wasmJsMain by getting {
      dependencies {
        implementation(libs.ktor.client.js)
      }
    }

    val macosMain by getting {
      dependencies {
        implementation(libs.ktor.client.darwin)
      }
    }

    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.compose.ui.tooling.preview)
    }

    commonMain.dependencies {
      implementation(project(":cloudy"))

      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.ui)
      implementation(compose.components.uiToolingPreview)
      implementation(libs.compose.resources)
      implementation(compose.material)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)

      implementation(libs.landscapist.coil)
      implementation(libs.coil)
      implementation(libs.coil.network)

      implementation(libs.androidx.nav3.ui)
      implementation(libs.kotlinx.serialization.json)
    }
  }
}
