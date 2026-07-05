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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.skydoves.cloudy.internal.CachedProgram
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.MirageProgramCache
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
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

private const val RASTER = 64

/**
 * Direct-skiko rasterization proof for the thin-film (chromatic) family. There is no compose-ui-test
 * harness in the desktop test set (only `compose.desktop.currentOs`), so instead of driving a real
 * `Modifier.mirage { ... }` this rasterizes each optic's compiled SKSL through skiko's [RuntimeEffect]
 * exactly the way the skiko backend does at draw time — the same source, the same schema-default
 * uniforms, with the lens sized to cover the whole raster so every pixel goes through the thin-film
 * branch (not the `content.eval(xy)` early-out).
 *
 * The point is Bug A end to end: OilSlick and Pearl share one compiled program yet must produce
 * *different* pixels, and each must differ from the un-filtered content. If the cache aliased their
 * schema defaults (the bug), OilSlick and Pearl would be pixel-identical here.
 */
internal class MirageChromaticRasterTest :
  FunSpec({

    test("OilSlick, Pearl, and baseline content all rasterize to distinct pixels") {
      val baseline = renderContent()
      val oil = renderChromatic(MirageOptics.OilSlick)
      val pearl = renderChromatic(MirageOptics.Pearl)

      // Each filtered look must actually alter the content (non-passthrough), and the two looks must
      // differ from each other — the direct evidence that per-optic schema defaults reach the shader.
      // Measured deltas (whole-raster lens coverage): oil-vs-baseline ~10.5, pearl-vs-baseline ~39.6,
      // oil-vs-pearl ~29.1 mean abs byte diff (0..255). Before the Bug A fix, oil-vs-pearl was 0.0.
      meanAbsDiff(oil, baseline).shouldBeGreaterThan(1.0)
      meanAbsDiff(pearl, baseline).shouldBeGreaterThan(1.0)
      meanAbsDiff(oil, pearl).shouldBeGreaterThan(1.0)
    }

    // X-seam regression: the bevel direction is a soft-min blend across the rounded-box diagonal
    // (`d.x == d.y`). With the historical 8px blend half-width the 90-degree direction turn was
    // compressed enough to read as a faint X-shaped crease in the thin-film color; widening
    // SEAM_BLEND_PX spreads the turn so the diagonal no longer stands out. MetallicFoil is the worst
    // case (a Fresnel rim boost sharpens the seam). The light is aimed straight up (off both
    // diagonals) so the focal pool, which by default sits on a diagonal, does not mask the seam.
    //
    // Metric: for each point on a main diagonal, walk perpendicular to it and take the local luma
    // slope; a crease makes the slope peak right at the diagonal. `diagonalSlopeExcess` returns the
    // mean slope in a 2px band centered on the diagonal minus the mean slope in the flanking band —
    // a seamless field has an excess near zero, a crease a large positive excess. Measured at this
    // config: the widened blend is about 0.6, the old 8px blend about 1.7.
    test("MetallicFoil has no X-shaped diagonal seam in the lens") {
      val raster = renderChromaticAt(
        MirageOptics.MetallicFoil,
        SEAM_RASTER,
        corner = SEAM_CORNER,
        light = Offset(0f, -1f),
      )
      val excess = diagonalSlopeExcess(raster, SEAM_RASTER)
      // Well under the old 8px-blend seam magnitude, with margin for skiko fp rounding.
      excess.shouldBeLessThan(1.2)
    }
  })

private const val SEAM_RASTER = 256
private const val SEAM_CORNER = 60f

/**
 * Renders [optic]'s compiled program over a fixed gradient content into an ARGB byte buffer. Resets
 * the params to the schema defaults (as the node does each draw), overrides only the lens framing to
 * cover the whole raster, then binds every uniform through the same [com.skydoves.cloudy.internal.
 * UniformSink] path the node uses.
 */
