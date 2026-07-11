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
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.skydoves.cloudy.internal.CompiledProgram
import com.skydoves.cloudy.internal.Dialect
import com.skydoves.cloudy.internal.GlProgram
import com.skydoves.cloudy.internal.MirageCompiler
import com.skydoves.cloudy.internal.MirageGlslEs
import com.skydoves.cloudy.internal.UniformSink
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Microbenchmark for one GLES mirage backdrop roundtrip on the API 29-32 band: the whole
 * `GlProgram.render` -> `GlEnv.render` path (`texImage2D` upload -> FBO draw -> `glFinish` ->
 * `eglSwapBuffers` -> `acquireLatestImage` -> `wrapHardwareBuffer` -> `copy(ARGB_8888)`), isolated so a
 * frame-budget question does not need to force the band on a 33+ device (calling `GlProgram` directly
 * bypasses `MirageBackendBand.resolve`, so nothing production is patched).
 *
 * Lives in `androidDeviceTest` for the same reason as [BackgroundBlurBenchmark]: `GlProgram` /
 * `MirageGlslEs` are `internal` to `androidMain`, and the KMP android device-test compilation is a
 * friend, so this is the only place a benchmark can drive them without widening visibility. The uniform
 * bind mirrors GlProgramMatchTest's (schema defaults through the recording sink).
 *
 * ## Representativeness (read before trusting the numbers)
 * These are the **lower bound** on the measuring device. On a flagship device the
 * roundtrip's dominant costs — the full-resolution ARGB_8888 readback copy and the `glFinish` stall —
 * are memory-bandwidth and GPU-throughput bound, and the actual band targets (API 29-32 devices, ~2016
 * to 2019 midrange GPUs with roughly 3-5x lower system-memory bandwidth) can be several times slower.
 * So these numbers are valid **only** for same-device before/after regression comparison, never as
 * device-representative latency for the band's real hardware. Emulator SwiftShader is not a GPU and
 * distorts this further; run on a physical device.
 */
@RunWith(Parameterized::class)
internal class GlesRoundtripBenchmark(private val case: Case) {

  @get:Rule
  val benchmarkRule = BenchmarkRule()

  private lateinit var program: GlProgram
  private lateinit var compiled: CompiledProgram
  private lateinit var content: Bitmap

  private fun setUp() {
    compiled = MirageCompiler.compile(case.optic, Dialect.GlslEs)
    program = GlProgram(MirageGlslEs.translate(compiled.source))
    content = gradientContent(case.width, case.height)
  }

  @Test
  fun roundtrip() {
    setUp()
    benchmarkRule.measureRepeated {
      // A fresh recording sink per iteration matches the node's per-draw bind; the cost of building it
      // is negligible next to the GL roundtrip and the sink is what render() replays on the GL thread.
      val (sink, writes) = program.uniformSink()
      bindSchemaDefaults(sink, compiled)
      if (case.optic === MirageOptics.Chromatic) frameLens(sink, case.width, case.height)
      program.render(content, writes)
    }
  }

  /** One case: which optic and at what content size (the size the readback copy scales with). */
  data class Case(val name: String, val optic: Optic<*>, val width: Int, val height: Int) {
    override fun toString(): String = name // Parameterized uses this for the test name.
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases(): List<Case> = listOf(
      // Duotone = simplest colorize kernel; Chromatic = lens kernel (does real per-pixel work). Card
      // ~= a floating backdrop card region; fullscreen ~= a full-bleed pane, where the bandwidth-bound
      // copy dominates.
      Case("duotone_card_720x480", MirageOptics.Duotone, 720, 480),
      Case("duotone_fullscreen_1080x2400", MirageOptics.Duotone, 1080, 2400),
      Case("chromatic_card_720x480", MirageOptics.Chromatic, 720, 480),
      Case("chromatic_fullscreen_1080x2400", MirageOptics.Chromatic, 1080, 2400),
    )
  }
}

/** Deterministic RGB gradient content; run-to-run reproducible so the kernel does stable work. */
private fun gradientContent(w: Int, h: Int): Bitmap {
  val pixels = IntArray(w * h)
  var i = 0
  for (y in 0 until h) {
    for (x in 0 until w) {
      val r = x * 255 / (w - 1)
      val g = y * 255 / (h - 1)
      pixels[i++] = Color.argb(255, r, g, 255 - r)
    }
  }
  return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/**
 * Binds [compiled]'s schema defaults through the recording sink, as the node's binder does. The
 * `mirageResolution` uniform is not written here: the translator aliases it to `uResolution`, which
 * [GlProgram.render] binds to the content size itself (see GlProgram).
 */
@OptIn(ExperimentalMirage::class)
private fun bindSchemaDefaults(sink: UniformSink, compiled: CompiledProgram) {
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

/** Frames the lens over the whole raster so every pixel takes the lens branch (not the sdf early-out). */
private fun frameLens(sink: UniformSink, w: Int, h: Int) {
  sink.float2("lensCenter", w / 2f, h / 2f)
  sink.float2("lensSize", w.toFloat(), h.toFloat())
  sink.float("cornerRadius", 0f)
}
