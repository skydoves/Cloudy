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
  id(libs.plugins.android.library.get().pluginId)
  id(libs.plugins.compose.multiplatform.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
  id(libs.plugins.nexus.plugin.get().pluginId)
}

apply(from = "${rootDir}/scripts/publish-module.gradle.kts")

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      listOf(
        "-Xexplicit-api=strict",
        "-Xexpect-actual-classes"
      )
    )
  }

  applyDefaultHierarchyTemplate()

  androidTarget()
  when (activeArch) {
    Arch.ARM -> {
      iosSimulatorArm64()
      iosArm64()
    }

    Arch.ARM_SIMULATOR_DEBUG -> {
      iosSimulatorArm64()
    }

    Arch.X86_64 -> iosX64()
    Arch.ALL -> {
      iosArm64()
      iosX64()
      iosSimulatorArm64()
    }
  }

  android {
    compileSdk = Configuration.compileSdk
    namespace = "com.skydoves.cloudy"
    defaultConfig {
      minSdk = Configuration.minSdk
      externalNativeBuild {
        cmake {
          cppFlags += "-std=c++17"
          arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
        }
      }
    }
    externalNativeBuild {
      cmake {
        path = file("src/main/cpp/CMakeLists.txt")
      }
    }

    buildFeatures {
      compose = true
    }

    packaging {
      resources {
        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
      }
    }

    sourceSets {
      getByName("main") {
        assets.srcDirs("src/androidMain/assets")
        java.srcDirs("src/androidMain/kotlin")
        res.srcDirs("src/androidMain/res")
      }
      getByName("test") {
        assets.srcDirs("src/androidUnitTest/assets")
        java.srcDirs("src/androidUnitTest/kotlin")
        res.srcDirs("src/androidUnitTest/res")
      }
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.androidx.compose.ui)
      implementation(libs.androidx.compose.runtime)
      implementation(libs.kotlinx.coroutines.android)
    }

    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.ui)
      implementation(libs.compose.resources)
      implementation(libs.compose.ui.tooling.preview)
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }

    androidUnitTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.androidx.compose.ui.test)
      implementation(libs.androidx.compose.ui.test.junit4)
      implementation(libs.junit4)
      implementation(libs.mockito)
      implementation(libs.mockito.inline)
    }

    iosTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
