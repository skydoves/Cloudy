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
@file:OptIn(ExperimentalRoborazziApi::class)

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.skydoves.cloudy.Configuration
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED

plugins {
  id(libs.plugins.android.application.get().pluginId)
  id(libs.plugins.kotlin.android.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
  id(libs.plugins.roborazzi.get().pluginId)
}

android {
  compileSdk = Configuration.compileSdk
  namespace = "com.skydoves.cloudydemo"
  defaultConfig {
    applicationId = "com.skydoves.cloudydemo"
    minSdk = Configuration.minSdk
    targetSdk = Configuration.targetSdk
    versionCode = Configuration.versionCode
    versionName = Configuration.versionName
  }

  buildFeatures {
    compose = true
  }

  packaging {
    jniLibs.pickFirsts.add("lib/*/librenderscript-toolkit.so")
  }

  buildTypes {
    create("benchmark") {
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
      isDebuggable = false
    }
  }

  lint {
    abortOnError = false
  }

  testOptions {
    unitTests {
      all {
        it.systemProperties["robolectric.graphicsMode"] = "NATIVE"
        it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
        it.maxParallelForks = Runtime.getRuntime().availableProcessors()
        it.maxParallelForks = Runtime.getRuntime().availableProcessors()
        it.testLogging {
          events.addAll(listOf(STARTED, PASSED, SKIPPED, FAILED))
          showCauses = true
          showExceptions = true
          exceptionFormat = FULL
        }
      }
    }
  }
}

dependencies {
  implementation(project(":cloudy"))

  implementation(libs.landscapist.glide)
  implementation(libs.landscapist.transformation)

  implementation(libs.material)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.constraintlayout)

  // Test dependencies
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit)
  testImplementation(libs.roborazzi.annotations)
  testImplementation(libs.roborazzi.compose.preview.scanner.support)
  testImplementation(libs.compose.preview.scanner.support)
  testImplementation(libs.robolectric)
  testImplementation(libs.junit4)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.androidx.compose.ui.test)
  testImplementation(libs.androidx.compose.ui.test.junit4)
}

roborazzi {
  generateComposePreviewRobolectricTests {
    enable.set(true)
    packages.set(listOf("com.skydoves.cloudydemo"))
    robolectricConfig = mapOf(
      "sdk" to "[27, 30, 33]",
      "qualifiers" to "RobolectricDeviceQualifiers.Pixel5",
    )
  }
}
