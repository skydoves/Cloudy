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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldNotBe
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
import java.io.File
import kotlin.math.sqrt

private const val RASTER = 96

/**
 * Direct-skiko rasterization proof for the demo `RainyWindowShader` kernel. The shader lives in the
 * `:app` module (a consumer of the open mirage API), so this test does not depend on it: it reads the
 * kernel source string out of the app file and compiles it through skiko's [RuntimeEffect] the same way
 * the skiko backend does at draw time. This makes the SKSL kernel the single source of truth while
 * still gating it on the `:cloudy:desktopTest` task the CI gate runs.
 *
 * The invariant this protects is that droplet animation stays exact at large `mirageTime`. fp32
 * `sin()` range reduction decays on some GPUs (notably Adreno) once its argument grows large, so a
 * kernel that hashed a time-drifting coordinate through `sin(dot(...))` would collapse the droplet
 * field into near-random output at long run times. The current kernel is texture-backed and hash-free
 * — the only time math is `fract(mirageTime * speed + phase)` with a tiny product, keeping every
 * argument small so `sin`/`fract` stay exact on all GPUs — and the large-time invariants are asserted
 * so any drift back to unbounded time arguments is caught.
 *
 * A raster CPU skiko does not reproduce a specific GPU's `sin()` decay, so a pass here is not a proof
 * that a broken kernel is broken. Instead this asserts the kernel's invariants hold at both a small
 * and a large `mirageTime`: the output is non-black, has real spatial variation (not a collapsed
 * uniform field), contains no NaN, and the two times differ (the drops actually animate). A kernel
 * with unbounded `sin` inputs would fail these on any backend whose `sin()` decays; on a backend whose
 * `sin()` stays exact it would still animate, so the value of this test is the invariant documentation
 * plus the compile/animation guard on the exact source the app ships.
 */
internal class RainyWindowRasterTest :
  FunSpec({

    val kernel = readRainyWindowKernel()

    test(
      "rainy-window kernel compiles and rasterizes sane pixels at a small and a large mirageTime",
    ) {
      val early = rasterizeRain(kernel, time = 0f)
      val late = rasterizeRain(kernel, time = 3599f)

      // Non-black: the blurred background dominates most pixels, so the mean luma must be well above 0.
      meanLuma(early).shouldBeGreaterThan(20.0)
      meanLuma(late).shouldBeGreaterThan(20.0)

      // Real spatial variation at both times: a collapsed/degenerate hash would flood the frame with
      // one refracted value or transparent black, killing the stddev. The content is a diagonal
      // gradient, so even the pure-blur baseline has a healthy spread; assert we keep it.
      lumaStdDev(early).shouldBeGreaterThan(15.0)
      lumaStdDev(late).shouldBeGreaterThan(15.0)

      // No NaN-ish collapse: every pixel is a finite byte in a PREMUL buffer (readPixels already yields
      // bytes, but guard the alpha channel is fully opaque so no transparent-black bled in).
      minAlpha(early) shouldNotBe 0
      minAlpha(late) shouldNotBe 0

      // The drops drift with mirageTime, so the two frames must differ. A field that collapsed at large
      // time would look frozen/degenerate and this delta would vanish or spike into pure noise; a
      // healthy animated field gives a modest, bounded delta.
      val delta = meanAbsDiff(early, late)
      delta.shouldBeGreaterThan(0.2)
      delta.shouldBeLessThan(80.0)
    }
  })

/**
 * Reads the `RAINY_WINDOW_KERNEL` triple-quoted string out of the app source file and returns it. The
 * app module is not on this test's classpath, so the kernel is located on disk relative to the repo
 * root (found by walking up from `user.dir`, which Gradle sets to the module dir for JVM tests).
 */
private fun readRainyWindowKernel(): String {
  var dir: File? = File(System.getProperty("user.dir")).absoluteFile
  while (dir != null && !File(dir, "settings.gradle.kts").exists()) {
    dir = dir.parentFile
  }
  requireNotNull(dir) { "could not locate the repo root (settings.gradle.kts) from user.dir" }
  val source = File(dir, "app/src/commonMain/kotlin/demo/shader/RainyWindowShader.kt")
  require(source.exists()) { "RainyWindowShader.kt not found at ${source.absolutePath}" }

  val text = source.readText()
  val marker = "RAINY_WINDOW_KERNEL: String = \"\"\""
  val start = text.indexOf(marker)
  require(start >= 0) { "kernel marker not found in ${source.name}" }
  val bodyStart = start + marker.length
  val end = text.indexOf("\"\"\"", bodyStart)
  require(end > bodyStart) { "kernel closing triple-quote not found in ${source.name}" }
  return text.substring(bodyStart, end)
}

/**
 * The uniform header the mirage compiler prepends before splicing a composite kernel (see
 * `MirageCompiler`): the standard `mirageResolution`/`mirageTime` uniforms, the schema uniforms this
 * shader declares (`dropletMap`, `rainAmount`, `blurRadius`, `dropScale`), and the `content` child
 * shader. The raw kernel body references these but does not declare them, so the test must prepend the
 * same header to compile. `dropletMap` is a `uniform shader` child the texture-backed kernel taps.
 */
