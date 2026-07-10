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
import kotlin.math.hypot
import com.skydoves.cloudy.internal.bindUniforms as bindLibraryUniforms

private const val RASTER = 64

/**
 * Direct-skiko rasterization proof for the thin-film (chromatic) family. There is no compose-ui-test
 * harness in the desktop test set (only `compose.desktop.currentOs`), so instead of driving a real
 * `Modifier.mirage { ... }` this rasterizes each optic's compiled SKSL through skiko's [RuntimeEffect]
 * exactly the way the skiko backend does at draw time — the same source, the same schema-default
 * uniforms, with the lens sized to cover the whole raster so every pixel goes through the thin-film
 * branch (not the `content.eval(xy)` early-out).
 *
 * The contract end to end: OilSlick and Pearl share one compiled program yet must produce
 * *different* pixels, and each must differ from the un-filtered content. If the cache aliased their
 * schema defaults to whichever compiled first, OilSlick and Pearl would be pixel-identical here.
 */
internal class MirageChromaticRasterTest :
  FunSpec({

    test("OilSlick, Pearl, and baseline content all rasterize to distinct pixels") {
      val baseline = renderContent()
      val oil = renderChromatic(MirageOptics.OilSlick)
      val pearl = renderChromatic(MirageOptics.Pearl)

      // Each filtered look must actually alter the content (non-passthrough), and the two looks must
      // differ from each other — the direct evidence that per-optic schema defaults reach the shader.
      // If OilSlick and Pearl shared one schema, oil-vs-pearl would be 0.
      meanAbsDiff(oil, baseline).shouldBeGreaterThan(1.0)
      meanAbsDiff(pearl, baseline).shouldBeGreaterThan(1.0)
      meanAbsDiff(oil, pearl).shouldBeGreaterThan(1.0)
    }

    // Regression for the origin-pinned lens default. MirageLensParams used to default lensCenter to
    // Offset.Zero with a fixed 350x350 lensSize, so a bare `filter(Chromatic)` on any node larger than
    // the lens graded only the origin quadrant and passed the rest through — on a backdrop card the
    // rainbow showed only in the corner, reading as "chromatic draws behind the content". These renders
    // go through the REAL library binder (bindUniforms), the exact path both mirage nodes take.
    test("unset lens framing auto-binds as the full node frame, not an origin-pinned lens") {
      val size = 512
      val auto = renderThroughLibraryBinder(MirageOptics.Chromatic, size)
      val explicitFullBleed = renderThroughLibraryBinder(MirageOptics.Chromatic, size) {
        lensCenter(Offset(size / 2f, size / 2f))
        lensSize(Size(size.toFloat(), size.toFloat()))
      }
      val originPinned = renderThroughLibraryBinder(MirageOptics.Chromatic, size) {
        lensCenter(Offset.Zero)
        lensSize(Size(350f, 350f))
      }

      // The substitution contract: an unset frame IS the explicit node frame, bit-for-bit.
      meanAbsDiff(auto, explicitFullBleed).shouldBe(0.0)
      // And it is no longer the old fixed default that graded only the origin quadrant.
      meanAbsDiff(auto, originPinned).shouldBeGreaterThan(1.0)
    }

    // chromaticPoolFrac regression: OilSlick-vs-Pearl distinctness above doesn't exercise it (the
    // chromatic effect differs regardless of pool radius), so this pins that the uniform actually
    // reaches the shader through the real binder. Chromatic's default modulate is 1, so the pool
    // fraction shapes the rainbow — a widened pool must change the render.
    test("chromaticPoolFrac changes the render when the pool modulates the effect") {
      val size = 512
      val defaultPool = renderThroughLibraryBinder(MirageOptics.Chromatic, size)
      val widePool = renderThroughLibraryBinder(MirageOptics.Chromatic, size) {
        chromaticPoolFrac(1.5f)
      }

      meanAbsDiff(defaultPool, widePool).shouldBeGreaterThan(1.0)
    }

    // Full-bleed / max-intensity X guard. At cornerRadius 0 + whole-pane lens + chromaticIntensity
    // 1.0, the dominant diagonal structure is the box depth field: `depthIn = -sdf` of a rounded box
    // is the distance to the nearest edge, whose ridge lines lie exactly on the diagonals, so the
    // thin-film rings stack into a bright corner-to-corner X (MetallicFoil's Fresnel rim boost sharpens
    // it most). This X is a *wide smooth ridge*, not a 1px crease. `diagonalRidgeExcess` bins the local
    // luma-gradient *magnitude* by angular position into a diagonal sector (±15 deg around each 45 deg
    // line) vs an axis sector, over the interior annulus. A box-SDF depth field dumps gradient energy
    // into the diagonal sectors (X); the shipped superellipse field spreads it evenly. Square configs
    // only — the sector binning is aspect-ratio sensitive, so the non-square case is covered visually.
    //
    // The broad-ridge metric is resolution- and content-sensitive (the smooth rounded-rect ring
    // gradients of the superellipse field also carry energy), so an absolute threshold is fragile.
    // The self-normalizing guard re-synthesizes a box-SDF bevel from the compiled source
    // (`toOldBoxBevel`) and compares it against the shipped superellipse field at the same framing:
    // the superellipse must dump measurably less gradient energy onto the diagonals. Because the
    // comparison catches a box-like field, it cannot pass vacuously. The pool is muted via
    // chromaticModulate 0 so only the bevel field shows.
    test(
      "MetallicFoil superellipse bevel has far less diagonal-X energy than the old box bevel (full-bleed/max)",
    ) {
      val fixed =
        renderRidgeProbe(MirageOptics.MetallicFoil, BLEED_RASTER, corner = 0f, intensity = 1f)
      val box = renderRidgeProbe(
        MirageOptics.MetallicFoil,
        BLEED_RASTER,
        corner = 0f,
        intensity = 1f,
        oldBoxBevel = true,
      )
      val fixedExcess = diagonalRidgeExcess(fixed, BLEED_RASTER)
      val boxExcess = diagonalRidgeExcess(box, BLEED_RASTER)
      // A box-SDF field concentrates markedly more gradient on the diagonals (the X). If the
      // superellipse field were box-like this ratio would collapse toward 1.0 and the test would fail.
      (boxExcess / fixedExcess).shouldBeGreaterThan(RIDGE_MIN_RATIO)
      fixedExcess.shouldBeLessThan(boxExcess)
    }

    // Same guard at a typical rounded framing (~0.23 corner fraction, intensity 0.6). The metric needs
    // enough resolution: below ~384px the superellipse field's crisp rounded-rect corner-ring gradients
    // land in the diagonal sector and the comparison inverts (a sampling artifact, not a real X), so
    // this runs at DFRAME_RASTER, well above that floor.
    test(
      "MetallicFoil superellipse bevel beats the box bevel at the default framing too (no regression)",
    ) {
      val corner = DFRAME_RASTER * 0.23f
      val fixed =
        renderRidgeProbe(
          MirageOptics.MetallicFoil,
          DFRAME_RASTER,
          corner = corner,
          intensity = 0.6f,
        )
      val box = renderRidgeProbe(
        MirageOptics.MetallicFoil,
        DFRAME_RASTER,
        corner = corner,
        intensity = 0.6f,
        oldBoxBevel = true,
      )
      diagonalRidgeExcess(
        fixed,
        DFRAME_RASTER,
      ).shouldBeLessThan(diagonalRidgeExcess(box, DFRAME_RASTER))
    }
  })

