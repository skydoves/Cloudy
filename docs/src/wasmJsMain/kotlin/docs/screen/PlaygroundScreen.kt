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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skydoves.cloudy.CloudyProgressive
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import docs.component.CodeBlock
import docs.theme.DocsTheme
import kotlin.math.roundToInt

private fun Float.format(decimals: Int = 2): String {
  val multiplier = when (decimals) {
    1 -> 10
    2 -> 100
    3 -> 1000
    else -> 100
  }
  val rounded = (this * multiplier).roundToInt() / multiplier.toFloat()
  return rounded.toString()
}

@Composable
fun PlaygroundScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Interactive Playground",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Adjust parameters and see blur effects in real-time. The code snippet updates automatically.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Spacer(modifier = Modifier.height(48.dp))

    // Basic Blur Demo
    BasicBlurDemo()

    Spacer(modifier = Modifier.height(48.dp))

    // Background Blur Demo
    // BackgroundBlurDemo()

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun BasicBlurDemo() {
  var radius by remember { mutableFloatStateOf(15f) }
  var enabled by remember { mutableStateOf(true) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DocsTheme.colors.surface),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(modifier = Modifier.padding(24.dp)) {
      Text(
        text = "Basic Blur (Modifier.cloudy)",
        style = DocsTheme.typography.h2,
        color = DocsTheme.colors.onBackground,
      )

      Spacer(modifier = Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        // Controls
        Column(modifier = Modifier.weight(1f)) {
          // Radius Slider
          Text(
            text = "Radius: ${radius.toInt()}",
            style = DocsTheme.typography.bodySmall,
            color = DocsTheme.colors.onSurface,
          )
          Slider(
            value = radius,
            onValueChange = { radius = it },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
              thumbColor = DocsTheme.colors.primary,
              activeTrackColor = DocsTheme.colors.primary,
            ),
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Enabled Toggle
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = enabled,
              onCheckedChange = { enabled = it },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Enabled",
              style = DocsTheme.typography.bodySmall,
              color = DocsTheme.colors.onSurface,
            )
          }
        }

        // Preview
        Box(
          modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DocsTheme.colors.divider, RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center,
        ) {
          // Sample gradient background with blur
          Box(
            modifier = Modifier
              .fillMaxSize()
              .cloudy(
                radius = radius.toInt(),
                enabled = enabled,
              ),
          ) {
            GradientBackground()
          }

          // Label
          Text(
            text = if (enabled) "radius = ${radius.toInt()}" else "disabled",
            style = DocsTheme.typography.caption,
            color = Color.White,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Generated Code
      Text(
        text = "Generated Code",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(8.dp))

      val basicBlurCode = buildString {
        append("Box(\n")
        append("  modifier = Modifier.cloudy(\n")
        append("    radius = ${radius.toInt()},\n")
        if (!enabled) {
          append("    enabled = false,\n")
        }
        append("  )\n")
        append(") {\n")
        append("  // Your content here\n")
        append("}")
      }

      CodeBlock(code = basicBlurCode)
    }
  }
}

