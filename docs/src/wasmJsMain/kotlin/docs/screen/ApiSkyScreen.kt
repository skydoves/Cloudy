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
fun ApiSkyScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Background Blur (Glassmorphism)",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Apply backdrop blur effects using Modifier.sky() " +
        "and Modifier.cloudy(sky:) for glassmorphism UI.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // rememberSky
    Text(
      text = "rememberSky()",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Creates and remembers a Sky instance for background blur functionality.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @Composable
        fun rememberSky(): Sky
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Modifier.sky()
    Text(
      text = "Modifier.sky()",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Captures the content of this composable to a GraphicsLayer for background blur.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @Composable
        fun Modifier.sky(sky: Sky): Modifier
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Modifier.cloudy(sky:)
    Text(
      text = "Modifier.cloudy(sky:)",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Applies a background blur effect using the content captured by Modifier.sky().",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @Composable
        fun Modifier.cloudy(
          sky: Sky,
          radius: Int = CloudyDefaults.BACKGROUND_RADIUS,
          progressive: CloudyProgressive = CloudyProgressive.None,
          tint: Color = Color.Transparent,
          enabled: Boolean = true,
          cpuBlurEnabled: Boolean = CloudyDefaults.CPP_BLUR_ENABLED,
          onStateChanged: (CloudyState) -> Unit = {},
        ): Modifier
      """,
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Parameters Table
    Text(
      text = "Parameters",
      style = DocsTheme.typography.h3,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    BackgroundBlurParameterTable()

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
        val sky = rememberSky()

        Box(modifier = Modifier.sky(sky)) {
          // Background content
          AsyncImage(
            model = "background.jpg",
            modifier = Modifier.fillMaxSize()
          )

          // Basic backdrop blur
          Card(
            modifier = Modifier
              .align(Alignment.Center)
              .cloudy(sky = sky, radius = 20)
          ) {
            Text("Glass Card")
          }

          // Progressive blur (fades from blurred to clear)
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .height(100.dp)
              .cloudy(
                sky = sky,
                radius = 25,
                progressive = CloudyProgressive.BottomToTop(),
                tint = Color.White.copy(alpha = 0.1f),
              )
          )
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Sky.invalidate()
    Text(
      text = "Sky.invalidate()",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "Call invalidate() when background content changes to invalidate cached blur results.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        val sky = rememberSky()
        var imageUrl by remember { mutableStateOf("image1.jpg") }

        Box(modifier = Modifier.sky(sky)) {
          AsyncImage(
            model = imageUrl,
            onSuccess = { sky.invalidate() } // Invalidate blur cache when image loads
          )

          Card(modifier = Modifier.cloudy(sky = sky, radius = 20)) {
            Text("Glass Card")
          }
        }
      """,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun BackgroundBlurParameterTable() {
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
        text = "Default",
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

    ParamRow("sky", "required", "Sky state holder from rememberSky()")
    ParamRow("radius", "20", "Blur radius in pixels")
    ParamRow("progressive", "None", "Progressive blur configuration")
    ParamRow("tint", "Transparent", "Tint color over blurred background")
    ParamRow("enabled", "true", "Enable/disable the blur effect")
    ParamRow("cpuBlurEnabled", "false", "Enable CPU blur on Android 30-")
    ParamRow("onStateChanged", "{}", "State change callback")
  }
}

@Composable
private fun ParamRow(name: String, defaultValue: String, description: String) {
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
      text = defaultValue,
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
