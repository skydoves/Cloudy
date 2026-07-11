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

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import android.graphics.RenderEffect as AndroidRenderEffect

/**
 * Android backend program — a thin final wrapper over a sealed [AndroidBackend]. The wrapper matches
 * the `expect class` modality (final on both sides); the sealed payload is what the free-function seam
 * branches over exhaustively. (Wrapping rather than making the expect itself sealed keeps the skiko
 * actual an ordinary class, untouched by the band ladder.)
 */
internal actual class MirageBackendProgram(val backend: AndroidBackend)

/**
 * One backend leaf per [MirageBackendBand]:
 * - [Agsl] wraps a [RuntimeShader] (API 33+); the original content-bound `RenderEffect` path.
 * - [Gles] runs a translated GLSL ES program on an offscreen FBO (API 29-32); applied by blitting a
 *   read-back [ImageBitmap] rather than a `RenderEffect`. Fleshed out in M3.
 * - [ColorGrade] reproduces a Colorize optic with an affine grade (API 23-28); applied by blitting the
 *   source through a `ColorMatrixColorFilter` (RenderEffect is API 31+, unavailable in this band).
 */
internal sealed interface AndroidBackend {

  class Agsl
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  constructor(val shader: RuntimeShader) :
    AndroidBackend

  /** API 29-32 GLES program: the AGSL source translated to GLSL ES, run through an offscreen FBO. */
  class Gles(val program: GlProgram) : AndroidBackend

  /**
   * API 23-28 affine grade. Holds this draw's 4x5 grade values, rebuilt each draw from the current
   * shadow/highlight/amount so a `filter(Duotone){ shadow(Red) }` override is honored (not just the
   * schema default). Safe as mutable shared state: a Colorize applies synchronously through the chain
   * (bind -> filterApplication -> layer.colorFilter, no suspension), and Compose draw is single-thread,
   * so no two draws interleave a write with a read.
   */
  class ColorGrade(initial: FloatArray) : AndroidBackend {
    val matrix: android.graphics.ColorMatrix = android.graphics.ColorMatrix(initial)

    fun update(values: FloatArray) {
      matrix.set(values)
    }
  }
}

/**
 * Compiles [compiled] into the backend program for the running band.
 *
 * - [MirageBackendBand.Agsl] : a [RuntimeShader] from the AGSL source.
 * - [MirageBackendBand.Gles] / [MirageBackendBand.ColorGrade] : not yet built (M2/M3) — returns
 *   `null` so the caller no-ops exactly as it did below API 33 before.
 *
 * A source that fails to compile on 33+ throws from the `RuntimeShader` constructor (surfaced, not
 * swallowed).
 */
internal actual fun createBackendProgram(compiled: CompiledProgram): MirageBackendProgram? =
  when (MirageBackendBand.resolve(Build.VERSION.SDK_INT)) {
    MirageBackendBand.Agsl ->
      MirageBackendProgram(AndroidBackend.Agsl(RuntimeShader(compiled.source)))

    // API 23-28: only an affine Colorize (Duotone) is reproducible as a color matrix; any other optic
    // (lens Composite / Generate) is unsupported and stays a no-op, so the node draws the fallback. The
    // matrix is seeded from schema defaults and rebuilt each draw from the current params (see the sink).
    MirageBackendBand.ColorGrade ->
      if (isColorGradeReproducible(compiled)) {
        // Seed with identity (no grade). The chain rebuilds the matrix from the draw's params in bind()
        // before filterApplication() reads it, so this seed only guards a stray pre-bind read.
        MirageBackendProgram(AndroidBackend.ColorGrade(IDENTITY_COLOR_MATRIX))
      } else {
        null
      }

    // API 29-32: translate the AGSL to GLSL ES and build a GL program, for content-filtering optics
    // only. Declined (-> null -> no-op): a raw optic (untranslatable), a time-driven optic (animation
    // is out of scope for this band), and a Generate overlay (overlays use a ShaderBrush, not the FBO
    // filter path). The backdrop node runs the result via an async capture, so a self-lit node still
    // no-ops on this band (self-lit has no content-version cache key — see MirageBackdropNode).
    MirageBackendBand.Gles -> when {
      compiled.isRaw || compiled.usesTime || compiled.category == OpticCategory.Generate -> null

      else -> {
        val glsl = MirageGlslEs.translate(compiled.source)
        MirageBackendProgram(AndroidBackend.Gles(GlProgram(glsl)))
      }
    }
  }

