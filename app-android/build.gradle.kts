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

plugins {
  id(libs.plugins.android.application.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
  id(libs.plugins.compose.multiplatform.get().pluginId)
}

android {
  namespace = "com.skydoves.cloudydemo"
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

  packaging {
    // librenderscript-toolkit.so arrives transitively via :cloudy-native; dedup to one copy per ABI.
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
  implementation(project(":app"))

  implementation(libs.androidx.activity.compose)
  // Referenced only from themes.xml (Theme.MaterialComponents.* parent), so it looks unused to code.
  implementation(libs.material)
  // IDE-preview tooling only; keep it out of release/benchmark APKs.
  debugImplementation(libs.androidx.compose.ui.tooling)
}
