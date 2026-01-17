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
fun ApiLiquidGlassScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(32.dp),
  ) {
    Text(
      text = "Modifier.liquidGlass()",
      style = DocsTheme.typography.h1,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Applies a cross-platform Liquid Glass lens distortion effect to the content. " +
        "Creates an interactive glass lens that distorts the dynamic content beneath it in real-time.",
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
        fun Modifier.liquidGlass(
          lensCenter: Offset,
          lensSize: Size = LiquidGlassDefaults.LENS_SIZE,
          cornerRadius: Float = LiquidGlassDefaults.CORNER_RADIUS,
          refraction: Float = LiquidGlassDefaults.REFRACTION,
          curve: Float = LiquidGlassDefaults.CURVE,
          dispersion: Float = LiquidGlassDefaults.DISPERSION,
          saturation: Float = LiquidGlassDefaults.SATURATION,
          contrast: Float = LiquidGlassDefaults.CONTRAST,
          tint: Color = LiquidGlassDefaults.TINT,
          edge: Float = LiquidGlassDefaults.EDGE,
          enabled: Boolean = true,
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

    LiquidGlassParameterTable()

    Spacer(modifier = Modifier.height(32.dp))

    // Default Values
    Text(
      text = "Default Values",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    DefaultValuesTable()

    Spacer(modifier = Modifier.height(32.dp))

    // Platform Behavior
    Text(
      text = "Platform Behavior",
      style = DocsTheme.typography.h2,
      color = DocsTheme.colors.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    LiquidGlassPlatformTable()

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
        var lensCenter by remember { mutableStateOf(Offset(100f, 100f)) }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              detectDragGestures { change, dragAmount ->
                lensCenter += dragAmount
                change.consume()
              }
            }
            .cloudy(radius = 15) // Optional: combine with Cloudy blur
            .liquidGlass(
              lensCenter = lensCenter,
              lensSize = Size(350f, 350f),
              cornerRadius = 50f,
              refraction = 0.25f,
              curve = 0.25f,
              dispersion = 0.0f,
              saturation = 1.0f,
              edge = 0.2f,
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
        • The effect uses SDF (Signed Distance Field) for crisp edges and normal-based refraction
        • Chromatic dispersion creates a prism-like RGB channel separation effect
        • For blur effects, use Modifier.cloudy() separately - the two modifiers are independent
        • On Android API < 33, refraction and dispersion have no effect (fallback mode)
        • Set cornerRadius to half of lensSize for a circular lens shape
        • The lensCenter should be updated based on touch/pointer input for interactive effects
      """.trimIndent(),
      style = DocsTheme.typography.body,
      color = DocsTheme.colors.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))
  }
}

@Composable
private fun LiquidGlassParameterTable() {
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

    LiquidGlassParamRow("lensCenter", "Offset", "Position of the glass lens center in pixels.")
    LiquidGlassParamRow("lensSize", "Size", "Size of the lens in pixels (width, height).")
    LiquidGlassParamRow(
      "cornerRadius",
      "Float",
      "Corner radius of the rounded rectangle lens shape.",
    )
    LiquidGlassParamRow("refraction", "Float", "How much the background distorts through the lens.")
    LiquidGlassParamRow("curve", "Float", "How strongly the lens curves at center vs edges.")
    LiquidGlassParamRow("dispersion", "Float", "Chromatic aberration (RGB channel separation).")
    LiquidGlassParamRow("saturation", "Float", "Color saturation. 1.0 = normal.")
    LiquidGlassParamRow("contrast", "Float", "Contrast adjustment. 1.0 = normal.")
    LiquidGlassParamRow("tint", "Color", "Optional color tint overlay.")
    LiquidGlassParamRow("edge", "Float", "Edge lighting/rim width.")
    LiquidGlassParamRow("enabled", "Boolean", "If false, disables the effect.")
  }
}

@Composable
private fun LiquidGlassParamRow(name: String, type: String, description: String) {
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
private fun DefaultValuesTable() {
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
        text = "Constant",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1.5f),
      )
      Text(
        text = "Value",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1f),
      )
    }

    DefaultValueRow("LENS_SIZE", "Size(350f, 350f)")
    DefaultValueRow("CORNER_RADIUS", "50f")
    DefaultValueRow("REFRACTION", "0.25f")
    DefaultValueRow("CURVE", "0.25f")
    DefaultValueRow("DISPERSION", "0.0f")
    DefaultValueRow("SATURATION", "1.0f")
    DefaultValueRow("CONTRAST", "1.0f")
    DefaultValueRow("TINT", "Color.Transparent")
    DefaultValueRow("EDGE", "0.2f")
  }
}

@Composable
private fun DefaultValueRow(constant: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(DocsTheme.colors.surface)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = "LiquidGlassDefaults.$constant",
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.primary,
      modifier = Modifier.weight(1.5f),
    )
    Text(
      text = value,
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.secondary,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun LiquidGlassPlatformTable() {
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
        text = "Features",
        style = DocsTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = DocsTheme.colors.onSurface,
        modifier = Modifier.weight(1.5f),
      )
    }

    LiquidGlassPlatformRow("Android 33+", "RuntimeShader (AGSL)", "Full effect")
    LiquidGlassPlatformRow("Android 23-32", "Fallback", "Saturation + edge only")
    LiquidGlassPlatformRow("iOS", "Skia RuntimeEffect (SKSL)", "Full effect")
    LiquidGlassPlatformRow("macOS", "Skia RuntimeEffect (SKSL)", "Full effect")
    LiquidGlassPlatformRow("Desktop (JVM)", "Skia RuntimeEffect (SKSL)", "Full effect")
    LiquidGlassPlatformRow("Web (WASM)", "Skia RuntimeEffect (SKSL)", "Full effect")
  }
}

@Composable
private fun LiquidGlassPlatformRow(platform: String, implementation: String, features: String) {
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
      text = features,
      style = DocsTheme.typography.code,
      color = DocsTheme.colors.secondary,
      modifier = Modifier.weight(1.5f),
    )
  }
}
