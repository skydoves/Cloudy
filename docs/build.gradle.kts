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
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  id(libs.plugins.kotlin.multiplatform.get().pluginId)
  id(libs.plugins.compose.multiplatform.get().pluginId)
  id(libs.plugins.compose.compiler.get().pluginId)
}

// Generate BuildConfig with version information from Configuration
val generateBuildConfig by tasks.registering {
  val outputDir = layout.buildDirectory.dir("generated/source/buildConfig/wasmJsMain/kotlin")
  outputs.dir(outputDir)

  doLast {
    val buildConfigDir = outputDir.get().asFile.resolve("docs/config")
    buildConfigDir.mkdirs()

    val buildConfigFile = buildConfigDir.resolve("BuildConfig.kt")
    buildConfigFile.writeText(
      """
      |package docs.config
      |
      |object BuildConfig {
      |  const val CLOUDY_VERSION = "${Configuration.versionName}"
      |}
      """.trimMargin()
    )
  }
}

kotlin {
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      commonWebpackConfig {
        outputFileName = "cloudy-docs.js"
      }
    }
    binaries.executable()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.components.resources)
    }

    wasmJsMain {
      kotlin.srcDir(generateBuildConfig.map { it.outputs.files.singleFile })

      dependencies {
        implementation(project(":cloudy"))

        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.ui)
        implementation(compose.material3)
        implementation(compose.materialIconsExtended)

        implementation(libs.landscapist.coil)
        implementation(libs.coil)
        implementation(libs.coil.network)
        implementation(libs.ktor.client.js)
      }
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  dependsOn(generateBuildConfig)
}
