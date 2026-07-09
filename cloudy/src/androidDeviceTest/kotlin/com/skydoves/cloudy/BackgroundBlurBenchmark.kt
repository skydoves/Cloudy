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

import android.graphics.Bitmap
import android.graphics.Color
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.skydoves.cloudy.internals.render.RenderScriptToolkit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Microbenchmark for the native [RenderScriptToolkit.backgroundBlur] pipeline.
 *
 * Lives in `androidDeviceTest` because [RenderScriptToolkit] is `internal` to `androidMain`; the
 * KMP android device-test compilation is a friend of `androidMain`, so this is the only place a
 * benchmark can call `backgroundBlur` without widening its visibility.
 *
 * Emulator ABI runs on the host CPU, not the device NEON path, so absolute numbers are only valid
 * for same-host before/after comparison, not as device-representative latency.
 */
@RunWith(Parameterized::class)
internal class BackgroundBlurBenchmark(private val case: Case) {

  @get:Rule
  val benchmarkRule = BenchmarkRule()

  private lateinit var src: Bitmap
  private lateinit var dst: Bitmap

  private fun setUp() {
    src = gradientBitmap(case.srcW, case.srcH)
    // dst must be exactly the crop size (backgroundBlur writes the full-res cropped output).
    dst = Bitmap.createBitmap(case.cropW, case.cropH, Bitmap.Config.ARGB_8888)
  }

  @Test
  fun backgroundBlur() {
    setUp()
    benchmarkRule.measureRepeated {
      RenderScriptToolkit.backgroundBlur(
        srcBitmap = src,
        dstBitmap = dst,
        cropX = case.cropX,
        cropY = case.cropY,
        radius = case.radius,
        scale = case.scale,
      )
    }
  }

  /**
   * One benchmark case. [srcW]/[srcH] is the full background; [cropW]/[cropH] (== dst) is the
   * blurred region; [scale] is the internal downscale before blur.
   */
  data class Case(
    val name: String,
    val srcW: Int,
    val srcH: Int,
    val cropX: Int,
    val cropY: Int,
    val cropW: Int,
    val cropH: Int,
    val radius: Int,
    val scale: Float,
  ) {
    override fun toString(): String = name // Parameterized uses this for the test name.
  }

  companion object {
    /**
     * Deterministic diagonal gradient: reproducible run-to-run, high-frequency enough that the blur
     * kernel does real work, and rowBytes stays width*4 as the toolkit's stride check requires.
     */
    private fun gradientBitmap(w: Int, h: Int): Bitmap {
      val pixels = IntArray(w * h)
      var i = 0
      for (y in 0 until h) {
        for (x in 0 until w) {
          val r = (x * 255) / w
          val g = (y * 255) / h
          val b = ((x + y) * 255) / (w + h)
          pixels[i++] = Color.rgb(r, g, b)
        }
      }
      return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases(): List<Case> = listOf(
      Case("phone_1080x800_s25_r25", 1080, 2400, 0, 800, 1080, 800, 25, 0.25f),
      Case("tablet_2000x1500_s25_r25", 2560, 1600, 280, 50, 2000, 1500, 25, 0.25f),
      // Aggressive scale 0.10 with a large dst: shows scale-up-to-full-res dominating regardless
      // of how small the downscaled blur input is.
      Case("bigcard_1080x1920_s10_r25", 1080, 1920, 0, 0, 1080, 1920, 25, 0.10f),
    )
  }
}
