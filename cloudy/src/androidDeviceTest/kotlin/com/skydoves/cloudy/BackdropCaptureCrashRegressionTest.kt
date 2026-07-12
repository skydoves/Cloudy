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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Regression for the backdrop `captureToImage` / PixelCopy crash: capturing a Compose tree with a
 * `Modifier.sky(sky)` recorder and a descendant `Modifier.cloudy(sky = sky, …)` backdrop node used to
 * SIGSEGV the RenderThread with an unbounded `RenderNode::prepareTreeImpl` recursion — a cyclic
 * RenderNode graph (`skyLayer → card → blurLayer → skyLayer`) that `captureToImage`'s forced full-tree
 * re-walk descends without a cycle guard.
 *
 * The frame-time `Sky.isCapturing` guard (issue #112) keeps the cycle out of on-screen rendering, but
 * it cannot protect the capture re-walk: the LIBRARY-owned blur layer keeps a stale
 * `drawLayer(sky.backgroundLayer)` back-edge that the guard's draw skip never clears, and the same
 * frame's on-screen draw re-records it anyway. The fix removes the back-edge structurally: the blur
 * backdrop samples a rasterized SNAPSHOT of the sky layer (`drawImage`, pixels only — see
 * [BackdropClearBlurrer][com.skydoves.cloudy.internal.BackdropClearBlurrer]), so no cycle can
 * form regardless of what triggers a full-tree prepare.
 *
 * Each of the four variants reproduced the crash before the fix. A crash kills the whole
 * instrumentation process, so a green run of this class IS the proof the cycle is broken. The
 * blur/tint pixel EFFECTS are asserted separately (blur softening on skiko `SkyBackdropRasterTest`;
 * passthrough/tint on device in `SkyBackdropScreenshotTest`).
 */
internal class BackdropCaptureCrashRegressionTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var radiusState by mutableStateOf(0)
  private var tintState by mutableStateOf(Color.Transparent)

  private companion object {
    const val ROOT_TAG = "root"
    const val CARD_TAG = "card"
  }

  @Composable
  private fun Fixture() {
    val sky = rememberSky()
    Box(
      modifier = Modifier.testTag(ROOT_TAG).size(240.dp).sky(sky),
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
          .size(width = 120.dp, height = 80.dp)
          .clip(RoundedCornerShape(16.dp))
          .testTag(CARD_TAG)
          .cloudy(sky = sky, radius = radiusState, tint = tintState),
      )
    }
  }

  /** A radius > 0 blur capture: the blur layer's `drawLayer(backgroundLayer)` back-edge was the cycle. */
  @Test
  fun rootCapture_singleBlur_doesNotCrash() {
    composeTestRule.setContent { Fixture() }
    radiusState = 20
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage()
  }

  /** Capturing radius 0 first, then swapping to a blur and re-capturing (re-keys the cached effect). */
  @Test
  fun rootCapture_swapZeroThenBlur_doesNotCrash() {
    composeTestRule.setContent { Fixture() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage()
    radiusState = 20
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage()
  }

  /** Radius 0 + a non-transparent tint: the over-draw composites the sampled region back into the tree. */
  @Test
  fun rootCapture_tintOnly_doesNotCrash() {
    composeTestRule.setContent { Fixture() }
    tintState = Color(0x66FFFFFF)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage()
  }

  /** Capturing the blurred card SUB-NODE directly (an isolated PixelCopy tree that used to cycle). */
  @Test
  fun cardSubNodeCapture_blur_doesNotCrash() {
    composeTestRule.setContent { Fixture() }
    radiusState = 20
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CARD_TAG).captureToImage()
  }

  /**
   * A mirage backdrop pipeline records stage-0 into a filter layer under a content-bound RenderEffect,
   * the same cyclic reference shape the blur layer had. On Android it samples the sky through a
   * rasterized snapshot ([MirageBackdropSnapshot][com.skydoves.cloudy.internal.MirageBackdropSnapshot],
   * gated by `backdropNeedsAcyclicSnapshot`), not a live `drawLayer(backgroundLayer)`, so the
   * `captureToImage` full-tree re-walk has no back-edge to recurse on.
   */
  @OptIn(ExperimentalMirage::class)
  @Test
  fun rootCapture_mirageBackdrop_doesNotCrash() {
    composeTestRule.setContent {
      val sky = rememberSky()
      Box(
        modifier = Modifier.testTag(ROOT_TAG).size(240.dp).sky(sky),
        contentAlignment = Alignment.Center,
      ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222244)))
        Box(
          modifier = Modifier
            .size(width = 120.dp, height = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .mirage(sky = sky) { filter(MirageShaders.Duotone) },
        )
      }
    }
    composeTestRule.waitForIdle()
    // Capture twice: the first may land before the sky's first capture pass (passthrough card); the
    // second runs after the pipeline has drawn through the filter chain, which is the cyclic structure.
    composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(ROOT_TAG).captureToImage()
  }
}
