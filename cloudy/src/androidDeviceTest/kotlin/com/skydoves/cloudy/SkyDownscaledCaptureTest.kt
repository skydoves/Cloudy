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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * Pins the region alignment of the DOWNSCALED backdrop capture.
 *
 * `BackdropClearBlurrer` snapshots the sky at a reduced scale once the blur is wide enough to hide the
 * resample (`captureScaleFor`: radius >= 16 captures at 1/4). The sampled sub-region is then expressed
 * in SNAPSHOT pixels — `srcX/srcY` divide the node's sky-space offset by that scale — and drawn back
 * out at node size. Get that division wrong and the blur silently shows the WRONG PART of the
 * backdrop: at 1/4, forgetting to divide moves the sampled band four times further down the sky.
 *
 * The existing striped fixture in [SkyBackdropScreenshotTest] cannot catch this — its backdrop is
 * horizontally uniform and vertically periodic, so a shifted sample still averages to the same color.
 * This fixture is a vertical red-to-blue gradient with the card parked OFF-CENTER near the top, which
 * turns any drift in the sampled band into a large red/blue shift.
 *
 * A normalized blur kernel preserves the mean of a linear gradient, so the assertion is that the
 * blurred card's mean color still matches the un-blurred passthrough's. The companion assertion —
 * that the blur measurably flattened the gradient — keeps the test from passing vacuously if the blur
 * silently stopped being applied at all.
 */
internal class SkyDownscaledCaptureTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val surfaceDp = 240
  private val cardWidthDp = 120
  private val cardHeightDp = 60

  // Parks the card near the top of the sky, so its sample offset is a large non-zero value that the
  // scale division has to get right. Centered (the other fixture's layout) would make offset.y small
  // and the drift correspondingly hard to see.
  private val cardTopPaddingDp = 24

  private var radiusState by mutableStateOf(0)
  private var contentSet = false

  private companion object {
    const val ROOT_TAG = "sky-root"

    // radius >= 16 is the tier that captures at 1/4 scale (captureScaleFor).
    const val DOWNSCALED_RADIUS = 20

    const val SETTLE_TIMEOUT_NANOS = 1_500_000_000L

    // Mean-channel tolerance. Blurring a linear gradient preserves its mean exactly apart from edge
    // clamping, so a correct sample lands well inside this; a 1/4-scale offset drift moves the
    // sampled band far enough down the gradient to shift a channel by well over 100.
    const val MEAN_TOLERANCE = 16.0
  }

  /** Vertical red -> blue gradient backdrop with an off-center card sampling it. */
  @Composable
  private fun GradientFixture() {
    val sky = rememberSky()
    Box(
      modifier = Modifier.testTag(ROOT_TAG).size(surfaceDp.dp).sky(sky),
      contentAlignment = Alignment.TopCenter,
    ) {
      Box(
        modifier = Modifier.fillMaxSize().background(
          Brush.verticalGradient(listOf(Color.Red, Color.Blue)),
        ),
      )
      Box(
        modifier = Modifier
          .padding(top = cardTopPaddingDp.dp)
          .size(width = cardWidthDp.dp, height = cardHeightDp.dp)
          .cloudy(sky = sky, radius = radiusState),
      )
    }
  }

  private class Capture(val pixels: IntArray, val width: Int, val height: Int)

  @Test
  fun downscaledCapture_samplesTheSameBackdropRegionAsFullResolution() {
    val sharp = captureCard(radius = 0)
    val blurred = captureCard(radius = DOWNSCALED_RADIUS)

    val sharpMean = meanChannels(sharp)
    val blurredMean = meanChannels(blurred)

    // Region alignment: the 1/4-scale snapshot must sample the same band of the gradient.
    for (channel in 0 until 3) {
      val delta = abs(sharpMean[channel] - blurredMean[channel])
      assertTrue(
        "channel $channel mean drifted by $delta (sharp=${sharpMean[channel]}, " +
          "blurred=${blurredMean[channel]}) — the downscaled capture is sampling a different " +
          "region of the sky",
        delta <= MEAN_TOLERANCE,
      )
    }

    // Not vacuous: the blur must actually have flattened the gradient.
    val sharpSpread = verticalSpread(sharp)
    val blurredSpread = verticalSpread(blurred)
    assertTrue(
      "blur did not flatten the gradient (sharp spread=$sharpSpread, blurred=$blurredSpread)",
      blurredSpread < sharpSpread,
    )
  }

  private fun captureCard(radius: Int): Capture {
    if (!contentSet) {
      composeTestRule.setContent { GradientFixture() }
      contentSet = true
    }
    radiusState = radius
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    // The snapshot lands over the first draw passes after a state change, so poll until two
    // consecutive captures are identical rather than sleeping a fixed amount.
    var previous: Capture? = null
    val deadline = System.nanoTime() + SETTLE_TIMEOUT_NANOS
    while (true) {
      composeTestRule.waitForIdle()
      val current = captureCardRegion()
      if (previous != null && meanAbsDiff(previous, current) == 0.0) return current
      check(System.nanoTime() < deadline) { "backdrop capture did not settle within timeout" }
      previous = current
    }
  }

  /**
   * Captures the sky root and crops the card region. Capturing the root rather than the card sub-node
   * is deliberate — see [SkyBackdropScreenshotTest.captureCardRegion]: an isolated sub-node capture
   * re-renders the cross-tree sky layer reference into a cyclic graph (issue #112).
   */
  private fun captureCardRegion(): Capture {
    val map = composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage().toPixelMap()
    val scale = map.width.toFloat() / surfaceDp
    val cardW = (cardWidthDp * scale).toInt().coerceAtMost(map.width)
    val cardH = (cardHeightDp * scale).toInt().coerceAtMost(map.height)
    val left = (map.width - cardW) / 2
    val top = (cardTopPaddingDp * scale).toInt().coerceAtMost(map.height - cardH)
    val pixels = IntArray(cardW * cardH)
    for (y in 0 until cardH) {
      for (x in 0 until cardW) {
        pixels[y * cardW + x] = map[left + x, top + y].toArgb()
      }
    }
    return Capture(pixels, cardW, cardH)
  }

  /** Mean R, G, B of a capture (0..255 each). */
  private fun meanChannels(capture: Capture): DoubleArray {
    val sums = LongArray(3)
    for (pixel in capture.pixels) {
      sums[0] += (pixel ushr 16) and 0xFF
      sums[1] += (pixel ushr 8) and 0xFF
      sums[2] += pixel and 0xFF
    }
    return DoubleArray(3) { sums[it].toDouble() / capture.pixels.size }
  }

  /**
   * Difference between the mean of the top row band and the bottom row band — how much vertical
   * gradient survives. Blur flattens it; a sharp gradient keeps it.
   */
  private fun verticalSpread(capture: Capture): Double {
    val band = (capture.height / 8).coerceAtLeast(1)
    fun bandMean(startRow: Int): Double {
      var sum = 0L
      var count = 0
      for (y in startRow until (startRow + band).coerceAtMost(capture.height)) {
        for (x in 0 until capture.width) {
          val pixel = capture.pixels[y * capture.width + x]
          sum += pixel and 0xFF // blue rises down the gradient
          count++
        }
      }
      return sum.toDouble() / count
    }
    return abs(bandMean(0) - bandMean(capture.height - band))
  }

  private fun meanAbsDiff(a: Capture, b: Capture): Double {
    require(a.pixels.size == b.pixels.size)
    var sum = 0L
    for (i in a.pixels.indices) {
      val pa = a.pixels[i]
      val pb = b.pixels[i]
      sum += abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
      sum += abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
      sum += abs((pa and 0xFF) - (pb and 0xFF))
    }
    return sum.toDouble() / (a.pixels.size * 3)
  }
}
