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
  lensCenter: Offset,
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
  require(lensSize.width > 0f) { "lensSize.width must be > 0, but was ${lensSize.width}" }
  require(lensSize.height > 0f) { "lensSize.height must be > 0, but was ${lensSize.height}" }
  require(cornerRadius >= 0f) { "cornerRadius must be >= 0, but was $cornerRadius" }
  require(refraction >= 0f) { "refraction must be >= 0, but was $refraction" }
  require(curve >= 0f) { "curve must be >= 0, but was $curve" }
  require(dispersion >= 0f) { "dispersion must be >= 0, but was $dispersion" }
  require(saturation >= 0f) { "saturation must be >= 0, but was $saturation" }
  require(contrast >= 0f) { "contrast must be >= 0, but was $contrast" }
  require(edge >= 0f) { "edge must be >= 0, but was $edge" }

  if (!enabled) {
    return this
  }

  // Create and cache the RuntimeEffect and RuntimeShaderBuilder
  val shaderBuilder = remember {
    try {
      val effect = RuntimeEffect.makeForShader(LiquidGlassShaderSource.SKSL)
      RuntimeShaderBuilder(effect)
    } catch (e: Exception) {
      null
    }
  }

  // If shader failed to compile, return unchanged
  if (shaderBuilder == null) {
    return this
  }

  return this.graphicsLayer {
    val width = size.width
    val height = size.height

    if (width > 0 && height > 0) {
      // Update uniforms on the cached builder
      shaderBuilder.uniform("resolution", width, height)
      shaderBuilder.uniform("lensCenter", lensCenter.x, lensCenter.y)
      shaderBuilder.uniform("lensSize", lensSize.width, lensSize.height)
      shaderBuilder.uniform("cornerRadius", cornerRadius)
      shaderBuilder.uniform("refraction", refraction)
      shaderBuilder.uniform("curve", curve)
      shaderBuilder.uniform("dispersion", dispersion)
      shaderBuilder.uniform("saturation", saturation)
      shaderBuilder.uniform("contrast", contrast)
      shaderBuilder.uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
      shaderBuilder.uniform("edge", edge)

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