internal actual fun MirageBackendProgram.uniformSink(): UniformSink = when (val b = backend) {
  is AndroidBackend.Agsl -> AndroidUniformSink(b.shader)

  // ColorGrade captures this draw's shadow/highlight/amount and rebuilds its matrix, so a per-draw
  // params override is honored (not just the schema default).
  is AndroidBackend.ColorGrade -> ColorGradeSink(b)

  // GLES binds through prepareGlesBlit (fresh per-draw list), not this generic sink.
  is AndroidBackend.Gles -> NoOpUniformSink
}

/**
 * Builds a content-bound [RenderEffect]. Only the [AndroidBackend.Agsl] leaf is a RenderEffect
 * (API 33+); the other leaves apply via [FilterApplication.Blit] instead — [RenderEffect] itself is
 * API 31+, unavailable in their bands — so [filterApplication] steers the chain past this for them and
 * a call here is a wiring bug.
 */
internal actual fun MirageBackendProgram.asContentRenderEffect(): RenderEffect =
  when (val b = backend) {
    is AndroidBackend.Agsl ->
      AndroidRenderEffect
        .createRuntimeShaderEffect(b.shader, "content")
        .asComposeRenderEffect()

    is AndroidBackend.Gles, is AndroidBackend.ColorGrade ->
      error("only the Agsl backend applies via RenderEffect; others use FilterApplication.Blit")
  }

/**
 * How this backend applies to a stage's content:
 * - AGSL : a content-bound RenderEffect.
 * - ColorGrade : a `ColorMatrixColorFilter` set on the stage layer (works on API 23+; RenderEffect is
 *   API 31+, so not usable in this band).
 * - GLES : an FBO blit. The [FilterApplication.Blit] here is a **detection marker** (identity) — the
 *   real per-draw transform, with uniforms bound into a fresh list, comes from [prepareGlesBlit]. A
 *   self-lit content node has no async capture path for it, so it treats this marker as unsupported and
 *   no-ops (GLES is backdrop-only; see the node and planRenders).
 */
internal actual fun MirageBackendProgram.filterApplication(): FilterApplication =
  when (val b = backend) {
    is AndroidBackend.Agsl -> FilterApplication.Effect(asContentRenderEffect())

    is AndroidBackend.ColorGrade ->
      FilterApplication.ColorFilter(
        // A fresh ColorMatrixColorFilter over the matrix the sink just rebuilt for this draw.
        android.graphics.ColorMatrixColorFilter(b.matrix).asComposeColorFilter(),
      )

    is AndroidBackend.Gles -> FilterApplication.Blit { it }
  }

/**
 * Builds the GLES transform with this draw's uniforms bound into a **fresh** recording list (never a
 * shared field — the GLES program is process-wide, so a shared record would race concurrent nodes /
 * frames). Only the GLES leaf returns a closure; every other backend returns `null`.
 */
internal actual fun MirageBackendProgram.prepareGlesBlit(
  cached: CachedProgram,
  params: com.skydoves.cloudy.MirageParams,
  paramsBlock: (com.skydoves.cloudy.MirageParams.() -> Unit)?,
  width: Float,
  height: Float,
  density: Float,
  time: Float,
): ((ImageBitmap) -> ImageBitmap)? {
  val gles = backend as? AndroidBackend.Gles ?: return null
  val (sink, writes) = gles.program.uniformSink()
  bindUniformsInto(sink, cached, params, paramsBlock, width, height, density, time)
  return { input -> gles.program.render(input.asAndroidBitmap(), writes)?.asImageBitmap() ?: input }
}

