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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Regression for issue #112: a backdrop-sampling node (`Modifier.cloudy(sky)` / `Modifier.mirage(sky)`)
 * placed as a DESCENDANT of the `Modifier.sky(sky)` recorder used to build a cyclic `RenderNode` graph
 * — the overlay's blur layer sampled the backdrop layer that was, in turn, recording the overlay — and
 * `prepareTreeImpl` overflowed the RenderThread stack (SIGSEGV). The fix guards every backdrop node's
 * draw with `Sky.isCapturing`, so the overlay draws nothing while the recorder is capturing.
 *
 * This is a does-not-crash test: it composes the exact cyclic layout (sampling node inside the sky
 * subtree), animates the sampled node so the recorder re-captures across several frames, and asserts
 * the composable survives and stays displayed. Both backdrop overloads are exercised.
 */
internal class Issue112RegressionTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun cloudyBackdropInsideSkySubtree_reCapturesAcrossFrames_withoutCrash() {
    assertSurvivesFrames { sky ->
      Box(modifier = Modifier.size(120.dp, 80.dp).cloudy(sky = sky, radius = 20))
    }
  }

  @Test
  fun mirageBackdropInsideSkySubtree_reCapturesAcrossFrames_withoutCrash() {
    assertSurvivesFrames { sky ->
      Box(
        modifier = Modifier
          .size(120.dp, 80.dp)
          .mirage(sky = sky) { filter(MirageShaders.Duotone) },
      )
    }
  }

  /**
   * Composes [samplingNode] as a descendant of `Modifier.sky`, then animates its position so the sky
   * recorder re-records each frame (the path that hit the cyclic graph), advancing frames the whole
   * time. Reaching the final assertion means the RenderThread never crashed.
   */
  private fun assertSurvivesFrames(samplingNode: @Composable (Sky) -> Unit) {
    composeTestRule.setContent {
      val sky = rememberSky()
      var moved by remember { mutableStateOf(false) }
      // Drive a real position change so the backdrop is re-captured over multiple frames (the recorder
      // only re-records when its draw re-runs; moving a descendant repaints the sky subtree).
      val dx by animateDpAsState(if (moved) 24.dp else 0.dp, label = "offset")
      // Kick the animation off the very first frame.
      androidx.compose.runtime.LaunchedEffect(Unit) { moved = true }

      Box(
        modifier = Modifier
          .testTag("root")
          .size(240.dp)
          .sky(sky),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier
            .size(240.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF222244), Color(0xFFEEAA33)))),
        )
        Box(modifier = Modifier.offset(x = dx)) {
          samplingNode(sky)
        }
      }
    }

    // The default clock auto-advances, so waitForIdle pumps every animation frame — each of which
    // re-records the backdrop. A cyclic RenderNode graph would overflow the RenderThread stack during
    // one of those prepareTree passes; reaching the assertion means the guard held.
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("root").assertExists()
  }
}
