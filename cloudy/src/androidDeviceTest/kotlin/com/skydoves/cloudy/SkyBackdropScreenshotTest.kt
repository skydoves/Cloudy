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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Ignore
import org.junit.Test

/**
 * Backdrop-blur (`Modifier.sky` + `Modifier.cloudy(sky = ...)`) screenshot spec -- PHASE-2.
 *
 * ## Why this lives in androidInstrumentedTest (not androidUnitTest)
 *
 * The backdrop path is GPU/RenderThread-dependent: `Modifier.sky` is a
 * [androidx.compose.ui.node.DrawModifierNode] that, during its own draw pass, records the container
 * subtree into a [androidx.compose.ui.graphics.layer.GraphicsLayer] ([Sky.backgroundLayer]) and the
 * descendant overlay re-samples and blurs that layer (two-pass capture gated by `Sky.isCapturing`,
 * per issue #112). Per the repo's `.coderabbit.yaml`, androidUnitTest tests must NOT depend on a
 * real GPU/RenderEffect, so this spec belongs on an emulator, not the Robolectric host.
 *
 * ## Verified host limitation (why it must be on an emulator)
 *
 * During PR-3 development on Robolectric 4.16 native graphics + `pixelCopyRenderMode=hardware` (via
 * roborazzi-compose's single-shot `captureRoboImage(content)`), this path produced NO blurred
 * overlay:
 *  - With radius 0/20 and tint transparent/white the captures were **byte-identical** -- radius and
 *    tint had zero pixel effect.
 *  - The card region showed the raw, *unblurred* backdrop (sharp stripes passed straight through;
 *    high-frequency vertical contrast was identical between radius0 and radius20).
 *  - A probe confirmed the root cause: after capture, `Sky.backgroundLayer == null` and
 *    `Sky.contentVersion == 0`, and `onStateChanged` never fired -- i.e. `SkyModifierNode.draw()`
 *    (which sets `backgroundLayer` / increments `contentVersion`) **never executes** under the host
 *    capture pipeline. (Passthrough RenderEffect renders fine on the same host because it is a plain
 *    `graphicsLayer { renderEffect }` in the normal draw tree, not a self-recording GraphicsLayer
 *    capture.)
 *
 * ## Phase-2 implementation note
 *
 * The `BackdropFixture` below is real Compose and is the ready-to-run spec. Implement the assertion
 * on the emulator with an instrumented screenshot tool (e.g. Dropshots `assertSnapshot`, or
 * Roborazzi's instrumented `captureRoboImage`/`RoborazziRule`) reading pixels via
 * `android.graphics.Bitmap` -- NOT `java.awt`/`javax.imageio`, which do not exist on Dalvik. Gate
 * idea: blur must reduce the backdrop's high-frequency vertical contrast inside the card, and tint
 * must change the result.
 */
@Ignore(
  "Phase-2 emulator spec: backdrop (Modifier.sky) GraphicsLayer capture needs a real " +
    "GPU/RenderThread; it does not render under Robolectric host capture. See class KDoc.",
)
internal class SkyBackdropScreenshotTest {

  private val surfaceDp = 240
  private val cardWidthDp = 120
  private val cardHeightDp = 80

  /**
   * 240dp sky container: gradient + sharp-stripe backdrop with a centered 120x80dp rounded card
   * that blurs the backdrop behind it. A high-frequency stripe pattern is used (not a smooth
   * gradient) so that blur is measurably distinguishable from no-blur.
   */
  @Composable
  private fun BackdropFixture(radius: Int, tint: Color) {
    val sky = rememberSky()
    Box(
      modifier = Modifier
        .size(surfaceDp.dp)
        .sky(sky),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(listOf(Color(0xFF222244), Color(0xFFEEAA33)))),
      )
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stripe = size.height / 24f
        var y = 0f
        var on = true
        while (y < size.height) {
          if (on) {
            drawRect(
              color = Color.White.copy(alpha = 0.65f),
              topLeft = Offset(0f, y),
              size = Size(size.width, stripe),
            )
          }
          y += stripe
          on = !on
        }
      }
      Box(
        modifier = Modifier
          .size(width = cardWidthDp.dp, height = cardHeightDp.dp)
          .clip(RoundedCornerShape(16.dp))
          .cloudy(sky = sky, radius = radius, tint = tint),
      )
    }
  }

  @Test
  @Ignore("Phase-2 emulator: capture BackdropFixture(radius = 0) and assert it is the reference.")
  fun backdrop_sharp() {
    // Phase-2: render BackdropFixture(radius = 0, tint = Transparent) and snapshot on emulator.
  }

  @Test
  @Ignore(
    "Phase-2 emulator: capture BackdropFixture(radius = 20) and assert it blurs the backdrop.",
  )
  fun backdrop_uniform() {
    // Phase-2: render BackdropFixture(radius = 20, tint = Transparent) and snapshot on emulator.
  }

  @Test
  @Ignore("Phase-2 emulator: capture BackdropFixture(radius = 20, tint) and assert tint differs.")
  fun backdrop_tint() {
    // Phase-2: render BackdropFixture(radius = 20, tint = White@0.2) and snapshot on emulator.
  }
}
