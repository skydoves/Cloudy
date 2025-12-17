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
package docs.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import docs.component.CodeBlock
import docs.theme.DocsTheme

private const val CLOUDY_VERSION = "0.3.0"

@Composable
fun InstallationScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Installation",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Add Cloudy to your Kotlin Multiplatform or Android project.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Gradle Version Catalog
    Text(
      text = "Gradle Version Catalog",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Add to your libs.versions.toml:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        [versions]
        cloudy = "$CLOUDY_VERSION"

        [libraries]
        cloudy = { module = "com.github.skydoves:cloudy", version.ref = "cloudy" }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Kotlin Multiplatform
    Text(
      text = "Kotlin Multiplatform",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Add the dependency in your build.gradle.kts:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        kotlin {
          sourceSets {
            commonMain.dependencies {
              implementation(libs.cloudy)
            }
          }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Android Only
    Text(
      text = "Android Only",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "For Android-only projects:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        dependencies {
          implementation(libs.cloudy)
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Maven Coordinates
    Text(
      text = "Maven Coordinates",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "If not using version catalog:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        implementation("com.github.skydoves:cloudy:$CLOUDY_VERSION")
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Requirements
    Text(
      text = "Requirements",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = """
        • Android: minSdk 23, compileSdk 36
        • iOS: iOS 13+
        • macOS: macOS 10.15+
        • Desktop: JVM 11+
        • Web: Modern browser with WASM support
        • Compose Multiplatform: 1.9.3+
        • Kotlin: 2.2.21+
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}
