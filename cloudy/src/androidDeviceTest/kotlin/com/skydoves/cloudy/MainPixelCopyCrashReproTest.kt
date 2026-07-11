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
 * Regression coverage for the backdrop `captureToImage()` / `PixelCopy` crash: capturing a
 * `Modifier.sky` tree with a descendant `Modifier.cloudy(sky = ...)` backdrop node used to SIGSEGV the
 * RenderThread with an unbounded `prepareTreeImpl` recursion (a cyclic RenderNode graph formed by
 * `drawLayer(sky.backgroundLayer)`). Each variant below reproduced the crash on unmodified `main`; a
 * green run of this class is the proof `BackdropClearBlurMachine`'s rasterized-snapshot fix breaks the
 * cycle.
 */
internal class MainPixelCopyCrashReproTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var radiusState by mutableStateOf(0)
  private var tintState by mutableStateOf(Color.Transparent)

  @Composable
  private fun Fixture() {
    val sky = rememberSky()
    Box(
      modifier = Modifier.testTag("root").size(240.dp).sky(sky),
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
          .testTag("card")
          .cloudy(sky = sky, radius = radiusState, tint = tintState),
      )
    }
  }

  /** A radius > 0 blur capture: the blur layer's `drawLayer(backgroundLayer)` back-edge was the cycle. */
  @Test
  fun rootCapture_singleBlur() {
    composeTestRule.setContent { Fixture() }
    radiusState = 20
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("root").captureToImage()
  }

  /** Capturing radius 0 first, then swapping to a blur and re-capturing (re-keys the cached effect). */
  @Test
  fun rootCapture_swapZeroThenBlur() {
    composeTestRule.setContent { Fixture() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("root").captureToImage()
    radiusState = 20
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("root").captureToImage()
  }

  /** Radius 0 + a non-transparent tint: the over-draw composites the sampled region back into the tree. */
  @Test
  fun rootCapture_tintOnly() {
    composeTestRule.setContent { Fixture() }
    tintState = Color(0x66FFFFFF)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("root").captureToImage()
  }

  /** Capturing the blurred card sub-node directly (an isolated PixelCopy tree that used to cycle). */
  @Test
  fun cardSubNodeCapture_blur() {
    composeTestRule.setContent { Fixture() }
    radiusState = 20
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("card").captureToImage()
  }
}