private const val KERNEL_HEADER: String = """
uniform float2 mirageResolution;
uniform float mirageTime;
uniform shader dropletMap;
uniform float rainAmount;
uniform float blurRadius;
uniform float dropScale;
uniform shader content;
"""

/**
 * Rasterizes the rainy-window kernel over an opaque diagonal-gradient content at a chosen [time]. Binds
 * the standard mirage uniforms the kernel reads (`mirageResolution`, `mirageTime`) plus the schema
 * uniforms (`rainAmount`, `blurRadius`, `dropScale`) at their demo defaults, and a synthetic droplet
 * map as the `dropletMap` child (a procedural paraboloid field standing in for the baked texture, so
 * the test does not depend on the app's `DropletMap` generator). Returns RGBA_8888 bytes.
 */
private fun rasterizeRain(kernel: String, time: Float): ByteArray {
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(KERNEL_HEADER + kernel))
  builder.uniform("mirageResolution", RASTER.toFloat(), RASTER.toFloat())
  builder.uniform("mirageTime", time)
  builder.uniform("rainAmount", 0.35f)
  builder.uniform("blurRadius", 1.6f)
  builder.uniform("dropScale", 1.6f)
  builder.child("dropletMap", dropletMapShader())
  builder.child("content", contentShader())
  return rasterize(builder.makeShader())
}

/**
 * A synthetic droplet map standing in for the baked [DropletMap] texture: a procedural field that
 * scatters paraboloid drops on a 512-px grid and encodes the same channels the kernel decodes — surface
 * normal in R,G (`0.5 + 0.5 * n`), coverage/height in A, and a per-cell random phase in B. It samples in
 * texel space just like the REPEAT-tiled bitmap child, so the kernel's `dropletMap.eval(mod(...,512))`
 * reads a non-degenerate field with real drops, exercising refraction + the life cycle in the test.
 */
private fun dropletMapShader(): Shader = RuntimeEffect.makeForShader(
  """
  float2 cellHash(float2 c) {
    float3 p = fract(float3(c.xyx) * float3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.xx + p.yz) * p.zy);
  }
  half4 main(float2 xy) {
    float2 grid = xy / 64.0;            // ~8 cells across the 512 texture
    float2 cell = floor(grid);
    float2 h = cellHash(cell);
    float2 local = fract(grid) - float2(0.35 + 0.3 * h.x, 0.35 + 0.3 * h.y);
    float radius = 0.22 + 0.12 * h.x;
    float dist = length(local);
    float u = dist / radius;
    if (u >= 1.0) { return half4(0.5, 0.5, 0.0, 0.0); }
    float cov = (1.0 - u * u * u * u);
    float slope = 4.0 * u * (1.0 - u);        // hump peaking mid-drop, matching the generator
    float2 n = (dist > 1e-4) ? (local / dist) * slope : float2(0.0);
    return half4(half(0.5 + 0.5 * n.x), half(0.5 + 0.5 * n.y), half(h.y), half(cov));
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }

/** A deterministic opaque diagonal gradient, so the sharp/blur taps sample real spatial variation. */
private fun contentShader(): Shader = RuntimeEffect.makeForShader(
  """
  half4 main(float2 xy) {
    float2 uv = xy / float2($RASTER.0, $RASTER.0);
    return half4(half(uv.x), half(uv.y), half(1.0 - uv.x), 1.0);
  }
  """.trimIndent(),
).let { RuntimeShaderBuilder(it).makeShader() }

/** Draws [shader] over an opaque raster and returns its RGBA_8888 bytes. */
private fun rasterize(shader: Shader): ByteArray {
  val info = ImageInfo(RASTER, RASTER, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
  val surface = Surface.makeRaster(info)
  val canvas: Canvas = surface.canvas
  canvas.drawPaint(Paint().apply { this.shader = shader })

  val bitmap = Bitmap().apply { allocPixels(info) }
  surface.readPixels(bitmap, 0, 0)
  return bitmap.readPixels() ?: error("readPixels returned null")
}

private fun luma(argb: ByteArray, i: Int): Double {
  val r = argb[i].toInt() and 0xFF
  val g = argb[i + 1].toInt() and 0xFF
  val b = argb[i + 2].toInt() and 0xFF
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun meanLuma(argb: ByteArray): Double {
  var sum = 0.0
  var n = 0
  var i = 0
  while (i < argb.size) {
    sum += luma(argb, i)
    n++
    i += 4
  }
  return sum / n
}

private fun lumaStdDev(argb: ByteArray): Double {
  val mean = meanLuma(argb)
  var sum = 0.0
  var n = 0
  var i = 0
  while (i < argb.size) {
    val d = luma(argb, i) - mean
    sum += d * d
    n++
    i += 4
  }
  return sqrt(sum / n)
}

private fun minAlpha(argb: ByteArray): Int {
  var m = 255
  var i = 3
  while (i < argb.size) {
    val a = argb[i].toInt() and 0xFF
    if (a < m) m = a
    i += 4
  }
  return m
}

/** Mean absolute per-byte difference between two equally sized RGBA buffers (0..255 scale). */
private fun meanAbsDiff(a: ByteArray, b: ByteArray): Double {
  require(a.size == b.size) { "buffers differ in size: ${a.size} vs ${b.size}" }
  var sum = 0L
  for (i in a.indices) sum += kotlin.math.abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
  return sum.toDouble() / a.size
}
