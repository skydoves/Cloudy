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

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id(libs.plugins.android.application.get().pluginId)
  id(libs.plugins.kotlin.android.get().pluginId)
}

android {
  compileSdk = Configuration.compileSdk
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

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidxComposeCompiler.get()
  }

  packagingOptions {
    pickFirsts.add("lib/*/librenderscript-toolkit.so")
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
  implementation(project(":cloudy"))

  implementation(libs.landscapist.glide)
  implementation(libs.landscapist.transformation)

  implementation(libs.material)
  implementation(libs.androidx.activity.compose)

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.constraintlayout)
}
