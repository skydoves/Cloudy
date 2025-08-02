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
  id(libs.plugins.android.application.get().pluginId)
  id(libs.plugins.kotlin.multiplatform.get().pluginId)
  id(libs.plugins.compose.multiplatform.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
}

kotlin {
  targets
    .filterIsInstance<KotlinNativeTarget>()
    .forEach { target ->
      target.binaries {
        framework {
          baseName = "ComposeApp"
          isStatic = true
        }
      }
    }

  androidTarget()

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(compose.preview)
      implementation(libs.androidx.activity.compose)
    }

    commonMain.dependencies {
      implementation(project(":cloudy"))

      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.ui)
      implementation(libs.compose.resources)
      implementation(compose.material)

      implementation(libs.landscapist.coil)
      implementation(libs.coil)
      implementation(libs.coil.network)
    }
  }
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
}

dependencies {
  implementation(libs.material)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.constraintlayout)
}
