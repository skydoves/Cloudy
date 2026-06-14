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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skiko implementation of [Modifier.mirage] — shared across iOS, macOS, Desktop, and Wasm.
 *
 * Compiles `PREAMBLE_SKSL + recipe.sksl` into a Skia [RuntimeEffect]/[RuntimeShaderBuilder], writes
 * the standard uniforms (+ the recipe's own uniforms via its `bindUniforms`) each draw, and applies
 * the result as a [RenderEffect] via [ImageFilter.makeRuntimeShader]. No API-level check is needed
 * (Skia is always present); preview and `enabled = false` are no-ops.
 */
@ExperimentalMirage
@Composable
public actual fun Modifier.mirage(
  recipe: MirageRecipe,
  lensCenter: Offset,
  lensSize: Size,
  cornerRadius: Float,
  light: LiquidGlassLight,
  time: Float,
  enabled: Boolean,
): Modifier {
  if (!enabled) {
    return this
  }

  if (LocalInspectionMode.current) {
    return this
  }

  // Cache key is the full source text literal: the preamble + this recipe's body identifies the
  // compiled program. The preamble varies by inputMode (Overlay drops the `content` sampler), so the
  // key includes inputMode; the effect rebuilds only when the recipe's SKSL or its mode changes.
  val source = remember(recipe.sksl, recipe.inputMode) {
    val preamble = when (recipe.inputMode) {
      MirageInputMode.ContentFilter -> PREAMBLE_SKSL
      MirageInputMode.Overlay -> PREAMBLE_OVERLAY_SKSL
    }
    preamble + recipe.sksl
  }
  val shaderBuilder = remember(source) {
    try {
      RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
    } catch (e: Exception) {
      // Surface the failure instead of silently rendering blank; the modifier then no-ops below.
      println("Mirage: RuntimeEffect compilation failed: ${e.message}")
      null
    }
  } ?: return this

  return when (recipe.inputMode) {
    // ContentFilter — unchanged from M1: the shader reads `content` and its output replaces the
    // layer, applied as a RenderEffect via makeRuntimeShader with the content child bound.
    MirageInputMode.ContentFilter -> this.graphicsLayer {
      val width = size.width
      val height = size.height

      if (width > 0 && height > 0) {
        val scope = SkikoMirageScope(shaderBuilder)

        // Standard uniforms — Skia throws if any declared uniform is left unset before
        // makeRuntimeShader, so write all of them every draw.
        scope.uniform("iResolution", width, height)
        scope.uniform("lensCenter", lensCenter.x, lensCenter.y)
        scope.uniform("lensSize", lensSize.width, lensSize.height)
        scope.uniform("cornerRadius", cornerRadius)
        // Draw-phase read: holder identity is stable, so a high-frequency light source invalidates
        // the draw without recomposing.
        val lightDir = light.direction.value
        scope.uniform("iLight", lightDir.x, lightDir.y)
        scope.uniform("iTime", time)

        recipe.bindUniforms(scope)
        renderEffect = ImageFilter
          .makeRuntimeShader(
            runtimeShaderBuilder = shaderBuilder,
            shaderName = "content",
            input = null, // null = use the source content from the layer
          )
          .asComposeRenderEffect()
      }
    }

    // Overlay — the shader is a pure generator drawn ON TOP of the unmodified content. Draw the
    // content first, then build a fresh Skia Shader (uniforms are baked at makeShader time, so it is
    // rebuilt each draw to pick up the latest uniform values) and fill via ShaderBrush + SrcOver.
    MirageInputMode.Overlay -> this.drawWithContent {
      drawContent()
      val width = size.width
      val height = size.height
      if (width > 0 && height > 0) {
        val scope = SkikoMirageScope(shaderBuilder)
        scope.uniform("iResolution", width, height)
        scope.uniform("lensCenter", lensCenter.x, lensCenter.y)
        scope.uniform("lensSize", lensSize.width, lensSize.height)
        scope.uniform("cornerRadius", cornerRadius)
        val lightDir = light.direction.value
        scope.uniform("iLight", lightDir.x, lightDir.y)
        scope.uniform("iTime", time)
        recipe.bindUniforms(scope)
        // makeShader() returns org.jetbrains.skia.Shader = Compose's Shader typealias on skiko.
        drawRect(brush = ShaderBrush(shaderBuilder.makeShader()), blendMode = BlendMode.SrcOver)
      }
    }
  }
}

/** Skiko [MirageScope] — forwards uniform writes to a Skia [RuntimeShaderBuilder]. */
@OptIn(ExperimentalMirage::class)
internal class SkikoMirageScope(private val builder: RuntimeShaderBuilder) : MirageScope {
  override fun uniform(name: String, value: Float): Unit = builder.uniform(name, value)

  override fun uniform(name: String, x: Float, y: Float): Unit = builder.uniform(name, x, y)

  override fun uniform(name: String, x: Float, y: Float, z: Float, w: Float): Unit =
    builder.uniform(name, x, y, z, w)
}
