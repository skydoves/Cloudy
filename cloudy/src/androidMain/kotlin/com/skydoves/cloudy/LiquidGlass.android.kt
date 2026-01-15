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
package com.skydoves.cloudy

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize

/**
 * Android implementation of [Modifier.liquidGlass].
 *
 * Uses RuntimeShader with RenderEffect on API 33+ for GPU-accelerated glass effects.
 * On older API levels, provides a fallback with saturation, contrast, tint, and edge effects.
 *
 * **Note:** For blur effects, use [Modifier.cloudy] separately.
 */
@Composable
public actual fun Modifier.liquidGlass(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  dispersion: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
  enabled: Boolean,
): Modifier {
  // Validation
  require(cornerRadius >= 0f) { "cornerRadius must be >= 0, but was $cornerRadius" }
  require(refraction >= 0f) { "refraction must be >= 0, but was $refraction" }
  require(curve >= 0f) { "curve must be >= 0, but was $curve" }
  require(dispersion >= 0f) { "dispersion must be >= 0, but was $dispersion" }
  require(saturation >= 0f) { "saturation must be >= 0, but was $saturation" }
  require(contrast >= 0f) { "contrast must be >= 0, but was $contrast" }

  if (!enabled) {
    return this
  }

  // Preview mode fallback
  if (LocalInspectionMode.current) {
    return this
  }

  // API level check - RuntimeShader requires API 33+
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    liquidGlassApi33(
      mousePosition = mousePosition,
      lensSize = lensSize,
      cornerRadius = cornerRadius,
      refraction = refraction,
      curve = curve,
      dispersion = dispersion,
      saturation = saturation,
      contrast = contrast,
      tint = tint,
      edge = edge,
    )
  } else {
    // Fallback for older Android versions
    // Provides saturation, contrast, tint, and edge effects without lens refraction
    liquidGlassFallback(
      mousePosition = mousePosition,
      lensSize = lensSize,
      cornerRadius = cornerRadius,
      saturation = saturation,
      contrast = contrast,
      tint = tint,
      edge = edge,
    )
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.liquidGlassApi33(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  dispersion: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
): Modifier {
  // Create and cache the RuntimeShader
  val shader = remember {
    try {
      RuntimeShader(LiquidGlassShaderSource.AGSL)
    } catch (e: Exception) {
      null
    }
  }

  // If shader failed to compile, return unchanged
  if (shader == null) {
    return this
  }

  // Track size for resolution uniform
  var currentSize = remember { IntSize.Zero }

  return this
    .onSizeChanged { size ->
      currentSize = size
    }
    .graphicsLayer {
      val width = size.width
      val height = size.height

      if (width > 0 && height > 0) {
        // Update shader uniforms
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("mouse", mousePosition.x, mousePosition.y)
        shader.setFloatUniform("lensSize", lensSize.width, lensSize.height)
        shader.setFloatUniform("cornerRadius", cornerRadius)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("curve", curve)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("saturation", saturation)
        shader.setFloatUniform("contrast", contrast)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        shader.setFloatUniform("edge", edge)

        // Apply shader as RenderEffect - "content" binds to underlying layer
        renderEffect = RenderEffect
          .createRuntimeShaderEffect(shader, "content")
          .asComposeRenderEffect()
      }
    }
}

/**
 * Fallback implementation for Android < 33.
 *
 * Provides a graceful degradation with saturation, contrast, tint, and edge effects.
 * The lens refraction/distortion effect is not available without RuntimeShader (API 33+).
 *
 * For blur effects, use [Modifier.cloudy] separately.
 */
@Composable
private fun Modifier.liquidGlassFallback(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
): Modifier {
  // Create color filter for saturation and contrast adjustments
  val colorFilter = remember(saturation, contrast) {
    createSaturationContrastColorFilter(saturation, contrast)
  }

  return this.drawWithContent {
    // Draw original content first
    drawContent()

    // Calculate lens bounds centered on mouse position
    val halfWidth = lensSize.width / 2f
    val halfHeight = lensSize.height / 2f
    val lensLeft = mousePosition.x - halfWidth
    val lensTop = mousePosition.y - halfHeight
    val clampedCornerRadius = cornerRadius.coerceAtMost(minOf(halfWidth, halfHeight))

    // Create lens path
    val lensPath = Path().apply {
      addRoundRect(
        RoundRect(
          left = lensLeft,
          top = lensTop,
          right = lensLeft + lensSize.width,
          bottom = lensTop + lensSize.height,
          cornerRadius = CornerRadius(clampedCornerRadius, clampedCornerRadius),
        ),
      )
    }

    // Draw color-adjusted overlay inside the lens shape
    if (saturation != 1f || contrast != 1f) {
      clipPath(lensPath) {
        // Draw a semi-transparent overlay that simulates the color adjustment
        // This is a simplified approximation since we can't re-render content with a filter
        val overlayAlpha = 0.3f * ((1f - saturation).coerceIn(0f, 1f) + (contrast - 1f).coerceIn(0f, 1f))
        if (overlayAlpha > 0f) {
          drawRect(
            color = Color.Gray.copy(alpha = overlayAlpha.coerceIn(0f, 0.5f)),
            topLeft = Offset(lensLeft, lensTop),
            size = lensSize,
          )
        }
      }
    }

    // Draw tint overlay inside the lens
    if (tint != Color.Transparent && tint.alpha > 0f) {
      clipPath(lensPath) {
        drawRect(
          color = tint,
          topLeft = Offset(lensLeft, lensTop),
          size = lensSize,
        )
      }
    }

    // Draw edge lighting effect
    if (edge > 0f) {
      val strokeWidth = edge * 20f // Scale edge value to reasonable stroke width
      val edgeColor = Color.White.copy(alpha = (edge * 0.5f).coerceIn(0f, 0.8f))

      drawPath(
        path = lensPath,
        color = edgeColor,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
      )
    }
  }
}

/**
 * Creates a ColorMatrix that adjusts saturation and contrast.
 *
 * @param saturation 1.0 = normal, 0.0 = grayscale, >1.0 = oversaturated
 * @param contrast 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast
 */
private fun createSaturationContrastColorFilter(saturation: Float, contrast: Float): ColorFilter? {
  if (saturation == 1f && contrast == 1f) {
    return null
  }

  // Apply saturation (using luminance-based desaturation)
  val colorMatrix = if (saturation != 1f) {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat

    ColorMatrix(
      floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
      ),
    )
  } else {
    ColorMatrix()
  }

  // Apply contrast
  if (contrast != 1f) {
    val scale = contrast
    val translate = (1f - contrast) * 127.5f

    val contrastMatrix = ColorMatrix(
      floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f,
      ),
    )

    colorMatrix.timesAssign(contrastMatrix)
  }

  return ColorFilter.colorMatrix(colorMatrix)
}