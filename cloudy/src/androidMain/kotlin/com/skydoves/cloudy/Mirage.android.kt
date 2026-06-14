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
import android.util.Log
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

/**
 * Android implementation of [Modifier.mirage].
 *
 * Compiles `PREAMBLE_AGSL + recipe.agsl` into a single [RuntimeShader] (API 33+), writes the
 * standard uniforms (+ the recipe's own uniforms via its `bindUniforms`) each draw, and applies the
 * result as a [RenderEffect] bound to the layer's `content`. API < 33, preview, and `enabled = false`
 * are no-ops (the modifier is returned unchanged).
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

  // Preview mode fallback — RuntimeShader is unavailable in the inspection renderer.
  if (LocalInspectionMode.current) {
    return this
  }

  // RuntimeShader requires API 33+; older levels are a no-op (no fallback for arbitrary recipes).
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    return this
  }

  // Cache key is the full source text literal: the preamble + this recipe's body identifies the
  // compiled program. The preamble varies by inputMode (Overlay drops the `content` sampler), so the
  // key includes inputMode; the shader recompiles only when the recipe's AGSL or its mode changes.
  val source = remember(recipe.agsl, recipe.inputMode) {
    val preamble = when (recipe.inputMode) {
      MirageInputMode.ContentFilter -> PREAMBLE_AGSL
      MirageInputMode.Overlay -> PREAMBLE_OVERLAY_AGSL
    }
    preamble + recipe.agsl
  }
  val shader = remember(source) {
    try {
      RuntimeShader(source)
    } catch (e: Exception) {
      // Surface the failure instead of silently rendering blank; the modifier then no-ops below.
      Log.w("Mirage", "RuntimeShader compilation failed", e)
      null
    }
  } ?: return this

  return when (recipe.inputMode) {
    // ContentFilter — unchanged from M1: the shader reads `content` and its output replaces the
    // layer, applied as a RenderEffect with the content child bound.
    MirageInputMode.ContentFilter -> this.graphicsLayer {
      val width = size.width
      val height = size.height

      if (width > 0 && height > 0) {
        val scope = AndroidMirageScope(shader)

        // Standard uniforms — written every draw (the shader requires every declared uniform set).
        scope.uniform("iResolution", width, height)
        scope.uniform("lensCenter", lensCenter.x, lensCenter.y)
        scope.uniform("lensSize", lensSize.width, lensSize.height)
        scope.uniform("cornerRadius", cornerRadius)
        // Draw-phase read: only the value changes per tick (holder identity is stable), so a
        // high-frequency light source invalidates the draw without recomposing.
        val lightDir = light.direction.value
        scope.uniform("iLight", lightDir.x, lightDir.y)
        scope.uniform("iTime", time)

        recipe.bindUniforms(scope)
        renderEffect = RenderEffect
          .createRuntimeShaderEffect(shader, "content")
          .asComposeRenderEffect()
      }
    }

    // Overlay — the shader is a pure generator drawn ON TOP of the unmodified content. Draw the
    // content first, then fill with a ShaderBrush over the RuntimeShader (SrcOver). No content child
    // is bound (the Overlay preamble omits it and the body never calls content.eval).
    MirageInputMode.Overlay -> this.drawWithContent {
      drawContent()
      val width = size.width
      val height = size.height
      if (width > 0 && height > 0) {
        val scope = AndroidMirageScope(shader)
        scope.uniform("iResolution", width, height)
        scope.uniform("lensCenter", lensCenter.x, lensCenter.y)
        scope.uniform("lensSize", lensSize.width, lensSize.height)
        scope.uniform("cornerRadius", cornerRadius)
        val lightDir = light.direction.value
        scope.uniform("iLight", lightDir.x, lightDir.y)
        scope.uniform("iTime", time)
        recipe.bindUniforms(scope)
        // RuntimeShader extends android.graphics.Shader, so ShaderBrush(shader) typechecks.
        drawRect(brush = ShaderBrush(shader), blendMode = BlendMode.SrcOver)
      }
    }
  }
}

/** Android [MirageScope] — forwards uniform writes to a [RuntimeShader]. */
@OptIn(ExperimentalMirage::class)
internal class AndroidMirageScope(private val shader: RuntimeShader) : MirageScope {
  override fun uniform(name: String, value: Float): Unit = shader.setFloatUniform(name, value)

  override fun uniform(name: String, x: Float, y: Float): Unit = shader.setFloatUniform(name, x, y)

  override fun uniform(name: String, x: Float, y: Float, z: Float, w: Float): Unit =
    shader.setFloatUniform(name, x, y, z, w)
}
