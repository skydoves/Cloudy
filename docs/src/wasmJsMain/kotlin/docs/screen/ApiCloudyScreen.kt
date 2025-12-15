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
import docs.component.CodeBlock
import docs.theme.DocsTheme

@Composable
fun ApiCloudyScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Modifier.cloudy()",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Applies a cross-platform blur effect to the current modifier.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Signature
    Text(
      text = "Signature",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @Composable
        fun Modifier.cloudy(
          radius: Int = 10,
          enabled: Boolean = true,
          onStateChanged: (CloudyState) -> Unit = {},
        ): Modifier
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Parameters
    Text(
      text = "Parameters",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    ParameterTable()

    Spacer(modifier = Modifier.height(32.dp))

    // Platform Behavior
    Text(
      text = "Platform Behavior",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    PlatformBehaviorTable()

    Spacer(modifier = Modifier.height(32.dp))

    // Example
    Text(
      text = "Example",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        Box(
          modifier = Modifier.cloudy(
            radius = 15,
            onStateChanged = { state ->
              when (state) {
                is CloudyState.Success.Applied -> {
                  // GPU blur applied, no bitmap available
                }
                is CloudyState.Success.Captured -> {
                  // CPU blur done, bitmap available: state.bitmap
                }
                CloudyState.Success.Scrim -> {
                  // Scrim fallback (Android 30- when cpuBlurEnabled = false)
                }
                CloudyState.Loading -> { /* Processing */ }
                is CloudyState.Error -> { /* Handle error */ }
                CloudyState.Nothing -> { /* Initial state */ }
              }
            }
          )
        ) {
          Image(painter = imagePainter, contentDescription = null)
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Notes
    Text(
      text = "Notes",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = """
        • The radius is converted to sigma internally using sigma = radius / 2.0
        • On Android API 30 and below, values above 25 use iterative passes
        • GPU-accelerated blur does not provide a bitmap (performance optimization)
        • CPU-based blur (Android 30-) provides the blurred bitmap via CloudyState.Success.Captured
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun ParameterTable() {
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
      Text(
        text = "Parameter",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "Type",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "Description",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(2f),
      )
    }

    ParameterRow("radius", "Int = 10", "Blur radius in pixels. Must be non-negative.")
    ParameterRow("enabled", "Boolean = true", "If false, disables the blur effect.")
    ParameterRow("onStateChanged", "(CloudyState) -> Unit", "Callback when blur state changes.")
  }
}

@Composable
private fun ParameterRow(name: String, type: String, description: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(DocsTheme.colors.surface)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = name,
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.primary,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = type,
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = description,
      style = DocsTheme.typography.bodySmall,
      color = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.weight(2f),
    )
  }
}

@Composable
private fun PlatformBehaviorTable() {
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
      Text(
        text = "Platform",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = "Implementation",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1.5f),
      )
      Text(
        text = "State Returned",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1.5f),
      )
    }

    PlatformRow("iOS", "Skia BlurEffect (Metal GPU)", "CloudyState.Success.Applied")
    PlatformRow("Android 31+", "RenderEffect (GPU)", "CloudyState.Success.Applied")
    PlatformRow("Android 30-", "Native C++ (CPU)", "CloudyState.Success.Captured")
  }
}

@Composable
private fun PlatformRow(platform: String, implementation: String, state: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(DocsTheme.colors.surface)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = platform,
      style = DocsTheme.typography.bodySmall,
      color = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = implementation,
      style = DocsTheme.typography.bodySmall,
      color = DocsTheme.colors.onSurfaceVariant,
      modifier = Modifier.weight(1.5f),
    )
    Text(
      text = state,
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.secondary,
      modifier = Modifier.weight(1.5f),
    )
  }
}
