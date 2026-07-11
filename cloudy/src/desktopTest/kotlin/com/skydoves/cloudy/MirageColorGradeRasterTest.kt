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

import androidx.compose.ui.graphics.Color
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import com.skydoves.cloudy.internal.colorGradeMatrixOf
import com.skydoves.cloudy.internal.isColorGradeReproducible
import com.skydoves.cloudy.internal.resetToDefaults
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RASTER = 64

/**
 * Proves the API 23-28 ColorGrade path reproduces the Duotone Colorize optic *exactly*.
 *
 * Below API 33 there is no `RuntimeShader`, so the Android backend reproduces the affine Duotone
 * kernel with a `ColorMatrixColorFilter`. This test derives that same matrix on desktop
 * ([colorGradeMatrixOf] over the compiled Duotone program) and applies it numerically to each content
 * pixel, then compares against the *actual Duotone SKSL kernel* rasterized over the same content. If
 * the affine expansion drifted from the kernel, the per-pixel diff would blow past the rounding floor.
 *
 * The kernel runs through skiko exactly as the skiko backend does at draw time (same source, same
 * schema-default uniforms) — the only difference from the Android grade is sRGB rounding to 8-bit,
 * which is why the tolerance is 1 unit, not 0.
 */
internal class MirageColorGradeRasterTest :
  FunSpec({

    // Build a Duotone params reset to defaults; the .apply receiver is the public DuotoneParams (its
    // type is never named to avoid the same-package private DuotoneParams in the compiler test).
    fun duotoneParams() = MirageOptics.Duotone.paramsFactory()
      .apply { resetToDefaults(this, duotoneCompiled().schema) }

    test(
      "the Duotone color matrix (schema defaults) reproduces the Duotone kernel pixel-for-pixel",
    ) {
      val params = duotoneParams()
      val kernelPixels = renderDuotoneKernel(params)
      val gradePixels = applyMatrix(colorGradeMatrixOf(duotoneCompiled(), params), contentPixels())

      // Max per-channel abs diff over the whole raster. Both paths operate on the same sRGB-encoded
      // 0..1 values; the only slack is the final round-to-byte, so <= 1 unit is exact reproduction.
      maxAbsDiff(kernelPixels, gradePixels).shouldBeLessThan(2)
    }

    test("a per-draw params override is honored (matrix tracks the current values, not defaults)") {
      // The fix for the ColorGrade-ignores-params bug: filter(Duotone){ shadow=Red; amount=0.5 } must
      // change the grade. Render the kernel and the matrix with the SAME override and compare.
      val params = duotoneParams().apply {
        shadow(Color.Red)
        highlight(Color.Green)
        amount(0.5f)
      }
      val kernelPixels = renderDuotoneKernel(params)
      val matrix = colorGradeMatrixOf(duotoneCompiled(), params)
      maxAbsDiff(kernelPixels, applyMatrix(matrix, contentPixels())).shouldBeLessThan(2)

      // And it must differ from the default grade — proving the override actually took effect.
      val defaultGrade =
        applyMatrix(colorGradeMatrixOf(duotoneCompiled(), duotoneParams()), contentPixels())
      meanAbsDiff(applyMatrix(matrix, contentPixels()), defaultGrade).shouldBeGreaterThan(1.0)
    }

    test("a non-Colorize optic is not reproducible (stays a no-op below API 33)") {
      // A Composite lens optic is not affine, so the ColorGrade path must decline it, which is what
      // makes it a no-op / fallback below API 33.
      isColorGradeReproducible(
        MirageProgramCache.obtain(MirageOptics.Chromatic, Dialect.Sksl).shouldNotBeNull().compiled,
      ) shouldBe false
      isColorGradeReproducible(duotoneCompiled()) shouldBe true
    }

    test("the matrix actually changes the content (not an accidental identity)") {
      val graded =
        applyMatrix(colorGradeMatrixOf(duotoneCompiled(), duotoneParams()), contentPixels())
      meanAbsDiff(graded, contentPixels()).shouldBeGreaterThan(1.0)
    }
  })

/** The compiled Duotone program (skiko dialect) — the source the ColorGrade matrix is derived from. */
@OptIn(ExperimentalMirage::class)
private fun duotoneCompiled() =
  MirageProgramCache.obtain(MirageOptics.Duotone, Dialect.Sksl)!!.compiled

/** Deterministic diagonal-gradient content, opaque — the same shape the chromatic raster test uses. */
private fun contentShader(): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float2 uv = xy / float2($RASTER.0, $RASTER.0);
    return half4(half(uv.x), half(uv.y), half(1.0 - uv.x), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }

/** The raw content bytes (RGBA_8888), the input both the kernel and the matrix transform. */
private fun contentPixels(): ByteArray = rasterize(contentShader())

/**
 * Rasterizes the compiled Duotone SKSL over the content binding [params]'s current handle values
 * exactly as the node does — the reference the per-draw ColorGrade matrix must match.
 */
@OptIn(ExperimentalMirage::class)
private fun renderDuotoneKernel(params: MirageParams): ByteArray {
  val compiled = duotoneCompiled()
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(compiled.source))
  val entries = compiled.schema.entries
  for (handle in params.handles) {
    val name = entries[handle.slot].name
    when (handle) {
      is UColor -> {
        val c = handle.value
        builder.uniform(name, c.red, c.green, c.blue, c.alpha)
      }

      is UFloat -> builder.uniform(name, handle.value)

      else -> error("unexpected Duotone handle: $handle")
    }
  }
  builder.child("content", contentShader())
  return rasterize(builder.makeShader())
}

/**
 * Applies a 4x5 android-layout color matrix to RGBA_8888 [src] bytes, mirroring
 * `ColorMatrixColorFilter`: rows R,G,B,A; cols R,G,B,A,offset (offset in 0..255). Channels are read as
 * 0..255, transformed, clamped, rounded — the same arithmetic the framework filter performs.
 */
private fun applyMatrix(m: FloatArray, src: ByteArray): ByteArray {
  val out = ByteArray(src.size)
  var i = 0
  while (i < src.size) {
    val r = (src[i].toInt() and 0xFF).toFloat()
    val g = (src[i + 1].toInt() and 0xFF).toFloat()
    val b = (src[i + 2].toInt() and 0xFF).toFloat()
    val a = (src[i + 3].toInt() and 0xFF).toFloat()
    for (c in 0 until 4) {
      val row = c * 5
      val v = m[row] * r + m[row + 1] * g + m[row + 2] * b + m[row + 3] * a + m[row + 4]
      out[i + c] = v.roundToInt().coerceIn(0, 255).toByte()
    }
    i += 4
  }
  return out
}

private fun rasterize(shader: Shader): ByteArray {
  val info = ImageInfo(RASTER, RASTER, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
  val surface = Surface.makeRaster(info)
  val canvas: Canvas = surface.canvas
  canvas.drawPaint(Paint().apply { this.shader = shader })
  val bitmap = Bitmap().apply { allocPixels(info) }
  surface.readPixels(bitmap, 0, 0)
  return bitmap.readPixels() ?: error("readPixels returned null")
}

private fun maxAbsDiff(a: ByteArray, b: ByteArray): Int {
  require(a.size == b.size)
  var max = 0
  for (i in a.indices) {
    val d = abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
    if (d > max) max = d
  }
  return max
}

private fun meanAbsDiff(a: ByteArray, b: ByteArray): Double {
  require(a.size == b.size)
  var sum = 0L
  for (i in a.indices) sum += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
  return sum.toDouble() / a.size
}
