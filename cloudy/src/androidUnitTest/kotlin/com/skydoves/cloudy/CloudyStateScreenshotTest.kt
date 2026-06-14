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
 * Golden set proving `Modifier.cloudy(enabled = ...)` gates the blur.
 *
 * `enabled = false` returns the receiver modifier unchanged (sharp); `enabled = true` applies the
 * RenderEffect blur. The gate asserts the two goldens differ -- i.e. the `enabled` flag is honored
 * and not silently ignored.
 *
 * Determinism: `@GraphicsMode(NATIVE)`, fixed size/colors, no text/image/network/animation, no
 * `Thread.sleep`. Same synthetic fixture as the passthrough suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
internal class CloudyStateScreenshotTest {

  @Composable
  private fun StateFixture(enabled: Boolean) {
    Box(
      modifier = Modifier
        .size(80.dp)
        .cloudy(radius = 20, enabled = enabled)
        .background(Color.White),
    )
  }

  @Test
  fun a_record_state_disabled() {
    captureCloudyGolden("state_disabled.png") { StateFixture(enabled = false) }
  }

  @Test
  fun b_record_state_enabled() {
    captureCloudyGolden("state_enabled.png") { StateFixture(enabled = true) }
  }

  /**
   * GATE: enabled (blurred) and disabled (sharp) goldens differ and are both non-blank. The
   * disabled golden must also match a pure hard-edged square (no blur diffusion), which we proxy
   * by requiring it has FEWER distinct colors than the blurred one.
   */
  @Test
  fun c_gate_enabled_differs_from_disabled() {
    val disabled = readGolden("state_disabled.png")
    val enabled = readGolden("state_enabled.png")

    assertTrue(
      "goldens must share dimensions",
      disabled.width == enabled.width && disabled.height == enabled.height,
    )
    assertTrue("state_disabled is blank", distinctColors(disabled) >= 2)
    assertTrue("state_enabled is blank", distinctColors(enabled) >= 2)

    val diff = pixelDiffRatio(disabled, enabled)
    assertTrue(
      "enabled (blur) and disabled (sharp) renders are effectively identical (diffRatio=$diff): " +
        "the enabled flag is not gating the blur",
      diff > 0.005f,
    )

    // Disabled is a hard-edged square (2 colors); enabled diffuses the edge into many colors.
    val disabledColors = distinctColors(disabled, cap = 8)
    val enabledColors = distinctColors(enabled, cap = 8)
    assertTrue(
      "expected disabled ($disabledColors) to have fewer colors than enabled ($enabledColors) " +
        "(blur introduces a gradient edge)",
      enabledColors > disabledColors,
    )
  }
}
