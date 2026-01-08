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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize

/**
 * Android implementation of [Modifier.liquidGlass].
 *
 * Uses RuntimeShader with RenderEffect on API 33+ for GPU-accelerated glass effects.
 * On older API levels, returns the modifier unchanged (no-op fallback).
 */
@Composable
public actual fun Modifier.liquidGlass(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  blur: Float,
  aberration: Float,
  saturation: Float,
  edgeBrightness: Float,
  enabled: Boolean,
): Modifier {
  // Validation
  require(cornerRadius >= 0f) { "cornerRadius must be >= 0, but was $cornerRadius" }
  require(refraction >= 0f) { "refraction must be >= 0, but was $refraction" }
  require(blur >= 0f) { "blur must be >= 0, but was $blur" }
  require(aberration >= 0f) { "aberration must be >= 0, but was $aberration" }
  require(saturation >= 0f) { "saturation must be >= 0, but was $saturation" }

  if (!enabled) {
    return this
  }

  // Preview mode fallback
  if (LocalInspectionMode.current) {
    return this
  }

  // API level check - RuntimeShader requires API 33+
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    return this
  }

  return liquidGlassApi33(
    mousePosition = mousePosition,
    lensSize = lensSize,
    cornerRadius = cornerRadius,
    refraction = refraction,
    blur = blur,
    aberration = aberration,
    saturation = saturation,
    edgeBrightness = edgeBrightness,
  )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.liquidGlassApi33(
  mousePosition: Offset,
  lensSize: Size,
  cornerRadius: Float,
  refraction: Float,
  blur: Float,
  aberration: Float,
  saturation: Float,
  edgeBrightness: Float,
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
        shader.setFloatUniform("blur", blur)
        shader.setFloatUniform("aberration", aberration)
        shader.setFloatUniform("saturation", saturation)
        shader.setFloatUniform("edgeBrightness", edgeBrightness)

        // Apply shader as RenderEffect - "content" binds to underlying layer
        renderEffect = RenderEffect
          .createRuntimeShaderEffect(shader, "content")
          .asComposeRenderEffect()
      }
    }
}
