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
package com.skydoves.cloudy.internal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skiko backend program — wraps a [RuntimeShaderBuilder] (its uniforms are the live per-draw state).
 * The source [RuntimeEffect] is retained too: it is the compiled artifact the builder was made from,
 * and keeping a reference keeps the compiled effect reachable for the builder's lifetime.
 */
internal actual class MirageBackendProgram(
  val builder: RuntimeShaderBuilder,
  @Suppress("unused") private val effect: RuntimeEffect,
)

/**
 * Compiles [compiled] into a skiko [RuntimeEffect] + [RuntimeShaderBuilder]. Skia is always present,
 * so there is no version gate and this never returns `null`; a source that fails to compile throws
 * from [RuntimeEffect.makeForShader] (surfaced, not swallowed).
 */
internal actual fun createBackendProgram(compiled: CompiledProgram): MirageBackendProgram? {
  val effect = RuntimeEffect.makeForShader(compiled.source)
  return MirageBackendProgram(RuntimeShaderBuilder(effect), effect)
}

internal actual fun MirageBackendProgram.uniformSink(): UniformSink = SkikoUniformSink(builder)

/**
 * makeRuntimeShader with input = null feeds the layer's own content as the `content` child, matching
 * the Android createRuntimeShaderEffect(shader, "content") path.
 */
internal actual fun MirageBackendProgram.asContentRenderEffect(): RenderEffect = ImageFilter
  .makeRuntimeShader(
    runtimeShaderBuilder = builder,
    shaderName = "content",
    input = null,
  )
  .asComposeRenderEffect()

/**
 * Skia bakes uniforms into the Shader at makeShader() time, so this rebuilds from the builder's
 * current uniforms each call - the node calls it after writing the per-draw values.
 */
internal actual fun MirageBackendProgram.asShaderBrush(): ShaderBrush =
  ShaderBrush(builder.makeShader().asComposeShader())

private class SkikoUniformSink(private val builder: RuntimeShaderBuilder) : UniformSink {

  override fun float(name: String, v: Float): Unit = builder.uniform(name, v)

  override fun float2(name: String, x: Float, y: Float): Unit = builder.uniform(name, x, y)

  override fun float4(name: String, x: Float, y: Float, z: Float, w: Float): Unit =
    builder.uniform(name, x, y, z, w)

  override fun int(name: String, v: Int): Unit = builder.uniform(name, v)

  override fun floatArray(name: String, v: FloatArray): Unit = builder.uniform(name, v)

  /**
   * Section 8-2 - skiko's RuntimeShaderBuilder has NO color-space-aware setter (verified against the skiko
   * 0.9.37.3 source: it exposes only uniform(Int.../Float.../FloatArray/Matrix...) and child(Shader/
   * ColorFilter) - there is no Color overload and no layout(color) handling). So, unlike Android's
   * setColorUniform, we convert the Color ourselves and write a plain float4. We pick sRGB
   * unpremultiplied as the canonical encoding: Color.convert(Srgb) normalises any source gamut (e.g.
   * a Display-P3 literal) to sRGB before reading components, matching how an sRGB layout(color) on
   * Android is treated. CAVEAT: because the shader receives raw sRGB float4 with no working-color-
   * space conversion, wide-gamut displays may render this color slightly differently from Android,
   * where setColorUniform performs the working-space transform. The bundled presets use only sRGB
   * literals, so this difference is a no-op in practice; it matters only for a wide-gamut color.
   */
  override fun color(name: String, c: Color) {
    val srgb = c.convert(ColorSpaces.Srgb)
    builder.uniform(name, srgb.red, srgb.green, srgb.blue, srgb.alpha)
  }

  /**
   * Bind an image as a child shader. The compose->skia bitmap bridge is public (asSkiaBitmap); the
   * Compose->skia TileMode map is not exported from compose-ui, so it is inlined below.
   */
  override fun texture(name: String, img: ImageBitmap?, tileMode: TileMode) {
    if (img == null) return
    val tile = tileMode.toSkiaFilterTileMode()
    val shader = Image.makeFromBitmap(img.asSkiaBitmap()).makeShader(tile, tile, null)
    builder.child(name, shader)
  }
}

/**
 * Compose TileMode -> skia FilterTileMode. Mirrors compose-ui's internal toSkiaTileMode(), which is
 * not visible outside its package.
 */
private fun TileMode.toSkiaFilterTileMode(): FilterTileMode = when (this) {
  TileMode.Repeated -> FilterTileMode.REPEAT
  TileMode.Mirror -> FilterTileMode.MIRROR
  TileMode.Decal -> FilterTileMode.DECAL
  else -> FilterTileMode.CLAMP
}
