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
@file:OptIn(ExperimentalMirage::class)

package com.skydoves.cloudy

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skydoves.cloudy.internal.CompiledProgram
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.GlProgram
import com.skydoves.cloudy.internal.MirageCompiler
import com.skydoves.cloudy.internal.MirageGlslEs
import com.skydoves.cloudy.internal.UniformSink
import com.skydoves.cloudy.internal.colorGradeMatrixOf
import com.skydoves.cloudy.internal.resetToDefaults
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * On-device validation of the GLES pipeline (translator + [GlProgram] + GlEnv roundtrip) on the API
 * 29-32 band. Runs real optics through the GL program and checks the output:
 *
 * - **Duotone (Colorize)** must match the affine `ColorMatrix` reference (the affine-grade path, proven
 *   exact on desktop) within a modest tolerance — this catches translation, Y-flip, and sampler bugs, since a
 *   flipped or mis-sampled content would diverge hugely from the per-pixel matrix result.
 * - **Chromatic (Composite, lens preamble)** must actually alter the content (non-passthrough) — the
 *   lens kernel compiled and ran through GL.
 */
@RunWith(AndroidJUnit4::class)
public class GlProgramMatchTest {

  @Test
  public fun duotoneGlMatchesTheColorMatrixReference() {
    val content = gradientContent(64, 64)

    val compiled = MirageCompiler.compile(MirageShaders.Duotone, Dialect.GlslEs)
    val program = GlProgram(MirageGlslEs.translate(compiled.source))

    // Bind the optic's schema defaults through the recording sink, exactly as the node's binder does.
    val (sink, writes) = program.uniformSink()
    bindSchemaDefaults(sink, compiled)
    val glOut = runBlocking { program.render(content, writes) }
    assertNotNull("GL render returned null on the GLES band", glOut)

    val params = defaultParams(compiled)
    val matrix = colorGradeMatrixOf(compiled, params)
    val reference = applyMatrix(matrix, content)

    // Tolerance covers GL bilinear sampling at texel centers + sRGB round-trip on SwiftShader. The
    // point is "same grade, right orientation"; a Y-flip or wrong sampler would blow far past this.
    val mad = meanAbsDiff(glOut!!, reference)
    assertTrue("GL Duotone diverged from the ColorMatrix reference: MAD=$mad", mad < 6.0)
  }

  @Test
  public fun chromaticGlActuallyTransformsContent() {
    val content = gradientContent(64, 64)
    val compiled = MirageCompiler.compile(MirageShaders.Chromatic, Dialect.GlslEs)
    val program = GlProgram(MirageGlslEs.translate(compiled.source))

    val (sink, writes) = program.uniformSink()
    bindSchemaDefaults(sink, compiled)
    // Frame the lens over the whole raster so pixels take the thin-film branch, not the sdf early-out.
    sink.float2("lensCenter", 32f, 32f)
    sink.float2("lensSize", 64f, 64f)
    sink.float("cornerRadius", 0f)

    val glOut = runBlocking { program.render(content, writes) }
    assertNotNull("GL render returned null (Chromatic lens kernel failed to compile/run?)", glOut)
    assertTrue(
      "Chromatic GL output is identical to content (kernel did nothing)",
      meanAbsDiff(glOut!!, content) > 1.0,
    )
  }

  /**
   * The lens kernel translated to GLSL ES ([GlProgram], the 29-32 band) must match the same optic run
   * natively as an AGSL [RuntimeShader] (the 33+ band) — proving the translator, not just that "the GL
   * program did *something*". A vendor GPU on 33+ runs both in one process, so this is the
   * cross-check the emulator's SwiftShader can't give.
   *
   * The lens is framed over the whole 64x64 raster (center 32,32 / size 64,64 / cornerRadius 0) so every
   * pixel takes the refraction/thin-film branch rather than the sdf early-out. Both paths bind the
   * schema defaults from the *same* [CompiledProgram] (no hand-copied values), then override the lens
   * frame identically, so any divergence is a translation bug, not a setup drift.
   *
   * The tolerance is a bound the assert message always prints the measured MAD against, so a device run
   * still reports the number even when it passes. Bilinear content.eval vs a GL bilinear texture fetch
   * on refracted (sub-pixel) coords is where any real divergence shows up.
   */
  @Test
  public fun chromaticGlMatchesAgslReference() {
    assertLensOpticMatches(MirageShaders.Chromatic)
  }

  @Test
  public fun specularGlMatchesAgslReference() {
    assertLensOpticMatches(MirageShaders.Specular)
  }
}

