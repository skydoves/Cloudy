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
        uniform("blur", blur)
        uniform("aberration", aberration)
        uniform("saturation", saturation)
        uniform("edgeBrightness", edgeBrightness)
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