private const val BLEED_RASTER = 900
private const val DFRAME_RASTER = 512
private const val RIDGE_MIN_RATIO = 1.30

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
 * Renders [optic] over the gradient content through the REAL library binder — reset-to-defaults, the
 * per-draw [frame] block, then the auto lens-frame substitution — exactly as [com.skydoves.cloudy.
 * internal.MirageWeather] binds each draw. [frame] left `null` exercises the bare preset (the auto
 * framing path).
 */
@OptIn(ExperimentalMirage::class)
private fun renderThroughLibraryBinder(
  optic: CompositeOptic<ChromaticParams>,
  size: Int,
  frame: (ChromaticParams.() -> Unit)? = null,
): ByteArray {
  val cached = MirageProgramCache.obtain(optic, Dialect.Sksl).shouldNotBeNull()
  val params = optic.paramsFactory()
  bindLibraryUniforms(
    cached = cached,
    params = params,
    paramsBlock = frame?.let { block -> { (this as ChromaticParams).block() } },
    width = size.toFloat(),
    height = size.toFloat(),
    density = 1f,
    time = 0f,
  )
  val builder = cached.backend.builder
  builder.child("content", contentShaderAt(size))
  return rasterize(builder.makeShader(), size)
}

/**
 * Renders [optic] at a square [size] with the bevel-field artifact isolated for the broad-ridge metric:
 * the lens covers the whole raster (center + size), corner is [corner], `chromaticIntensity` is forced
 * to [intensity], and `chromaticModulate` is forced to 0 so the light focal pool does not overlay the
 * ring geometry (the pool is a separate, pre-existing feature — the X is purely the bevel field). Light
 * is aimed straight up (off both diagonals) so any diagonal energy is the field, not the light.
 *
 * When [oldBoxBevel] is true the compiled superellipse bevel block is rewritten to a box-SDF
 * construction (`depthIn = max(-sdf, 0)` depth + the soft-min direction), the contrasting field the
 * guard compares against.
 */