@Composable
private fun BackgroundBlurDemo() {
  val sky = rememberSky()
  var radius by remember { mutableFloatStateOf(20f) }
  var progressiveType by remember { mutableStateOf(ProgressiveTypeOption.None) }
  var progressiveStart by remember { mutableFloatStateOf(0f) }
  var progressiveEnd by remember { mutableFloatStateOf(0.5f) }
  var fadeDistance by remember { mutableFloatStateOf(0.2f) }
  var tintAlpha by remember { mutableFloatStateOf(0f) }
  var dropdownExpanded by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DocsTheme.colors.surface),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(modifier = Modifier.padding(24.dp)) {
      Text(
        text = "Background Blur (Glassmorphism)",
        style = DocsTheme.typography.h2,
        color = DocsTheme.colors.onBackground,
      )

      Spacer(modifier = Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        // Controls
        Column(modifier = Modifier.weight(1f)) {
          // Radius Slider
          Text(
            text = "Radius: ${radius.toInt()}",
            style = DocsTheme.typography.bodySmall,
            color = DocsTheme.colors.onSurface,
          )
          Slider(
            value = radius,
            onValueChange = { radius = it },
            valueRange = 0f..50f,
            colors = SliderDefaults.colors(
              thumbColor = DocsTheme.colors.primary,
              activeTrackColor = DocsTheme.colors.primary,
            ),
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Progressive Type Dropdown
          Text(
            text = "Progressive Type",
            style = DocsTheme.typography.bodySmall,
            color = DocsTheme.colors.onSurface,
          )
          Spacer(modifier = Modifier.height(4.dp))

          Box {
            OutlinedButton(onClick = { dropdownExpanded = true }) {
              Text(progressiveType.displayName)
            }

            DropdownMenu(
              expanded = dropdownExpanded,
              onDismissRequest = { dropdownExpanded = false },
            ) {
              ProgressiveTypeOption.entries.forEach { option ->
                DropdownMenuItem(
                  text = { Text(option.displayName) },
                  onClick = {
                    progressiveType = option
                    dropdownExpanded = false
                  },
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Progressive parameters based on type
          when (progressiveType) {
            ProgressiveTypeOption.TopToBottom -> {
              Text("Start: ${progressiveStart.format()}")
              Slider(
                value = progressiveStart,
                onValueChange = { progressiveStart = it.coerceAtMost(progressiveEnd - 0.01f) },
                valueRange = 0f..0.99f,
              )
              Text("End: ${progressiveEnd.format()}")
              Slider(
                value = progressiveEnd,
                onValueChange = { progressiveEnd = it.coerceAtLeast(progressiveStart + 0.01f) },
                valueRange = 0.01f..1f,
              )
            }

            ProgressiveTypeOption.BottomToTop -> {
              Text("Start: ${progressiveStart.coerceAtLeast(0.5f).format()}")
              Slider(
                value = progressiveStart.coerceAtLeast(0.5f),
                onValueChange = { progressiveStart = it },
                valueRange = 0.01f..1f,
              )
              Text("End: ${progressiveEnd.coerceAtMost(0.5f).format()}")
              Slider(
                value = progressiveEnd.coerceAtMost(0.5f),
                onValueChange = { progressiveEnd = it },
                valueRange = 0f..0.99f,
              )
            }

            ProgressiveTypeOption.Edges -> {
              Text("Fade Distance: ${fadeDistance.format()}")
              Slider(
                value = fadeDistance,
                onValueChange = { fadeDistance = it },
                valueRange = 0.05f..0.5f,
              )
            }

            ProgressiveTypeOption.None -> {
              // No additional parameters
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Tint Alpha
          Text(
            text = "Tint Alpha: ${tintAlpha.format()}",
            style = DocsTheme.typography.bodySmall,
            color = DocsTheme.colors.onSurface,
          )
          Slider(
            value = tintAlpha,
            onValueChange = { tintAlpha = it },
            valueRange = 0f..0.5f,
          )
        }

        // Preview with real background blur
        Box(
          modifier = Modifier
            .size(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DocsTheme.colors.divider, RoundedCornerShape(12.dp))
            .sky(sky),
          contentAlignment = Alignment.Center,
        ) {
          // Background
          GradientBackground()

          // Glass card with real blur
          val progressive = when (progressiveType) {
            ProgressiveTypeOption.None -> CloudyProgressive.None
            ProgressiveTypeOption.TopToBottom -> CloudyProgressive.TopToBottom(
              start = progressiveStart,
              end = progressiveEnd,
            )

            ProgressiveTypeOption.BottomToTop -> CloudyProgressive.BottomToTop(
              start = progressiveStart.coerceAtLeast(progressiveEnd + 0.01f),
              end = progressiveEnd,
            )

            ProgressiveTypeOption.Edges -> CloudyProgressive.Edges(
              fadeDistance = fadeDistance,
            )
          }

          Box(
            modifier = Modifier
              .size(180.dp, 120.dp)
              .clip(RoundedCornerShape(16.dp))
              .cloudy(
                sky = sky,
                radius = radius.toInt(),
                progressive = progressive,
                tint = Color.White.copy(alpha = tintAlpha),
              )
              .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "Glass Card",
                style = DocsTheme.typography.body,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Text(
                text = "radius = ${radius.toInt()}",
                style = DocsTheme.typography.caption,
                color = Color.White.copy(alpha = 0.8f),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Generated Code
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Generated Code",
          style = DocsTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = DocsTheme.colors.onSurface,
          modifier = Modifier.weight(1f),
        )

        IconButton(onClick = { /* Copy to clipboard - WASM limitation */ }) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            tint = DocsTheme.colors.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      val backgroundBlurCode = buildBackgroundBlurCode(
        radius = radius.toInt(),
        progressiveType = progressiveType,
        progressiveStart = progressiveStart,
        progressiveEnd = progressiveEnd,
        fadeDistance = fadeDistance,
        tintAlpha = tintAlpha,
      )

      CodeBlock(code = backgroundBlurCode)
    }
  }
}

@Composable
private fun GradientBackground() {
  Canvas(modifier = Modifier.fillMaxSize()) {
    // Create a colorful gradient background
    drawRect(
      brush = Brush.linearGradient(
        colors = listOf(
          Color(0xFF6200EE),
          Color(0xFF03DAC6),
          Color(0xFFBB86FC),
          Color(0xFF018786),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
      ),
    )

    // Add some circles for visual interest
    drawCircle(
      color = Color.White.copy(alpha = 0.3f),
      radius = size.minDimension * 0.3f,
      center = Offset(size.width * 0.2f, size.height * 0.3f),
    )
    drawCircle(
      color = Color.White.copy(alpha = 0.2f),
      radius = size.minDimension * 0.4f,
      center = Offset(size.width * 0.8f, size.height * 0.7f),
    )
    drawCircle(
      color = Color(0xFFBB86FC).copy(alpha = 0.4f),
      radius = size.minDimension * 0.25f,
      center = Offset(size.width * 0.6f, size.height * 0.2f),
    )
  }
}

private fun buildBackgroundBlurCode(
  radius: Int,
  progressiveType: ProgressiveTypeOption,
  progressiveStart: Float,
  progressiveEnd: Float,
  fadeDistance: Float,
  tintAlpha: Float,
): String = buildString {
  append("val sky = rememberSky()\n\n")
  append("Box(modifier = Modifier.sky(sky)) {\n")
  append("  // Background content\n")
  append("  Image(painter = backgroundPainter)\n\n")
  append("  // Glass card\n")
  append("  Box(\n")
  append("    modifier = Modifier.cloudy(\n")
  append("      sky = sky,\n")
  append("      radius = $radius,\n")

  when (progressiveType) {
    ProgressiveTypeOption.None -> {
      // Default, no need to specify
    }

    ProgressiveTypeOption.TopToBottom -> {
      append("      progressive = CloudyProgressive.TopToBottom(\n")
      append("        start = ${progressiveStart.format()}f,\n")
      append("        end = ${progressiveEnd.format()}f,\n")
      append("      ),\n")
    }

    ProgressiveTypeOption.BottomToTop -> {
      append("      progressive = CloudyProgressive.BottomToTop(\n")
      append(
        "        start = ${
          progressiveStart.coerceAtLeast(progressiveEnd + 0.01f).format()
        }f,\n"
      )
      append("        end = ${progressiveEnd.format()}f,\n")
      append("      ),\n")
    }

    ProgressiveTypeOption.Edges -> {
      append("      progressive = CloudyProgressive.Edges(\n")
      append("        fadeDistance = ${fadeDistance.format()}f,\n")
      append("      ),\n")
    }
  }

  if (tintAlpha > 0.01f) {
    append("      tint = Color.White.copy(alpha = ${tintAlpha.format()}f),\n")
  }

  append("    )\n")
  append("  ) {\n")
  append("    Text(\"Glass Card\")\n")
  append("  }\n")
  append("}")
}

private enum class ProgressiveTypeOption(val displayName: String) {
  None("None (Uniform)"),
  TopToBottom("TopToBottom"),
  BottomToTop("BottomToTop"),
  Edges("Edges (Vignette)"),
}
