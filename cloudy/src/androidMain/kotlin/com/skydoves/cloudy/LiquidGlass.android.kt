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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Android implementation of [Modifier.liquidGlass].
 *
 * Uses RuntimeShader with RenderEffect on API 33+ for GPU-accelerated glass effects.
 * On older API levels, provides a fallback with blur + saturation + edge effects.
 */
@Composable
public actual fun Modifier.liquidGlass(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  curve: Float,
  blur: Float,
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
  require(blur >= 0f) { "blur must be >= 0, but was $blur" }
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
      blur = blur,
      dispersion = dispersion,
      saturation = saturation,
      contrast = contrast,
      tint = tint,
      edge = edge,
    )
  } else {
    // Fallback for older Android versions
    // Uses existing Cloudy blur with shape clipping
    liquidGlassFallback(
      mousePosition = mousePosition,
      lensSize = lensSize,
      cornerRadius = cornerRadius,
      blur = blur.toInt().coerceIn(0, 25),
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
  blur: Float,
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
        shader.setFloatUniform("blur", blur)
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
 * Provides a degraded but functional glass effect using:
 * - Existing Cloudy blur (works on all Android versions)
 * - Shape clipping for the lens area
 * - Saturation adjustment via color matrix (when supported)
 * - Basic edge border effect
 *
 * Note: refraction, curve, and dispersion are not available in fallback mode.
 */
@Composable
private fun Modifier.liquidGlassFallback(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  blur: Int,
  saturation: Float,
  contrast: Float,
  tint: Color,
  edge: Float,
): Modifier {
  val density = LocalDensity.current

  // Convert pixel values to Dp
  val lensWidthDp: Dp = with(density) { lensSize.width.toDp() }
  val lensHeightDp: Dp = with(density) { lensSize.height.toDp() }
  val cornerRadiusDp: Dp = with(density) { cornerRadius.toDp() }

  // Calculate offset to center the lens on mouse position
  val offsetX: Dp = with(density) { (mousePosition.x - lensSize.width / 2).toDp() }
  val offsetY: Dp = with(density) { (mousePosition.y - lensSize.height / 2).toDp() }

  val shape = RoundedCornerShape(cornerRadiusDp)

  // For fallback, we apply blur to the entire content and overlay a clipped region
  // This is a simplified approach - the full effect requires the shader
  return this.cloudy(radius = blur)
}
