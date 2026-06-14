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

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke / gate screenshot test for `Modifier.cloudy`.
 *
 * This is PR-1 of the screenshot-testing effort: it does NOT yet exercise every Cloudy surface.
 * Its single job is to *prove*, under Cloudy's exact Robolectric 4.16 + Compose 1.10 toolchain,
 * that the host-side RenderEffect blur is captured *radius-sensitively* -- i.e. that a non-blurred
 * (`radius = 0`) golden and a blurred (`radius = 24`) golden are measurably different and neither
 * is blank. If that empirical gate ever fails, every downstream golden in this suite is worthless,
 * so the gate is encoded as an automated assertion rather than left to the human eye.
 *
 * Determinism (PR#71 regressions explicitly avoided):
 * - `@GraphicsMode(NATIVE)` + `robolectric.pixelCopyRenderMode=hardware` (set in build.gradle) so
 *   RenderNode effects (the blur) are actually rasterized rather than skipped.
 * - Fixed-size, fixed-color synthetic shapes. No text, no AsyncImage/network, no animation.
 * - Never `Thread.sleep`; the `roborazzi-compose` capture overload drives the frame clock to idle.
 *   Never captures the demo MainActivity.
 *
 * Method order is fixed so the two recording tests run before the gate, which reads the PNGs they
 * write.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
internal class CloudyScreenshotSmokeTest {

  /**
   * Deterministic synthetic content: a hard-edged white square that the blur diffuses against the
   * surrounding solid-blue [ScreenshotSurface]. The hard edge is what makes the radius0 vs radius24
   * goldens diverge.
   */
  @Composable
  private fun BlurFixture(radius: Int) {
    Box(
      modifier = Modifier
        .size(80.dp)
        .cloudy(radius = radius)
        .background(Color.White),
    )
  }

  /** Records the un-blurred reference golden (`radius = 0` short-circuits the blur). */
  @Test
  fun a_record_smoke_radius0() {
    captureCloudyGolden("smoke_radius0.png") {
      BlurFixture(radius = 0)
    }
  }

  /** Records the blurred golden (`radius = 24` -> RenderEffect blur on API 34). */
  @Test
  fun b_record_smoke_radius24() {
    captureCloudyGolden("smoke_radius24.png") {
      BlurFixture(radius = 24)
    }
  }

  /**
   * THE GATE. Reads back the two goldens written by the recording tests and asserts:
   * 1. both are non-blank (more than one distinct color -- catches all-one-color hardware-capture
   *    failures), and
   * 2. they diverge above a conservative threshold (proves the RenderEffect blur is radius-sensitive
   *    under this toolchain, not a no-op).
   *
   * This is the whole point of PR-1: a real, automated proof rather than a human eyeball check.
   */
  @Test
  fun c_gate_radius0_and_radius24_goldens_differ_and_are_non_blank() {
    val sharp = readGolden("smoke_radius0.png")
    val blurred = readGolden("smoke_radius24.png")

    assertTrue(
      "goldens must share dimensions to be comparable: " +
        "${sharp.width}x${sharp.height} vs ${blurred.width}x${blurred.height}",
      sharp.width == blurred.width && sharp.height == blurred.height,
    )
    assertTrue("golden must be non-empty", sharp.width > 0 && sharp.height > 0)

    assertTrue("smoke_radius0.png is blank (all one color)", distinctColors(sharp) >= 2)
    assertTrue("smoke_radius24.png is blank (all one color)", distinctColors(blurred) >= 2)

    val diffRatio = pixelDiffRatio(sharp, blurred)

    // A 24px blur on an 80dp white square against blue MUST perturb a substantial pixel band.
    // 0.5% is a deliberately conservative floor; the measured ratio is surfaced in the message so
    // the magnitude is visible in build output even on success.
    assertTrue(
      "smoke_radius0.png and smoke_radius24.png are effectively identical " +
        "(diffRatio=$diffRatio) -- RenderEffect blur is a no-op under this " +
        "Robolectric/Compose toolchain",
      diffRatio > 0.005f,
    )
  }
}
