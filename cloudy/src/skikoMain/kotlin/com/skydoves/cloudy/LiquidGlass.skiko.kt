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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skiko implementation of [Modifier.liquidGlass].
 *
 * Uses Skia's RuntimeEffect with ImageFilter.makeRuntimeShader for GPU-accelerated glass effects.
 * This implementation is shared across iOS, macOS, JVM Desktop, and WASM platforms.
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

  // Create and cache the RuntimeEffect
  val runtimeEffect = remember {
    try {
      RuntimeEffect.makeForShader(LiquidGlassShaderSource.SKSL)
    } catch (e: Exception) {
      null
    }
  }

  // If shader failed to compile, return unchanged
  if (runtimeEffect == null) {
    return this
  }

  return this.graphicsLayer {
    val width = size.width
    val height = size.height

    if (width > 0 && height > 0) {
      // Create RuntimeShaderBuilder and set uniforms
      val shaderBuilder = RuntimeShaderBuilder(runtimeEffect).apply {
        uniform("resolution", width, height)
        uniform("mouse", mousePosition.x, mousePosition.y)
        uniform("lensSize", lensSize.width, lensSize.height)
        uniform("cornerRadius", cornerRadius)
        uniform("refraction", refraction)
        uniform("curve", curve)
        uniform("dispersion", dispersion)
        uniform("saturation", saturation)
        uniform("contrast", contrast)
        uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
        uniform("edge", edge)
      }

      // Create ImageFilter with RuntimeShader
      // "content" binds to the underlying content (null input = source content)
      val imageFilter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = shaderBuilder,
        shaderName = "content",
        input = null, // null means use the source content from the layer
      )

      // Apply as Compose RenderEffect
      renderEffect = imageFilter.asComposeRenderEffect()
    }
  }
}