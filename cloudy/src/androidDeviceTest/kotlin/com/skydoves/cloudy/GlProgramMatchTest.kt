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
import android.graphics.Color
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.GlProgram
import com.skydoves.cloudy.internal.MirageCompiler
import com.skydoves.cloudy.internal.MirageGlslEs
import com.skydoves.cloudy.internal.UniformSink
import com.skydoves.cloudy.internal.colorGradeMatrixOf
import kotlin.math.abs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * On-device validation of the GLES M3 pipeline (translator + [GlProgram] + GlEnv roundtrip) on the API
 * 29-32 band. Runs real optics through the GL program and checks the output:
 *
 * - **Duotone (Colorize)** must match the affine `ColorMatrix` reference (the M2 path, proven exact on
 *   desktop) within a modest tolerance — this catches translation, Y-flip, and sampler bugs, since a
 *   flipped or mis-sampled content would diverge hugely from the per-pixel matrix result.
 * - **Chromatic (Composite, lens preamble)** must actually alter the content (non-passthrough) — the
 *   lens kernel compiled and ran through GL.
 */
@RunWith(AndroidJUnit4::class)
public class GlProgramMatchTest {

  @Test
  public fun duotoneGlMatchesTheColorMatrixReference() {
    val content = gradientContent(64, 64)

    val compiled = MirageCompiler.compile(MirageOptics.Duotone, Dialect.GlslEs)
    val program = GlProgram(MirageGlslEs.translate(compiled.source))

    // Bind the optic's schema defaults through the recording sink, exactly as the node's binder does.
    val (sink, writes) = program.uniformSink()
    bindSchemaDefaults(sink, compiled)
    val glOut = program.render(content, writes)
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
    val compiled = MirageCompiler.compile(MirageOptics.Chromatic, Dialect.GlslEs)
    val program = GlProgram(MirageGlslEs.translate(compiled.source))

    val (sink, writes) = program.uniformSink()
    bindSchemaDefaults(sink, compiled)
    // Frame the lens over the whole raster so pixels take the thin-film branch, not the sdf early-out.
    sink.float2("lensCenter", 32f, 32f)
    sink.float2("lensSize", 64f, 64f)
    sink.float("cornerRadius", 0f)

    val glOut = program.render(content, writes)
    assertNotNull("GL render returned null (Chromatic lens kernel failed to compile/run?)", glOut)
    assertTrue(
      "Chromatic GL output is identical to content (kernel did nothing)",
      meanAbsDiff(glOut!!, content) > 1.0,
    )
  }
}

/** A params instance reset to [compiled]'s schema defaults — what colorGradeMatrixOf reads per draw. */
@OptIn(ExperimentalMirage::class)
private fun defaultParams(compiled: com.skydoves.cloudy.internal.CompiledProgram): MirageParams {
  val params = MirageOptics.Duotone.paramsFactory()
  com.skydoves.cloudy.internal.resetToDefaults(params, compiled.schema)
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
private fun bindSchemaDefaults(sink: UniformSink, compiled: com.skydoves.cloudy.internal.CompiledProgram) {
  if (compiled.usesResolution) sink.float2("mirageResolution", 64f, 64f)
  for (entry in compiled.schema.entries) {
    when (val d = entry.default) {
      is androidx.compose.ui.graphics.Color -> sink.color(entry.name, d)
      is Float -> sink.float(entry.name, d)
      is androidx.compose.ui.geometry.Offset -> sink.float2(entry.name, d.x, d.y)
      is androidx.compose.ui.geometry.Size -> sink.float2(entry.name, d.width, d.height)
      is FloatArray -> sink.floatArray(entry.name, d)
      is Int -> sink.int(entry.name, d)
      else -> {} // textures / null: unused by these optics
    }
  }
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
