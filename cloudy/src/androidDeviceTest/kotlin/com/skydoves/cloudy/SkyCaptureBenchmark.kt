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
import android.graphics.Picture
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Measures the per-frame backdrop snapshot cost of the API 31+ GPU tier.
 *
 * `BackdropClearBlurrer.captureInline` calls `GraphicsLayer.toImageBitmap()` every time the sky's
 * `contentVersion` advances, and `SkyModifierNode.recapture` advances it once PER FRAME while the
 * frame driver is pumping (scroll / fling / animation). The captured layer is the WHOLE sky
 * container, so a card-sized backdrop still pays a full-screen rasterize each frame.
 *
 * `toImageBitmap()` on this tier resolves to `LayerSnapshotV28`, i.e.
 * `Bitmap.createBitmap(Picture)` with `requiresHardwareAcceleration = true` — a synchronous
 * GPU rasterize into a freshly allocated hardware bitmap. Two benchmarks:
 *
 *  - [captureLayer] drives the production call (same synchronous `startCoroutine` driver as
 *    `captureImageBitmapOrNull`) over a real recorded [GraphicsLayer].
 *  - [rasterizePicture] isolates the same primitive without Compose, as a cross-check that the
 *    cost curve is the rasterize and not layer bookkeeping.
 *
 * Cases model a full-screen sky capture (what ships today) against the sampled sub-region and
 * downscaled captures the optimization would use.
 *
 * Emulator runs rasterize on the host GPU/SwiftShader, so absolute numbers are NOT
 * device-representative; the size-to-size RATIO is the transferable result.
 */
@RunWith(Parameterized::class)
internal class SkyCaptureBenchmark(private val case: Case) {

  @get:Rule
  val benchmarkRule = BenchmarkRule()

  @get:Rule
  val composeRule = createComposeRule()

  /**
   * Absorbs process-level one-time init (HardwareRenderer / EGL context / shader warm) before any
   * measured loop. Without it the FIRST parameterized case in the class pays that init and reports a
   * cost that is not the rasterize — which is exactly what made the first run of this benchmark
   * report 1080p as SLOWER than 1440p.
   */
  @Before
  fun warmUpProcess() {
    val picture = Picture()
    val canvas = picture.beginRecording(WARMUP_SIZE, WARMUP_SIZE)
    canvas.drawColor(android.graphics.Color.BLUE)
    picture.endRecording()
    repeat(WARMUP_ITERATIONS) { Bitmap.createBitmap(picture) }
  }

  @Test
  fun captureLayer() {
    var layer: GraphicsLayer? = null
    var density: Density? = null
    composeRule.setContent {
      layer = rememberGraphicsLayer()
      density = LocalDensity.current
    }
    composeRule.waitForIdle()
    val target = layer!!
    val d = density!!
    val size = IntSize(case.width, case.height)
    composeRule.runOnUiThread {
      target.record(d, LayoutDirection.Ltr, size) { drawBackdropContent(case.width, case.height) }
    }

    benchmarkRule.measureRepeated { captureBlocking(target) }
  }

  @Test
  fun rasterizePicture() {
    val picture = Picture()
    val canvas = picture.beginRecording(case.width, case.height)
    val compose = androidx.compose.ui.graphics.Canvas(canvas)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
      Density(1f),
      LayoutDirection.Ltr,
      compose,
      Size(case.width.toFloat(), case.height.toFloat()),
    ) { drawBackdropContent(case.width, case.height) }
    picture.endRecording()

