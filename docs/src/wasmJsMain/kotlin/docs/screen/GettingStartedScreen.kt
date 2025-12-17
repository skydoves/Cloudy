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

@Composable
fun GettingStartedScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Getting Started",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Cloudy is a Jetpack Compose blur library for Kotlin Multiplatform. " +
        "It provides a simple Modifier.cloudy() API to apply blur effects to any composable.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Basic Usage
    Text(
      text = "Basic Usage",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Apply a blur effect to any composable using the cloudy() modifier:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        Box(
          modifier = Modifier
            .size(200.dp)
            .cloudy(radius = 15)
        ) {
          Image(
            painter = painterResource("image.png"),
            contentDescription = null,
          )
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Background Blur
    Text(
      text = "Background Blur",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Create backdrop blur effects by blurring the background content behind a composable:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        val sky = rememberSky()

        Box(modifier = Modifier.sky(sky)) {
          // Background content
          Image(
            painter = painterResource("background.png"),
            modifier = Modifier.fillMaxSize(),
          )

          // Card with blurred background
          Card(
            modifier = Modifier
              .align(Alignment.Center)
              .cloudy(sky = sky, radius = 25)
          ) {
            Text("Blurred Background Card")
          }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // State Handling
    Text(
      text = "State Handling",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Track the blur processing state using the onStateChanged callback:",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        Modifier.cloudy(
          radius = 15,
          onStateChanged = { state ->
            when (state) {
              is CloudyState.Success.Applied -> {
                // GPU blur applied (Android 31+, iOS, etc.)
              }
              is CloudyState.Success.Captured -> {
                // CPU blur with bitmap (Android 30-)
                val bitmap = state.bitmap
              }
              is CloudyState.Success.Scrim -> {
                // Scrim fallback (Android 30- when cpuBlurEnabled = false)
              }
              is CloudyState.Loading -> {
                // Blur processing in progress
              }
              is CloudyState.Error -> {
                // Handle error: state.throwable
              }
              CloudyState.Nothing -> {
                // Initial state
              }
            }
          }
        )
      """,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}