internal actual fun MirageBackendProgram.asShaderBrush(): ShaderBrush = when (val b = backend) {
  is AndroidBackend.Agsl -> ShaderBrush(b.shader)

  // Overlays (Generate optics) only ever build an Agsl program: a Generate kernel is not translatable
  // to a ColorGrade and is a no-op on Gles, so neither leaf reaches an overlay brush.
  is AndroidBackend.Gles, is AndroidBackend.ColorGrade ->
    error("only the Agsl backend supports an overlay ShaderBrush")
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidUniformSink(private val shader: RuntimeShader) : UniformSink {

  override fun float(name: String, v: Float): Unit = shader.setFloatUniform(name, v)

  override fun float2(name: String, x: Float, y: Float): Unit = shader.setFloatUniform(name, x, y)

  override fun float4(name: String, x: Float, y: Float, z: Float, w: Float): Unit =
    shader.setFloatUniform(name, x, y, z, w)

  override fun int(name: String, v: Int): Unit = shader.setIntUniform(name, v)

  override fun floatArray(name: String, v: FloatArray): Unit = shader.setFloatUniform(name, v)

  /**
   * Android's setColorUniform(name, sRGB-int) converts the color into the shader's working color
   * space for us - no manual conversion (contrast the skiko actual, which has no color-aware setter).
   * This is the one platform where layout(color) is safe. toArgb() is the KMP-common sRGB bridge.
   */
  override fun color(name: String, c: Color): Unit = shader.setColorUniform(name, c.toArgb())

  override fun texture(name: String, img: ImageBitmap?, tileMode: TileMode) {
    if (img == null) return
    val tile = tileMode.toAndroidTileMode()
    val bitmapShader = BitmapShader(img.asAndroidBitmap(), tile, tile)
    shader.setInputShader(name, bitmapShader)
  }
}

/**
 * Sink for the GLES leaf when reached through the generic binder (it actually binds via
 * prepareGlesBlit's fresh list): a write has nowhere to go, so it is dropped rather than erroring,
 * because the shared binder walks every schema slot regardless of backend.
 */
private object NoOpUniformSink : UniformSink {
  override fun float(name: String, v: Float) = Unit
  override fun float2(name: String, x: Float, y: Float) = Unit
  override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) = Unit
  override fun int(name: String, v: Int) = Unit
  override fun floatArray(name: String, v: FloatArray) = Unit
  override fun color(name: String, c: Color) = Unit
  override fun texture(name: String, img: ImageBitmap?, tileMode: TileMode) = Unit
}

/** Identity 4x5 (no grade); the ColorGrade seed before the first per-draw rebuild. */
private val IDENTITY_COLOR_MATRIX = floatArrayOf(
  1f, 0f, 0f, 0f, 0f,
  0f, 1f, 0f, 0f, 0f,
  0f, 0f, 1f, 0f, 0f,
  0f, 0f, 0f, 1f, 0f,
)

/**
 * Captures the Duotone params (shadow / highlight / amount) the binder walks each draw and rebuilds the
 * [AndroidBackend.ColorGrade] matrix from them, so a per-draw override reaches the grade. Every non-
 * Duotone write is ignored (a reproducible ColorGrade optic has only these three). Rebuilds on each
 * relevant write (idempotent, 20 floats) so ordering within the walk does not matter.
 */
private class ColorGradeSink(private val leaf: AndroidBackend.ColorGrade) : UniformSink {
  private var shadow: Color = Color(0f, 0f, 0f)
  private var highlight: Color = Color(1f, 1f, 1f)
  private var amount: Float = 1f

  private fun rebuild() = leaf.update(duotoneMatrix(shadow, highlight, amount))

  override fun color(name: String, c: Color) {
    when (name) {
      "shadow" -> shadow = c
      "highlight" -> highlight = c
      else -> return
    }
    rebuild()
  }

  override fun float(name: String, v: Float) {
    if (name != "amount") return
    amount = v
    rebuild()
  }

  override fun float2(name: String, x: Float, y: Float) = Unit
  override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) = Unit
  override fun int(name: String, v: Int) = Unit
  override fun floatArray(name: String, v: FloatArray) = Unit
  override fun texture(name: String, img: ImageBitmap?, tileMode: TileMode) = Unit
}

/**
 * TileMode is a Compose type; map it to the framework enum the BitmapShader wants. Decal is API 31+,
 * which is always satisfied here (this sink only exists on API 33+).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun TileMode.toAndroidTileMode(): Shader.TileMode = when (this) {
  TileMode.Repeated -> Shader.TileMode.REPEAT
  TileMode.Mirror -> Shader.TileMode.MIRROR
  TileMode.Decal -> Shader.TileMode.DECAL
  else -> Shader.TileMode.CLAMP
}
