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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import docs.component.CodeBlock
import docs.theme.DocsTheme

@Composable
fun ApiStateScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "CloudyState",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Represents the state of the blur processing operation. " +
        "Use this to track blur progress and handle results.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // State Hierarchy
    Text(
      text = "State Hierarchy",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        sealed interface CloudyState {
          data object Nothing : CloudyState
          data object Loading : CloudyState

          sealed interface Success : CloudyState {
            data object Applied : Success      // GPU blur
            data class Captured(bitmap) : Success  // CPU blur
            data object Scrim : Success        // Fallback scrim
          }

          data class Error(throwable) : CloudyState
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // State Details
    StateCard(
      name = "CloudyState.Nothing",
      description = "Initial state before any blur processing begins.",
      when_ = "Default state when the modifier is first applied.",
    )

    Spacer(modifier = Modifier.height(16.dp))

    StateCard(
      name = "CloudyState.Loading",
      description = "Blur processing is currently in progress.",
      when_ = "During async blur computation (mainly on CPU path).",
    )

    Spacer(modifier = Modifier.height(16.dp))

    StateCard(
      name = "CloudyState.Success.Applied",
      description = "GPU-accelerated blur was applied directly in the rendering pipeline. " +
        "No bitmap is extracted for performance reasons.",
      when_ = "iOS (Skia Metal), Android 31+ (RenderEffect), Desktop, WASM.",
    )

    Spacer(modifier = Modifier.height(16.dp))

    StateCard(
      name = "CloudyState.Success.Captured",
      description = "CPU-based blur completed with a captured bitmap. Access via state.bitmap.",
      when_ = "Android 30 and below using Native C++ RenderScriptToolkit.",
      hasProperty = "bitmap: PlatformBitmap",
    )

    Spacer(modifier = Modifier.height(16.dp))

    StateCard(
      name = "CloudyState.Success.Scrim",
      description = "Scrim-only fallback was applied instead of blur. " +
        "A semi-transparent overlay is shown.",
      when_ = "Android 30- when cpuBlurEnabled = false (default).",
    )

    Spacer(modifier = Modifier.height(16.dp))

    StateCard(
      name = "CloudyState.Error",
      description = "Blur processing failed. Access the exception via state.throwable.",
      when_ = "Unexpected errors during blur computation.",
      hasProperty = "throwable: Throwable",
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Usage Example
    Text(
      text = "Usage Example",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        var blurState by remember { mutableStateOf<CloudyState>(CloudyState.Nothing) }

        Box(
          modifier = Modifier.cloudy(
            radius = 15,
            onStateChanged = { blurState = it }
          )
        ) {
          Image(painter = painter, contentDescription = null)
        }

        // React to state changes
        when (val state = blurState) {
          CloudyState.Nothing -> {
            // Initial state, blur not started
          }
          CloudyState.Loading -> {
            // Show loading indicator if needed
            CircularProgressIndicator()
          }
          is CloudyState.Success.Applied -> {
            // GPU blur applied, no bitmap available
            // Blur is already visible on screen
          }
          is CloudyState.Success.Captured -> {
            // CPU blur completed, bitmap available
            val blurredBitmap = state.bitmap
            // Can use bitmap for other purposes
          }
          CloudyState.Success.Scrim -> {
            // Scrim fallback applied (Android 30- when cpuBlurEnabled = false)
          }
          is CloudyState.Error -> {
            // Handle error
            Text("Blur failed: ${'$'}{state.throwable.message}")
          }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // CloudyDefaults
    Text(
      text = "CloudyDefaults",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Default values and constants for Cloudy blur effects.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        object CloudyDefaults {
          // Default blur radius for background blur
          const val BACKGROUND_RADIUS: Int = 20

          // Default end position for progressive blur fade
          const val PROGRESSIVE_FADE_END: Float = 0.5f

          // Default fade distance for Edges progressive
          const val EDGES_FADE_DISTANCE: Float = 0.2f

          // Default: CPU blur disabled on Android 30-
          const val CPP_BLUR_ENABLED: Boolean = false

          // Default scrim color when CPU blur disabled
          val DefaultScrimColor: Color = Color.Black.copy(alpha = 0.3f)
        }
      """,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun StateCard(
  name: String,
  description: String,
  when_: String,
  hasProperty: String? = null,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DocsTheme.colors.surface),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = name,
        style = DocsTheme.typography.code,
        color = DocsTheme.colors.primary,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = description,
        style = DocsTheme.typography.bodySmall,
        color = DocsTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "When: $when_",
        style = DocsTheme.typography.caption,
        color = DocsTheme.colors.onSurfaceVariant,
      )

      if (hasProperty != null) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Property: $hasProperty",
          style = DocsTheme.typography.code,
          color = DocsTheme.colors.secondary,
        )
      }
    }
  }
}