// Measured on a vendor GPU: the lens optics stay well under 1.0 (Chromatic ~0.017, Specular ~0.20)
// against the 33+ AGSL reference, so the GLSL-ES translation is pixel-tight. 1.0 leaves headroom over
// the worst optic while still catching a real regression (a Y-flip or coordinate bug blows the MAD up
// by orders of magnitude).
private const val GL_AGSL_MATCH_TOL = 1.0

/** The full-raster lens frame both paths share, so a divergence is a translation bug, not a setup skew. */
private const val LENS_FRAME = 64f

/**
 * Renders [optic] through both backends at 64x64 and asserts the GLES output matches the AGSL reference.
 * The MAD is always in the failure message so a passing-or-failing device run still reports the number.
 */
@OptIn(ExperimentalMirage::class)
private fun assertLensOpticMatches(optic: MirageShader<*>) {
  val content = gradientContent(64, 64)

  // GLES path: translate AGSL -> GLSL ES, bind schema defaults through the recording sink, override the
  // lens frame, render offscreen through GlEnv's FBO. Same setup the node's binder uses.
  val compiled = MirageCompiler.compile(optic, Dialect.GlslEs)
  val glProgram = GlProgram(MirageGlslEs.translate(compiled.source))
  val (glSink, glWrites) = glProgram.uniformSink()
  bindSchemaDefaults(glSink, compiled)
  frameLens(glSink)
  val glOut = runBlocking { glProgram.render(content, glWrites) }
  assertNotNull("GLES render returned null for ${compiled.category}", glOut)

  // AGSL path: the same optic compiled to AGSL, driven by an identical schema-default bind directly on
  // the RuntimeShader (no shared sink — set each uniform ourselves so the two paths stay symmetric).
  val agsl = MirageCompiler.compile(optic, Dialect.Agsl)
  val shader = RuntimeShader(agsl.source)
  bindAgslDefaults(shader, agsl)
  frameLensAgsl(shader)
  val agslOut = renderAgslToBitmap(shader, content)

  val mad = meanAbsDiff(glOut!!, agslOut)
  // Always report the MAD so a passing run still surfaces the number (asserts print only on failure).
  Log.i("GlProgramMatch", "MAD ${compiled.category}=$mad (TOL=$GL_AGSL_MATCH_TOL)")
  assertTrue(
    "GLES vs AGSL diverged for the lens optic: MAD=$mad (TOL=$GL_AGSL_MATCH_TOL).",
    mad < GL_AGSL_MATCH_TOL,
  )
}

/**
 * Renders an AGSL [shader] to a fresh ARGB_8888 bitmap on the GPU. A RuntimeShader is a HARDWARE-only
 * feature: a plain `Canvas(Bitmap)` is a software canvas and throws
 * `"Software rendering doesn't support RuntimeShader"`, so this drives a [RenderNode] through a
 * [HardwareRenderer] into an [ImageReader] surface and reads the frame back exactly like `GlEnv` does
 * (the readback path the duotone/chromatic GLES tests already exercise on a vendor GPU) —
 * `HardwareBuffer` -> `wrapHardwareBuffer` -> `copy(ARGB_8888)`.
 *
 * Binds [content] as the `content` child sampler (CLAMP, matching the GL texture wrap). The RenderNode
 * is positioned at the origin and the rect is drawn untransformed, so `main(float2 xy)` receives
 * top-left pixel coords matching the GLES path's Y-flipped fragCoord frame.
 */
private fun renderAgslToBitmap(shader: RuntimeShader, content: Bitmap): Bitmap {
  shader.setInputShader(
    "content",
    BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
  )
  val w = content.width
  val h = content.height

  val node = RenderNode("agsl-ref").apply {
    setPosition(0, 0, w, h)
    val canvas = beginRecording(w, h)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
    endRecording()
  }

  // Same ImageReader usage flags as GlEnv's proven readback target.
  val reader = ImageReader.newInstance(
    w,
    h,
    PixelFormat.RGBA_8888,
    2,
    HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
      HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
      HardwareBuffer.USAGE_CPU_READ_RARELY,
  )
  val renderer = HardwareRenderer().apply {
    setSurface(reader.surface)
    setContentRoot(node)
  }
  try {
    // setWaitForPresent(true) blocks until the single frame is on the surface; acquireNextImage then
    // returns that one produced frame (the recipe in the official RenderScript-migration guide).
    renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
    val image =
      reader.acquireNextImage() ?: error("HardwareRenderer produced no frame for the AGSL ref")
    return try {
      val hb = image.hardwareBuffer ?: error("AGSL ref image had no HardwareBuffer")
      val wrapped = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
        ?: error("wrapHardwareBuffer returned null for the AGSL ref")
      val copy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
      wrapped.recycle()
      hb.close()
      copy
    } finally {
      image.close()
    }
  } finally {
    renderer.destroy()
    reader.close()
    node.discardDisplayList()
  }
}