@OptIn(ExperimentalMirage::class)
private fun renderRidgeProbe(
  optic: CompositeOptic<ChromaticParams>,
  size: Int,
  corner: Float,
  intensity: Float,
  oldBoxBevel: Boolean = false,
): ByteArray {
  val cached = MirageProgramCache.obtain(optic, Dialect.Sksl).shouldNotBeNull()
  val params = optic.paramsFactory()

  applySchemaDefaults(params, cached)
  params.lensCenter(Offset(size / 2f, size / 2f))
  params.lensSize(Size(size.toFloat(), size.toFloat()))
  params.cornerRadius(corner)
  params.iLight(Offset(0f, -1f))
  for (handle in params.handles) {
    val name = cached.compiled.schema.entries[handle.slot].name
    if (handle is UFloat && name == "chromaticIntensity") handle.value = intensity
    if (handle is UFloat && name == "chromaticModulate") handle.value = 0f
  }

  val source = if (oldBoxBevel) toOldBoxBevel(cached.compiled.source) else cached.compiled.source
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(source))
  bindUniformsAt(builder, params, cached, size)
  builder.child("content", contentShaderAt(size))
  return rasterize(builder.makeShader(), size)
}

/**
 * Rewrites the chromatic superellipse bevel block in [source] to a box-SDF construction
 * (`depthIn = max(-sdf, 0)` depth normalized by minHalf + a soft-min interior direction). Rendering
 * this contrasting field lets the guard assert the box construction dumps more diagonal energy, so the
 * comparison cannot pass vacuously. Anchored on the bevel block's own source markers.
 */
private fun toOldBoxBevel(source: String): String {
  val startAnchor = "    float2 q  = abs(p) / max(halfDim, float2(1.0));"
  val endAnchor = "    float n_cos   = 1.0 - t;"
  val start = source.indexOf(startAnchor)
  val end = source.indexOf(endAnchor)
  require(start >= 0 && end > start) {
    "superellipse block markers not found — update toOldBoxBevel"
  }
  val head = source.substring(0, start)
  val tail = source.substring(end + endAnchor.length)
  val boxBlock = """
    float2 d2 = abs(p) - halfDim + float2(r);
    float2 s2 = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 cDir;
    if (max(d2.x, d2.y) > 0.0) {
        cDir = s2 * normalize(max(d2, 0.0));
    } else {
        float w = clamp(0.5 + 0.5 * (d2.x - d2.y) / SEAM_BLEND_PX, 0.0, 1.0);
        cDir = normalize(float2(s2.x * w, s2.y * (1.0 - w)) + float2(0.0, 1.0e-4));
    }
    float depthIn = max(-sdf, 0.0);
    float t       = clamp(depthIn / max(minHalf, 1.0), 0.0, 1.0);
    float n_cos   = 1.0 - t;
  """.trimIndent()
  return head + boxBlock + tail
}

/**
 * Broad diagonal-ridge metric. The box-bevel X is a *wide smooth ridge* whose luma gradient points
 * across the diagonal over a large area, not a 1px crease. This takes the central-difference luma
 * gradient at each interior pixel and bins its *magnitude*
 * by the pixel's angular position relative to center: a diagonal sector (within 15 deg of a 45 deg
 * diagonal) vs an axis sector (within 15 deg of H/V). The excess = mean |grad| in the diagonal sector
 * minus the axis sector, over the annulus `[0.15R, 0.85R]` (skips the bright center and the rim mask).
 * The box depth field concentrates gradient on the diagonals -> large positive excess; a smooth
 * concentric field spreads it evenly -> small excess. Square rasters only (the binning is aspect-aware).
 */
private fun diagonalRidgeExcess(argb: ByteArray, size: Int): Double {
  fun luma(x: Int, y: Int): Double {
    val xi = x.coerceIn(0, size - 1)
    val yi = y.coerceIn(0, size - 1)
    val i = (yi * size + xi) * 4
    val rr = argb[i].toInt() and 0xFF
    val gg = argb[i + 1].toInt() and 0xFF
    val bb = argb[i + 2].toInt() and 0xFF
    return 0.2126 * rr + 0.7152 * gg + 0.0722 * bb
  }
  val c = size / 2.0
  var diagSum = 0.0
  var diagCnt = 0
  var axisSum = 0.0
  var axisCnt = 0
  for (y in 2 until size - 2) {
    for (x in 2 until size - 2) {
      val nx = (x - c) / c
      val ny = (y - c) / c
      val rr = nx * nx + ny * ny
      if (rr < 0.15 * 0.15 || rr > 0.85 * 0.85) continue
      val gx = luma(x + 1, y) - luma(x - 1, y)
      val gy = luma(x, y + 1) - luma(x, y - 1)
      val mag = hypot(gx, gy)
      // angle of the pixel about center, folded to [0 deg = on a diagonal, 45 deg = on an axis].
      val fold = abs(Math.toDegrees(kotlin.math.atan2(abs(ny), abs(nx))) - 45.0)
      when {
        fold <= 15.0 -> {
          diagSum += mag
          diagCnt++
        }

        fold >= 30.0 -> {
          axisSum += mag
          axisCnt++
        }
      }
    }
  }
  val diag = if (diagCnt > 0) diagSum / diagCnt else 0.0
  val axis = if (axisCnt > 0) axisSum / axisCnt else 0.0
  return diag - axis
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