@OptIn(ExperimentalMirage::class)
private fun renderChromatic(optic: CompositeOptic<ChromaticParams>): ByteArray {
  val cached = MirageProgramCache.obtain(optic, Dialect.Sksl).shouldNotBeNull()
  val params = optic.paramsFactory()

  // Reset to this optic's declared defaults (mirrors the node's per-draw resetToDefaults), then frame
  // the lens over the whole raster so no pixel takes the sdf early-out.
  applySchemaDefaults(params, cached)
  params.lensCenter(Offset(RASTER / 2f, RASTER / 2f))
  params.lensSize(Size(RASTER.toFloat(), RASTER.toFloat()))
  params.cornerRadius(0f)

  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source))
  bindUniforms(builder, params, cached)
  builder.child("content", contentShader())

  return rasterize(builder.makeShader())
}

/**
 * Renders [optic] at an arbitrary [size] with a chosen [corner] radius (in raster px). Mirrors
 * [renderChromatic] but parameterizes the raster/corner so the seam test can use a large rounded lens
 * where the diagonal crease of the old blend was visible. The content shader is sampled at the same
 * scale, so pixels stay in [0, 1].
 */
@OptIn(ExperimentalMirage::class)
private fun renderChromaticAt(
  optic: CompositeOptic<ChromaticParams>,
  size: Int,
  corner: Float,
  light: Offset = Offset(-1f, -1f),
): ByteArray {
  val cached = MirageProgramCache.obtain(optic, Dialect.Sksl).shouldNotBeNull()
  val params = optic.paramsFactory()

  applySchemaDefaults(params, cached)
  params.lensCenter(Offset(size / 2f, size / 2f))
  params.lensSize(Size(size.toFloat(), size.toFloat()))
  params.cornerRadius(corner)
  params.iLight(light)

  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(cached.compiled.source))
  bindUniformsAt(builder, params, cached, size)
  builder.child("content", contentShaderAt(size))

  return rasterize(builder.makeShader(), size)
}

/** Baseline: the un-filtered content itself, so a filtered look can be diffed against it. */
private fun renderContent(): ByteArray = rasterize(contentShader())

/** A deterministic diagonal gradient content shader, opaque, so the alpha-branch is the opaque path. */
private fun contentShader(): Shader = contentShaderAt(RASTER)

/** [contentShader] scaled to an arbitrary raster [size] so the seam test can use a larger canvas. */
private fun contentShaderAt(size: Int): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float2 uv = xy / float2($size.0, $size.0);
    return half4(half(uv.x), half(uv.y), half(1.0 - uv.x), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }

/** Draws [shader] over an opaque raster and returns its ARGB_8888 bytes. */
private fun rasterize(shader: Shader, size: Int = RASTER): ByteArray {
  val info = ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
  val surface = Surface.makeRaster(info)
  val canvas: Canvas = surface.canvas
  canvas.drawPaint(Paint().apply { this.shader = shader })

  val bitmap = Bitmap().apply { allocPixels(info) }
  surface.readPixels(bitmap, 0, 0)
  return bitmap.readPixels() ?: error("readPixels returned null")
}

/**
 * Quantifies an X-shaped diagonal crease by the *localization* of the perpendicular luma slope at the
 * diagonal. For each point along a main diagonal it walks perpendicular over `[-W, W]` px and takes
 * the per-step absolute luma slope; a crease makes that slope peak right at the diagonal. The returned
 * value is the mean slope in the 2px band centered on the diagonal minus the mean slope in the
 * flanking band (`[4, W]` on both sides). A seamless field, whose luma varies smoothly across the
 * diagonal, has an excess near zero; a crease has a large positive excess. Both main diagonals are
 * sampled, over the inner half so the rim and the 1px medial point do not dominate.
 */
