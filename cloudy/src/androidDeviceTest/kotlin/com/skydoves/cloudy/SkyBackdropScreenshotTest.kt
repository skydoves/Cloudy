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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * Backdrop-blur (`Modifier.sky` + `Modifier.cloudy(sky = ...)`) screenshot spec — instrumented.
 *
 * ## Why this lives in androidDeviceTest (not androidHostTest)
 *
 * The backdrop path is GPU/RenderThread-dependent: `Modifier.sky` is a
 * [androidx.compose.ui.node.DrawModifierNode] that, during its own draw pass, records the container
 * subtree into a [androidx.compose.ui.graphics.layer.GraphicsLayer] ([Sky.backgroundLayer]) and the
 * descendant overlay re-samples and blurs that layer (two-pass capture gated by `Sky.isCapturing`,
 * per issue #112). Robolectric's host capture pipeline never runs `SkyModifierNode.draw()`, so this
 * spec belongs on an emulator/device, not the host — see the git history of this file for the probe
 * that verified the host no-op.
 *
 * ## What is asserted (relative pixel diffs, no golden files)
 *
 * The card sits over a high-frequency striped backdrop. Each capture reads the composed pixels of the
 * card region; assertions compare captures against each other, not against a stored image:
 *  - radius-0 == raw backdrop (passthrough), and deterministic.
 *  - radius-20 softens the backdrop (less stripe contrast than radius-0) and is deterministic.
 *  - tint at radius 0 changes the composed result.
 *
 * ## Capture safety (formerly a crash)
 *
 * `captureToImage()` of a radius > 0 (or tinted / scrimmed) `Modifier.cloudy(sky)` node used to SIGSEGV
 * the RenderThread with an unbounded `prepareTreeImpl` recursion — the backdrop's blur `GraphicsLayer`
 * recorded `drawLayer(sky.backgroundLayer)`, and since the node is a descendant of the sky recorder,
 * that formed a cyclic `RenderNode` graph (`sky → card → blurLayer → sky`) with no cycle guard. The
 * backdrop now samples a rasterized SNAPSHOT of the sky region (`drawImage`, no `drawLayer` back-edge —
 * the acyclic structure the API < 31 CPU path always used), so the blur/tint pixel effects are asserted
 * here directly. `BackdropCaptureCrashRegressionTest` guards the does-not-crash property; the skiko GPU
 * blur pixel effect is additionally asserted by `SkyBackdropRasterTest` in desktopTest.
 */
internal class SkyBackdropScreenshotTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val surfaceDp = 240
  private val cardWidthDp = 120
  private val cardHeightDp = 80

  // Card params, swapped between captures. setContent may only be called once per rule, so the
  // fixture reads these as snapshot state and each capture updates them then re-captures.
  private var radiusState by mutableStateOf(0)
  private var tintState by mutableStateOf(Color.Transparent)
  private var contentSet = false

  private companion object {
    const val ROOT_TAG = "sky-root"
  }

  /**
   * 240dp sky container: a dark fill + sharp horizontal stripes as the backdrop, with a centered
   * 120x80dp rounded card that samples the backdrop behind it. The high-frequency stripe pattern (not
   * a smooth gradient) makes blur measurably distinguishable from no-blur.
   */
  @Composable
  private fun BackdropFixture() {
    val sky = rememberSky()
    Box(
      modifier = Modifier.testTag(ROOT_TAG).size(surfaceDp.dp).sky(sky),
      contentAlignment = Alignment.Center,
    ) {
      Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222244)))
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stripe = size.height / 24f
        var y = 0f
        var on = true
        while (y < size.height) {
          if (on) {
            drawRect(color = Color.White, topLeft = Offset(0f, y), size = Size(size.width, stripe))
          }
          y += stripe
          on = !on
        }
      }
      Box(
        modifier = Modifier
          .size(width = cardWidthDp.dp, height = cardHeightDp.dp)
          .clip(RoundedCornerShape(16.dp))
          .cloudy(sky = sky, radius = radiusState, tint = tintState),
      )
    }
  }

  /** A card capture: row-major ARGB pixels plus the real pixel dimensions of the cropped region. */
  private class Capture(val pixels: IntArray, val width: Int, val height: Int)

  /**
   * Sets [radiusState]/[tintState], captures the WHOLE sky root, then crops the centered card region.
   *
   * Capturing the root (not the card sub-node) is deliberate: `Modifier.cloudy`'s blur node holds a
   * `RenderEffect` that samples the sky's `GraphicsLayer`, which lives in the root's RenderNode tree.
   * `captureToImage()` on the card sub-node alone re-renders it into an isolated PixelCopy tree where
   * that cross-tree layer reference becomes a cyclic RenderNode graph → `prepareTreeImpl` stack
   * overflow (the same #112 failure mode). Capturing the root keeps the sky layer and the overlay in
   * one tree, so no cycle forms; the card region is then cropped by its known centered geometry.
   */
  private fun captureCard(radius: Int, tint: Color): Capture {
    if (!contentSet) {
      composeTestRule.setContent { BackdropFixture() }
      contentSet = true
    }
    radiusState = radius
    tintState = tint
    composeTestRule.waitForIdle()
    // The API 31+ backdrop samples the sky through an async rasterized snapshot (drawImage, no
    // drawLayer(sky.backgroundLayer) — the acyclic capture-safe path, see BackdropClearBlurMachine),
    // so the first frame after a state change is a cold-start transparent until the snapshot lands.
    // Settle a couple of frames so the captured region reflects the sampled backdrop, not the cold gap.
    composeTestRule.mainClock.autoAdvance = true
    Thread.sleep(600)
    composeTestRule.waitForIdle()
    val map = composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage().toPixelMap()

    // Crop the centered card region. The card is centered in a square surface, so the pixel scale is
    // captureWidth / surfaceDp; derive the card's pixel box from that scale (independent of density).
    val scale = map.width.toFloat() / surfaceDp
    val cardW = (cardWidthDp * scale).toInt().coerceAtMost(map.width)
    val cardH = (cardHeightDp * scale).toInt().coerceAtMost(map.height)
    val left = (map.width - cardW) / 2
    val top = (map.height - cardH) / 2
    val pixels = IntArray(cardW * cardH)
    for (y in 0 until cardH) {
      for (x in 0 until cardW) {
        pixels[y * cardW + x] = map[left + x, top + y].toArgb()
      }
    }
    return Capture(pixels, cardW, cardH)
  }

  /** Mean absolute per-channel (A,R,G,B) difference between two equally sized captures (0..255). */
  private fun meanAbsDiff(a: Capture, b: Capture): Double {
    require(a.pixels.size == b.pixels.size) {
      "captures differ in size: ${a.pixels.size} vs ${b.pixels.size}"
    }
    var sum = 0L
    for (i in a.pixels.indices) {
      val pa = a.pixels[i]
      val pb = b.pixels[i]
      sum += abs(((pa ushr 24) and 0xFF) - ((pb ushr 24) and 0xFF))
      sum += abs(((pa ushr 16) and 0xFF) - ((pb ushr 16) and 0xFF))
      sum += abs(((pa ushr 8) and 0xFF) - ((pb ushr 8) and 0xFF))
      sum += abs((pa and 0xFF) - (pb and 0xFF))
    }
    return sum.toDouble() / (a.pixels.size * 4)
  }

  @Test
  fun backdrop_passthrough_isDeterministicAndShowsBackdrop() {
    // radius 0 + transparent draws the raw backdrop region unmodified (a plain drawLayer, no compositing
    // over-draw). This is the ONLY backdrop capture that survives captureToImage on device — see the
    // capture-crash finding below. Assert it is deterministic (two captures are byte-identical) and that
    // it actually shows the striped backdrop (not a blank card).
    val ref = captureCard(radius = 0, tint = Color.Transparent)
    val again = captureCard(radius = 0, tint = Color.Transparent)
    assertEquals("radius-0 passthrough must be deterministic", 0.0, meanAbsDiff(ref, again), 0.0)
    assertTrue("passthrough must show the backdrop stripes, not a flat card", hasContrast(ref))
  }

  @Test
  fun backdrop_blur_softensBackdrop_andIsDeterministic() {
    // radius 20 blurs the striped backdrop, so the card region has strictly LESS high-frequency
    // contrast than the radius-0 passthrough. captureToImage of a blurred backdrop used to SIGSEGV the
    // RenderThread (cyclic RenderNode graph, issue #112); it is now capture-safe because the backdrop
    // samples a rasterized snapshot (drawImage) instead of drawLayer(sky.backgroundLayer). See
    // BackdropClearBlurMachine and BackdropCaptureCrashRegressionTest.
    val sharp = captureCard(radius = 0, tint = Color.Transparent)
    val blurred = captureCard(radius = 20, tint = Color.Transparent)
    assertTrue("blur must actually change the composed card", meanAbsDiff(sharp, blurred) > 1.0)
    // Blur is a low-pass filter: it reduces the high-frequency vertical stripe energy. Measure that
    // energy (mean luma difference between vertically adjacent pixels) in the card interior — robust to
    // the transparent rounded corners that a raw min/max contrast range would be dominated by.
    assertTrue(
      "blur must reduce vertical high-frequency energy (softer than sharp)",
      verticalEnergy(blurred) < verticalEnergy(sharp),
    )
    val blurredAgain = captureCard(radius = 20, tint = Color.Transparent)
    assertEquals("blur capture must be deterministic", 0.0, meanAbsDiff(blurred, blurredAgain), 0.0)
  }

  @Test
  fun backdrop_tint_changesComposedResult() {
    // A non-transparent tint at radius 0 blends over the sampled backdrop, so the composed card differs
    // from the untinted passthrough. This capture also used to crash (tint over-draw) and is now safe.
    val untinted = captureCard(radius = 0, tint = Color.Transparent)
    val tinted = captureCard(radius = 0, tint = Color(0x66FFFFFF))
    assertTrue("tint must change the composed card", meanAbsDiff(untinted, tinted) > 1.0)
  }

  /**
   * Mean absolute luma difference between vertically adjacent pixels, over the card interior (a 50%
   * centered box, avoiding the transparent rounded corners). High for sharp horizontal stripes, lower
   * once a blur softens the vertical transitions — a direct measure of high-frequency energy.
   */
  private fun verticalEnergy(capture: Capture): Double {
    val w = capture.width
    val h = capture.height
    val x0 = w / 4
    val x1 = w - w / 4
    val y0 = h / 4
    val y1 = h - h / 4
    var sum = 0L
    var count = 0L
    for (y in y0 until y1 - 1) {
      for (x in x0 until x1) {
        val a = capture.pixels[y * w + x]
        val b = capture.pixels[(y + 1) * w + x]
        val la = ((a ushr 16 and 0xFF) + (a ushr 8 and 0xFF) + (a and 0xFF)) / 3
        val lb = ((b ushr 16 and 0xFF) + (b ushr 8 and 0xFF) + (b and 0xFF)) / 3
        sum += abs(la - lb)
        count++
      }
    }
    return if (count == 0L) 0.0 else sum.toDouble() / count
  }

  /** True if the capture has real per-channel variation (the backdrop stripes), not a flat fill. */
  private fun hasContrast(capture: Capture): Boolean {
    var min = 255
    var max = 0
    for (p in capture.pixels) {
      val luma = ((p ushr 16 and 0xFF) + (p ushr 8 and 0xFF) + (p and 0xFF)) / 3
      if (luma < min) min = luma
      if (luma > max) max = luma
    }
    return max - min > 16
  }
}