    benchmarkRule.measureRepeated { Bitmap.createBitmap(picture) }
  }

  /**
   * The candidate fix, measured end to end: instead of snapshotting the full sky layer, blit it into
   * a quarter-scale layer first and snapshot THAT.
   *
   * This is the only downscale shape the current structure allows — `toImageBitmap()` takes no
   * region or scale argument, and the sky recorder must keep recording at full size because other
   * overlays sample it — so the saving has to survive one extra `drawLayer` pass. Measuring it
   * (rather than projecting from the size curve) is the point: if the extra pass costs more than the
   * 16x fewer snapshot pixels save, the optimization is not real.
   */
  @Test
  fun captureDownscaled() {
    var source: GraphicsLayer? = null
    var scaled: GraphicsLayer? = null
    var density: Density? = null
    composeRule.setContent {
      source = rememberGraphicsLayer()
      scaled = rememberGraphicsLayer()
      density = LocalDensity.current
    }
    composeRule.waitForIdle()
    val src = source!!
    val dst = scaled!!
    val d = density!!
    val dstSize = IntSize(
      (case.width / DOWNSCALE).coerceAtLeast(1),
      (case.height / DOWNSCALE).coerceAtLeast(1),
    )
    composeRule.runOnUiThread {
      src.record(d, LayoutDirection.Ltr, IntSize(case.width, case.height)) {
        drawBackdropContent(case.width, case.height)
      }
    }

    benchmarkRule.measureRepeated {
      // Both the blit and the snapshot are inside the measured block: the blit is work the current
      // code does not do, so charging only the snapshot would flatter the candidate.
      dst.record(d, LayoutDirection.Ltr, dstSize) {
        scale(1f / DOWNSCALE, 1f / DOWNSCALE, pivot = androidx.compose.ui.geometry.Offset.Zero) {
          drawLayer(src)
        }
      }
      captureBlocking(dst)
    }
  }

  /**
   * [captureDownscaled] with the GPU forced to finish, so it can be compared against
   * [rasterizeAndReadback] on equal terms.
   *
   * The submit-only numbers are nearly size-blind, so they cannot show what downscaling buys. The
   * total (submit + rasterize) is where 16x fewer pixels should appear, and it is what the device
   * actually pays across the UI thread and the GPU combined.
   */
  @Test
  fun captureDownscaledReadback() {
    var source: GraphicsLayer? = null
    var scaled: GraphicsLayer? = null
    var density: Density? = null
    composeRule.setContent {
      source = rememberGraphicsLayer()
      scaled = rememberGraphicsLayer()
      density = LocalDensity.current
    }
    composeRule.waitForIdle()
    val src = source!!
    val dst = scaled!!
    val d = density!!
    val dstSize = IntSize(
      (case.width / DOWNSCALE).coerceAtLeast(1),
      (case.height / DOWNSCALE).coerceAtLeast(1),
    )
    composeRule.runOnUiThread {
      src.record(d, LayoutDirection.Ltr, IntSize(case.width, case.height)) {
        drawBackdropContent(case.width, case.height)
      }
    }

    benchmarkRule.measureRepeated {
      dst.record(d, LayoutDirection.Ltr, dstSize) {
        scale(1f / DOWNSCALE, 1f / DOWNSCALE, pivot = androidx.compose.ui.geometry.Offset.Zero) {
          drawLayer(src)
        }
      }
      captureBlocking(dst)?.asAndroidBitmap()?.copy(Bitmap.Config.ARGB_8888, false)
    }
  }

  /**
   * Same rasterize as [rasterizePicture], but forces the pixels to actually LAND by copying the
   * hardware bitmap back to a software one.
   *
   * `Bitmap.createBitmap(Picture)` hands back a HARDWARE bitmap: the rasterize is submitted to the
   * GPU and the call can return before the pixels exist. If [rasterizePicture] stays flat across
   * sizes while this one scales with pixel count, then what the UI thread pays inline is submission
   * overhead and the real rasterize cost is deferred to a later GPU sync — a materially different
   * conclusion about where the frame time actually goes.
   */
  @Test
  fun rasterizeAndReadback() {
    val picture = Picture()
    val canvas = picture.beginRecording(case.width, case.height)
    val compose = androidx.compose.ui.graphics.Canvas(canvas)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
      Density(1f),
      LayoutDirection.Ltr,
      compose,
      Size(case.width.toFloat(), case.height.toFloat()),
    ) { drawBackdropContent(case.width, case.height) }
    picture.endRecording()

    benchmarkRule.measureRepeated {
      Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false)
    }
  }

  /**
   * Runs the `suspend fun toImageBitmap()` to completion synchronously — a copy of
   * `BackdropClearBlurrer.captureImageBitmapOrNull`, so the benchmark measures the exact shape of
   * the production call rather than a `runBlocking` approximation.
   */
  private fun captureBlocking(layer: GraphicsLayer): ImageBitmap? {
    var result: Result<ImageBitmap>? = null
    val continuation = object : Continuation<ImageBitmap> {
      override val context: CoroutineContext = EmptyCoroutineContext
      override fun resumeWith(outcome: Result<ImageBitmap>) {
        result = outcome
      }
    }
    (suspend { layer.toImageBitmap() }).startCoroutine(continuation)
    return result?.getOrThrow()
  }

  /** One capture case: the pixel size of the layer handed to `toImageBitmap()`. */
  internal data class Case(val label: String, val width: Int, val height: Int) {
    override fun toString(): String = "$label(${width}x$height)"
  }

  internal companion object {
    private const val DOWNSCALE = 4
    private const val WARMUP_SIZE = 256
    private const val WARMUP_ITERATIONS = 20

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases(): List<Case> = listOf(
      // What ships today: the whole sky container is captured every pumped frame.
      Case("fullScreen1080p", 1080, 2400),
      Case("fullScreen1440p", 1440, 3120),
      // Capture only the node's sampled sub-region instead of the whole sky.
      Case("regionWideCard", 1080, 600),
      Case("regionMediumCard", 720, 480),
      Case("regionSmallCard", 540, 360),
      // Or keep the full sky but rasterize it downscaled (blur hides the resample).
      Case("fullScreenHalf", 540, 1200),
      Case("fullScreenQuarter", 270, 600),
      // Extra points so the cost curve can be fit rather than inferred from two ends.
      Case("px_320x320", 320, 320),
      Case("px_640x640", 640, 640),
      Case("px_1280x1280", 1280, 1280),
      Case("px_1920x1920", 1920, 1920),
    )
  }
}

/**
 * Representative backdrop content: a gradient-ish band pattern with overlapping translucent shapes,
 * so the rasterize does real overdraw work rather than filling one flat rect.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackdropContent(
  width: Int,
  height: Int,
) {
  val w = width.toFloat()
  val h = height.toFloat()
  drawRect(Color(0xFF1B2A4A))
  val bands = 24
  val bandHeight = h / bands
  for (i in 0 until bands) {
    val t = i.toFloat() / bands
    drawRect(
      color = Color(
        red = 0.15f + 0.5f * t,
        green = 0.25f + 0.35f * (1f - t),
        blue = 0.55f + 0.4f * t,
        alpha = 1f,
      ),
      topLeft = Offset(0f, i * bandHeight),
      size = Size(w, bandHeight),
    )
  }
  // Overlapping translucent circles: forces blending, closer to a real photo/list backdrop.
  val radius = minOf(w, h) * 0.18f
  for (i in 0 until 8) {
    val fx = (i * 137 % 100) / 100f
    val fy = (i * 71 % 100) / 100f
    drawCircle(
      color = Color.White.copy(alpha = 0.12f),
      radius = radius,
      center = Offset(fx * w, fy * h),
    )
  }
}