private fun diagonalSlopeExcess(argb: ByteArray, size: Int): Double {
  fun luma(x: Double, y: Double): Double {
    val xi = x.toInt().coerceIn(0, size - 1)
    val yi = y.toInt().coerceIn(0, size - 1)
    val i = (yi * size + xi) * 4
    val rr = argb[i].toInt() and 0xFF
    val gg = argb[i + 1].toInt() and 0xFF
    val bb = argb[i + 2].toInt() and 0xFF
    return 0.2126 * rr + 0.7152 * gg + 0.0722 * bb
  }
  val lo = size / 4
  val hi = size - size / 4
  val w = 20
  val inv = 1.0 / kotlin.math.sqrt(2.0)
  var centerSum = 0.0
  var centerCount = 0
  var flankSum = 0.0
  var flankCount = 0
  for (k in lo until hi) {
    // Perpendicular profile across the main diagonal at (k, k), stepping along (+1, -1).
    val profile = DoubleArray(2 * w + 1) { idx ->
      val s = idx - w
      luma(k + inv * s, k - inv * s)
    }
    for (idx in 0 until 2 * w) {
      val s = idx - w
      val slope = abs(profile[idx + 1] - profile[idx])
      when {
        s in -2..1 -> {
          centerSum += slope
          centerCount++
        }
        s <= -4 || s >= 4 -> {
          flankSum += slope
          flankCount++
        }
      }
    }
  }
  return centerSum / centerCount - flankSum / flankCount
}

/** Mean absolute per-byte difference between two equally sized ARGB buffers (0..255 scale). */
private fun meanAbsDiff(a: ByteArray, b: ByteArray): Double {
  require(a.size == b.size) { "buffers differ in size: ${a.size} vs ${b.size}" }
  var sum = 0L
  for (i in a.indices) sum += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
  return sum.toDouble() / a.size
}

/**
 * Resets each handle to its schema-entry default, mirroring the node's per-draw reset so a raster
 * starts from this optic's declared look. Only the value uniforms the chromatic family uses are
 * handled; that is the whole schema for these optics.
 */
@OptIn(ExperimentalMirage::class)
private fun applySchemaDefaults(params: MirageParams, cached: CachedProgram) {
  val entries = cached.compiled.schema.entries
  for (handle in params.handles) {
    val default = entries[handle.slot].default
    when (handle) {
      is UFloat -> handle.value = default as Float
      is UOffset -> handle.value = default as Offset
      is USize -> handle.value = default as Size
      is UVec4 -> handle.value = (default as FloatArray).copyOf()
      else -> error("unexpected handle type in chromatic schema: $handle")
    }
  }
}

/** Pushes the standard uniforms and every schema slot through the skiko builder, as the node does. */
@OptIn(ExperimentalMirage::class)
private fun bindUniforms(
  builder: RuntimeShaderBuilder,
  params: MirageParams,
  cached: CachedProgram,
) = bindUniformsAt(builder, params, cached, RASTER)

/** [bindUniforms] with an explicit raster [size] for the resolution uniform. */
@OptIn(ExperimentalMirage::class)
private fun bindUniformsAt(
  builder: RuntimeShaderBuilder,
  params: MirageParams,
  cached: CachedProgram,
  size: Int,
) {
  val compiled = cached.compiled
  if (compiled.usesResolution) {
    builder.uniform(
      "mirageResolution",
      size.toFloat(),
      size.toFloat(),
    )
  }
  if (compiled.usesTime) builder.uniform("mirageTime", 0f)
  if (compiled.usesDensity) builder.uniform("mirageDensity", 1f)

  val entries = compiled.schema.entries
  for (handle in params.handles) {
    val name = entries[handle.slot].name
    when (handle) {
      is UFloat -> builder.uniform(name, handle.value)
      is UOffset -> builder.uniform(name, handle.value.x, handle.value.y)
      is USize -> builder.uniform(name, handle.value.width, handle.value.height)
      is UVec4 -> builder.uniform(name, handle.value)
      else -> error("unexpected handle type in chromatic schema: $handle")
    }
  }
}