/** A params instance reset to [compiled]'s schema defaults — what colorGradeMatrixOf reads per draw. */
@OptIn(ExperimentalMirage::class)
private fun defaultParams(compiled: CompiledProgram): MirageParams {
  val params = MirageShaders.Duotone.paramsFactory()
  resetToDefaults(params, compiled.schema)
  return params
}

private fun gradientContent(w: Int, h: Int): Bitmap {
  val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  for (y in 0 until h) {
    for (x in 0 until w) {
      val r = (x * 255 / (w - 1))
      val g = (y * 255 / (h - 1))
      val b = 255 - r
      bmp.setPixel(x, y, Color.argb(255, r, g, b))
    }
  }
  return bmp
}

@OptIn(ExperimentalMirage::class)
private fun bindSchemaDefaults(sink: UniformSink, compiled: CompiledProgram) {
  if (compiled.usesResolution) sink.float2("mirageResolution", 64f, 64f)
  for (entry in compiled.schema.entries) {
    when (val d = entry.default) {
      is ComposeColor -> sink.color(entry.name, d)
      is Float -> sink.float(entry.name, d)
      is Offset -> sink.float2(entry.name, d.x, d.y)
      is Size -> sink.float2(entry.name, d.width, d.height)
      is FloatArray -> sink.floatArray(entry.name, d)
      is Int -> sink.int(entry.name, d)
      else -> {} // textures / null: unused by these optics
    }
  }
}

/**
 * Binds each schema slot's declared default directly onto the AGSL [shader] — the symmetric twin of
 * [bindSchemaDefaults], but set on the RuntimeShader ourselves so the GLES and AGSL paths never share a
 * sink (a shared sink's own conversions could mask a real translation divergence). A `layout(color)`
 * uniform uses `setColorUniform` (color-space aware, the native path); the lens optics declare none.
 */
@OptIn(ExperimentalMirage::class)
private fun bindAgslDefaults(shader: RuntimeShader, compiled: CompiledProgram) {
  for (entry in compiled.schema.entries) {
    val d = entry.default
    when {
      entry.isColor && d is ComposeColor ->
        shader.setColorUniform(entry.name, d.toArgb())

      d is Float -> shader.setFloatUniform(entry.name, d)

      d is Offset -> shader.setFloatUniform(entry.name, d.x, d.y)

      d is Size -> shader.setFloatUniform(
        entry.name,
        d.width,
        d.height,
      )

      d is FloatArray -> shader.setFloatUniform(entry.name, d)

      d is Int -> shader.setIntUniform(entry.name, d)

      else -> {} // textures / null: unused by these optics
    }
  }
}

/** Frames the lens over the whole 64x64 raster on the GLES sink so every pixel takes the lens branch. */
private fun frameLens(sink: UniformSink) {
  sink.float2("lensCenter", LENS_FRAME / 2f, LENS_FRAME / 2f)
  sink.float2("lensSize", LENS_FRAME, LENS_FRAME)
  sink.float("cornerRadius", 0f)
}

/** The AGSL twin of [frameLens] — the same override, set directly on the RuntimeShader. */
private fun frameLensAgsl(shader: RuntimeShader) {
  shader.setFloatUniform("lensCenter", LENS_FRAME / 2f, LENS_FRAME / 2f)
  shader.setFloatUniform("lensSize", LENS_FRAME, LENS_FRAME)
  shader.setFloatUniform("cornerRadius", 0f)
}

private fun applyMatrix(m: FloatArray, src: Bitmap): Bitmap {
  val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
  for (y in 0 until src.height) {
    for (x in 0 until src.width) {
      val p = src.getPixel(x, y)
      val r = Color.red(p).toFloat()
      val g = Color.green(p).toFloat()
      val b = Color.blue(p).toFloat()
      val a = Color.alpha(p).toFloat()
      fun ch(row: Int) =
        (m[row] * r + m[row + 1] * g + m[row + 2] * b + m[row + 3] * a + m[row + 4])
          .coerceIn(0f, 255f).toInt()
      out.setPixel(x, y, Color.argb(ch(15), ch(0), ch(5), ch(10)))
    }
  }
  return out
}

private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
  var sum = 0L
  var n = 0
  for (y in 0 until a.height) {
    for (x in 0 until a.width) {
      val pa = a.getPixel(x, y)
      val pb = b.getPixel(x, y)
      sum += abs(Color.red(pa) - Color.red(pb)).toLong()
      sum += abs(Color.green(pa) - Color.green(pb)).toLong()
      sum += abs(Color.blue(pa) - Color.blue(pb)).toLong()
      n += 3
    }
  }
  return sum.toDouble() / n
}
