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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import docs.component.CodeBlock
import docs.theme.DocsTheme

@Composable
fun ApiProgressiveScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "CloudyProgressive",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Configuration for progressive (gradient) blur effects " +
        "that vary blur intensity based on position.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // None
    ProgressiveTypeSection(
      title = "CloudyProgressive.None",
      description = "Uniform blur across the entire region. This is the default behavior.",
      code = """
        Modifier.cloudy(
          sky = sky,
          progressive = CloudyProgressive.None
        )
      """,
      gradientColors = listOf(
        DocsTheme.colors.primary.copy(alpha = 0.5f),
        DocsTheme.colors.primary.copy(alpha = 0.5f),
      ),
    )

    Spacer(modifier = Modifier.height(32.dp))

    // TopToBottom
    ProgressiveTypeSection(
      title = "CloudyProgressive.TopToBottom",
      description = "Blur intensity decreases from top to bottom. Creates a 'fog lifting' effect.",
      code = """
        // Default: blur from 0% to 50%
        CloudyProgressive.TopToBottom()

        // Custom: blur from 20% to 80%
        CloudyProgressive.TopToBottom(
          start = 0.2f,
          end = 0.8f,
          easing = FastOutSlowInEasing
        )
      """,
      gradientColors = listOf(
        DocsTheme.colors.primary.copy(alpha = 0.8f),
        DocsTheme.colors.primary.copy(alpha = 0.1f),
      ),
      parameters = listOf(
        "start" to "0f" to "Position where blur is at full intensity (0 = top)",
        "end" to "0.5f" to "Position where blur reaches zero intensity",
        "easing" to "FastOutSlowInEasing" to "Easing function for the transition",
      ),
    )

    Spacer(modifier = Modifier.height(32.dp))

    // BottomToTop
    ProgressiveTypeSection(
      title = "CloudyProgressive.BottomToTop",
      description = "Blur intensity decreases from bottom to top. " +
        "Creates a 'rising from mist' effect.",
      code = """
        // Default: blur from 100% to 50%
        CloudyProgressive.BottomToTop()

        // Custom: blur from 80% to 20%
        CloudyProgressive.BottomToTop(
          start = 0.8f,
          end = 0.2f,
          easing = FastOutSlowInEasing
        )
      """,
      gradientColors = listOf(
        DocsTheme.colors.primary.copy(alpha = 0.1f),
        DocsTheme.colors.primary.copy(alpha = 0.8f),
      ),
      parameters = listOf(
        "start" to "1f" to "Position where blur is at full intensity (1 = bottom)",
        "end" to "0.5f" to "Position where blur reaches zero intensity",
        "easing" to "FastOutSlowInEasing" to "Easing function for the transition",
      ),
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Edges
    ProgressiveTypeSection(
      title = "CloudyProgressive.Edges",
      description = "Blur intensity at edges, clear in center. Creates a vignette-like effect.",
      code = """
        // Default: 20% fade distance from each edge
        CloudyProgressive.Edges()

        // Custom: 30% fade distance
        CloudyProgressive.Edges(
          fadeDistance = 0.3f,
          easing = LinearEasing
        )
      """,
      gradientColors = listOf(
        DocsTheme.colors.primary.copy(alpha = 0.8f),
        DocsTheme.colors.primary.copy(alpha = 0.1f),
        DocsTheme.colors.primary.copy(alpha = 0.1f),
        DocsTheme.colors.primary.copy(alpha = 0.8f),
      ),
      parameters = listOf(
        "fadeDistance" to "0.2f" to "Distance from edge where blur fades (0..0.5)",
        "easing" to "FastOutSlowInEasing" to "Easing function for the transition",
      ),
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Platform Support
    Text(
      text = "Platform Support",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = """
        • Android 33+: Full support via AGSL RuntimeShader
        • Android 31-32: Falls back to uniform blur with warning
        • Android 23-30: Falls back to uniform blur (CPU limitation)
        • iOS/macOS/Desktop/WASM: Full support via Skia shader
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun ProgressiveTypeSection(
  title: String,
  description: String,
  code: String,
  gradientColors: List<Color>,
  parameters: List<Triple<String, String, String>>? = null,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DocsTheme.colors.surface),
    shape = RoundedCornerShape(12.dp),
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        // Visual representation
        Box(
          modifier = Modifier
            .size(60.dp, 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
              Brush.verticalGradient(gradientColors),
            ),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = DocsTheme.typography.h3,
            color = DocsTheme.colors.primary,
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = description,
            style = DocsTheme.typography.bodySmall,
            color = DocsTheme.colors.onSurfaceVariant,
          )
        }
      }

      if (parameters != null) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Parameters:",
          style = DocsTheme.typography.bodySmall,
          color = DocsTheme.colors.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        parameters.forEach { (name, default, desc) ->
          Row(modifier = Modifier.padding(vertical = 2.dp)) {
            Text(
              text = "$name = $default",
              style = DocsTheme.typography.code,
              color = DocsTheme.colors.secondary,
              modifier = Modifier.width(200.dp),
            )
            Text(
              text = desc,
              style = DocsTheme.typography.caption,
              color = DocsTheme.colors.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      CodeBlock(code = code)
    }
  }
}

private infix fun <A, B, C> Pair<A, B>.to(that: C): Triple<A, B, C> = Triple(first, second, that)
