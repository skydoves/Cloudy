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
import docs.component.Callout
import docs.component.CalloutType
import docs.component.CodeBlock
import docs.theme.DocsTheme

@Composable
fun ApiMotionLightScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Motion Light",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "The Liquid Glass specular highlight is driven by a LiquidGlassLight source. " +
        "The default is a fixed light; a motion-driven source makes the highlight sweep as the " +
        "surface tilts. Two factories build one: rememberGyroLightSource (device gyro) and " +
        "rememberTransformLightSource (a composable's own rotation, no sensor).",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Experimental opt-in
    Callout(
      text = "These motion APIs are experimental. Opt in with " +
        "@OptIn(ExperimentalLiquidGlassMotion::class) or propagate the annotation.",
      type = CalloutType.NOTE,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Signatures
    Text(
      text = "Signatures",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        // Stable holder for the specular light direction (screen-space, y-down).
        class LiquidGlassLight

        // Static (fixed) light pointing in a direction.
        fun LiquidGlassLight(direction: Offset): LiquidGlassLight

        @ExperimentalLiquidGlassMotion
        @Composable
        fun rememberGyroLightSource(
          enabled: Boolean = true,
          hz: Int = 30,
          base: Offset = LiquidGlassDefaults.LIGHT_DIR,
          tiltGain: Float = 1.2f,
        ): LiquidGlassLight

        @ExperimentalLiquidGlassMotion
        @Composable
        fun rememberTransformLightSource(
          rotationX: () -> Float,
          rotationY: () -> Float,
          base: Offset = LiquidGlassDefaults.LIGHT_DIR,
          gain: Float = 1.2f,
        ): LiquidGlassLight
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Gyro parameters
    Text(
      text = "rememberGyroLightSource Parameters",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    GyroParameterTable()

    Spacer(modifier = Modifier.height(32.dp))

    // Gyro usage
    Text(
      text = "Gyro-driven glass card",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @OptIn(ExperimentalLiquidGlassMotion::class)
        @Composable
        fun GlassCard() {
          // Hoist once and share across items in a list; one call registers one sensor.
          val light = rememberGyroLightSource(enabled = true)

          Box(
            modifier = Modifier.liquidGlass(
              lensCenter = lensCenter,
              light = light,
            ),
          ) { /* ... */ }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Transform usage
    Text(
      text = "Transform-driven light (no sensor)",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "rememberTransformLightSource drives the highlight from the target composable's own " +
        "rotationX / rotationY, the same values applied through Modifier.graphicsLayer. It needs " +
        "no sensor and runs on every target, including Desktop and Web. The rotations are read " +
        "as lambdas (deferred reads), so per-frame updates invalidate the draw without " +
        "recomposing the modifier.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @OptIn(ExperimentalLiquidGlassMotion::class)
        @Composable
        fun TiltCard() {
          var rx by remember { mutableFloatStateOf(0f) }
          var ry by remember { mutableFloatStateOf(0f) }
          val light = rememberTransformLightSource(rotationX = { rx }, rotationY = { ry })

          Box(
            modifier = Modifier
              .graphicsLayer { rotationX = rx; rotationY = ry; cameraDistance = 12f * density }
              .liquidGlass(lensCenter = lensCenter, light = light),
          ) { /* ... */ }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Cloudy backdrop
    Text(
      text = "Highlight over a blurred backdrop",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = "The same light source plugs into Modifier.cloudy(sky = ...) through its optional " +
        "light parameter. When non-null, a moving specular highlight is drawn over the blurred " +
        "backdrop; null leaves the blur unchanged.",
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    CodeBlock(
      code = """
        @OptIn(ExperimentalLiquidGlassMotion::class)
        @Composable
        fun GlassBar(sky: Sky) {
          val light = rememberGyroLightSource()

          Box(
            modifier = Modifier.cloudy(
              sky = sky,
              radius = 20,
              light = light,
            ),
          ) { /* ... */ }
        }
      """,
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Platform & accessibility
    Text(
      text = "Platform & Accessibility",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Callout(
      text =
      "Reduce Motion (Android animator scale 0, iOS isReduceMotionEnabled) freezes the light " +
        "at its base direction and registers no sensors. It is observed live, so toggling the " +
        "setting while on screen takes effect.",
      type = CalloutType.INFO,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Callout(
      text =
      "The motion highlight needs the shader path: Android API 33+. On API < 33 the Liquid " +
        "Glass fallback has no shader, so the light source is a no-op.",
      type = CalloutType.WARNING,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Callout(
      text = "On iOS, the consuming app's Info.plist must declare NSMotionUsageDescription, or " +
        "recent iOS terminates the app on the first device-motion read.",
      type = CalloutType.WARNING,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = """
        • rememberGyroLightSource reads device motion; sensorless devices, Desktop, and Web keep a static light.
        • rememberTransformLightSource has no sensor and is deterministic on every target.
        • In lists, hoist the source once above the list so a single sensor listener is shared.
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun GyroParameterTable() {
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

    GyroParamRow(
      "enabled",
      "true",
      "When false, the light is frozen at base and no sensors are registered.",
    )
    GyroParamRow("hz", "30", "Target emit rate in Hz; the value is throttled to this rate.")
    GyroParamRow(
      "base",
      "LiquidGlassDefaults.LIGHT_DIR",
      "Resting light direction when flat, disabled, or unsupported.",
    )
    GyroParamRow(
      "tiltGain",
      "1.2f",
      "How strongly tilt displaces the light from base. Higher = more sweep.",
    )
  }
}

@Composable
private fun GyroParamRow(name: String, defaultValue: String, description: String) {
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
