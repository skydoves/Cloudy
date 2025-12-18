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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import docs.component.Callout
import docs.component.CalloutType
import docs.theme.DocsTheme

@Composable
fun PlatformSupportScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Platform Support",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Cloudy supports all Kotlin Multiplatform targets " +
        "with platform-optimized implementations.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Platform Table
    Text(
      text = "Implementation Details",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    PlatformTable()

    Spacer(modifier = Modifier.height(32.dp))

    // Android Details
    Text(
      text = "Android Implementation",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = """
        Android uses different implementations based on API level:

        • API 33+: AGSL RuntimeShader for progressive blur with custom shaders
        • API 31-32: RenderEffect.createBlurEffect() for GPU-accelerated uniform blur
        • API 23-30: Native C++ with NEON/SIMD optimizations for CPU-based blur
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Callout(
      text = "Android API 30 and below: Background blur (Modifier.cloudy(sky:)) shows a scrim " +
        "overlay by default. To enable CPU-based blur instead, set cpuBlurEnabled = true. " +
        "Note that CPU blur may impact performance on lower-end devices.",
      type = CalloutType.WARNING,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Progressive Blur Support
    Text(
      text = "Progressive Blur Support",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    ProgressiveBlurTable()

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun PlatformTable() {
  val shape = RoundedCornerShape(8.dp)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, DocsTheme.colors.divider, shape),
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(DocsTheme.colors.surfaceVariant)
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      TableCell("Platform", weight = 1f, isHeader = true)
      TableCell("Implementation", weight = 1.5f, isHeader = true)
    }

    // Rows
    TableRow("Android 33+", "AGSL RuntimeShader")
    TableRow("Android 31-32", "RenderEffect (GPU)")
    TableRow("Android 23-30", "Native C++ (CPU)")
    TableRow("iOS", "Skia")
    TableRow("macOS", "Skia")
    TableRow("Desktop (JVM)", "Skia")
    TableRow("Web (WASM)", "Skia")
  }
}

@Composable
private fun ProgressiveBlurTable() {
  val shape = RoundedCornerShape(8.dp)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, DocsTheme.colors.divider, shape),
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(DocsTheme.colors.surfaceVariant)
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      TableCell("Platform", weight = 1f, isHeader = true)
      TableCell("Progressive Support", weight = 1f, isHeader = true)
      TableCell("Notes", weight = 1.5f, isHeader = true)
    }

    // Rows
    ProgressiveRow("Android 33+", "Full", "AGSL shader support")
    ProgressiveRow("Android 31-32", "Uniform only", "Falls back with warning")
    ProgressiveRow("Android 23-30", "Uniform only", "CPU blur limitation")
    ProgressiveRow("iOS/macOS/Desktop/WASM", "Full", "Skia shader support")
  }
}

@Composable
private fun TableRow(platform: String, implementation: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(DocsTheme.colors.surface)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    TableCell(platform, weight = 1f)
    TableCell(implementation, weight = 1.5f)
  }
}

@Composable
private fun ProgressiveRow(platform: String, support: String, notes: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(DocsTheme.colors.surface)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    TableCell(platform, weight = 1f)
    TableCell(support, weight = 1f)
    TableCell(notes, weight = 1.5f)
  }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, isHeader: Boolean = false) {
  Text(
    text = text,
    style = DocsTheme.typography.bodySmall,
    fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
    color = if (isHeader) DocsTheme.colors.onSurface else DocsTheme.colors.onSurfaceVariant,
    modifier = Modifier.weight(weight),
  )
}
