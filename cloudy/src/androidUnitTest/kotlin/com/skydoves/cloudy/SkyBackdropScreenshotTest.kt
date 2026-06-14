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
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Backdrop-blur (`Modifier.sky` + `Modifier.cloudy(sky = ...)`) golden set.
 *
 * ## DEFERRED TO PHASE-2 (EMULATOR) -- host capture does not drive the Sky draw pass.
 *
 * The backdrop path works fundamentally differently from passthrough (PR-1/PR-2): `Modifier.sky`
 * is a [androidx.compose.ui.node.DrawModifierNode] that, *during its own draw pass*, records the
 * container subtree into a [androidx.compose.ui.graphics.layer.GraphicsLayer]
 * ([Sky.backgroundLayer]) and bumps [Sky] `contentVersion`; the descendant overlay then re-samples
 * and blurs that layer (two-pass capture gated by `Sky.isCapturing`, per issue #112).
 *
 * Under Cloudy's host toolchain (Robolectric 4.16 native graphics + `pixelCopyRenderMode=hardware`,
 * via roborazzi-compose's single-shot `captureRoboImage(content)`), this path produces NO blurred
 * overlay. Empirically verified during PR-3 development:
 *  - With radius 0 / 20 and tint transparent / white@0.2, all three goldens were **byte-identical**
 *    -- radius and tint had zero pixel effect.
 *  - The card region showed the raw, *unblurred* backdrop (sharp stripes passed straight through;
 *    `verticalContrast` was identical between radius0 and radius20, to 13 significant figures).
 *  - A probe confirmed the root cause: after capture, `Sky.backgroundLayer == null` and
 *    `Sky.contentVersion == 0`, and `onStateChanged` never fired. I.e. `SkyModifierNode.draw()`
 *    (which sets `backgroundLayer` / increments `contentVersion`) **never executes** in the host
 *    capture -- the capture pipeline does not drive the sky container's DrawModifierNode draw.
 *
 * This is a backdrop-specific host limitation, NOT a Cloudy bug and NOT a config miss (passthrough
 * RenderEffect renders fine on the same host because it is a plain `graphicsLayer { renderEffect }`
 * inside the normal draw tree, not a self-recording GraphicsLayer capture). Validating the backdrop
 * visually requires a real RenderThread, so it belongs on the Phase-2 emulator
 * (instrumented `captureRoboImage` against `pixelCopyRenderMode=hardware` on-device, or an
 * androidTest screenshot). The fixture and gate below are kept (compiled, `@Ignore`d) as the
 * ready-to-run Phase-2 spec.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore(
  "Backdrop (Modifier.sky) GraphicsLayer capture does not render under Robolectric host capture: " +
    "SkyModifierNode.draw never runs (backgroundLayer stays null), so blur/tint have no pixel " +
    "effect. Deferred to Phase-2 emulator. See class KDoc.",
)
internal class SkyBackdropScreenshotTest {

  private val surfaceDp = 240

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
          .size(width = 120.dp, height = 80.dp)
          .clip(RoundedCornerShape(16.dp))
          .cloudy(sky = sky, radius = radius, tint = tint),
      )
    }
  }

  @Test
  fun a_record_sky_backdrop_sharp() {
    captureCloudyGoldenSized("sky_backdrop_sharp.png", surfaceDp) {
      BackdropFixture(radius = 0, tint = Color.Transparent)
    }
  }

  @Test
  fun b_record_sky_backdrop_uniform() {
    captureCloudyGoldenSized("sky_backdrop_uniform.png", surfaceDp) {
      BackdropFixture(radius = 20, tint = Color.Transparent)
    }
  }

  @Test
  fun c_record_sky_backdrop_tint() {
    captureCloudyGoldenSized("sky_backdrop_tint.png", surfaceDp) {
      BackdropFixture(radius = 20, tint = Color.White.copy(alpha = 0.2f))
    }
  }

  /**
   * Phase-2 gate: blur reduces the backdrop's high-frequency vertical contrast inside the card, and
   * tint changes the result. (Currently `@Ignore`d at the class level -- see class KDoc.)
   */
  @Test
  fun d_gate_backdrop_blurs_and_tint_differs() {
    val sharp = readGolden("sky_backdrop_sharp.png")
    val uniform = readGolden("sky_backdrop_uniform.png")
    val tinted = readGolden("sky_backdrop_tint.png")

    assertTrue(
      "backdrop goldens must share dimensions",
      sharp.width == uniform.width && uniform.width == tinted.width,
    )

    val sharpColors = distinctColorsInCardRegion(sharp)
    assertTrue(
      "card region shows only $sharpColors color(s): backdrop did not render through the card",
      sharpColors >= 4,
    )

    val sharpContrast = verticalContrastInCardRegion(sharp)
    val blurContrast = verticalContrastInCardRegion(uniform)
    assertTrue(
      "blur did not reduce backdrop contrast (sharp=$sharpContrast, blur=$blurContrast)",
      blurContrast < sharpContrast,
    )

    val tintDiff = pixelDiffRatio(uniform, tinted)
    assertTrue("tint did not change the backdrop (diffRatio=$tintDiff)", tintDiff > 0.01f)
  }

  // ---- card-region helpers (card is 120x80dp centered in a 240dp surface) ----

  private data class CardRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

  private fun cardRect(width: Int, height: Int): CardRect {
    val cw = (width * 0.18).toInt()
    val ch = (height * 0.12).toInt()
    val cx = width / 2
    val cy = height / 2
    return CardRect(cx - cw, cy - ch, cx + cw, cy + ch)
  }

  private fun distinctColorsInCardRegion(img: java.awt.image.BufferedImage): Int {
    val r = cardRect(img.width, img.height)
    val seen = HashSet<Int>()
    for (y in r.top until r.bottom) {
      for (x in r.left until r.right) {
        seen.add(img.getRGB(x, y))
        if (seen.size >= 64) return seen.size
      }
    }
    return seen.size
  }

  private fun lum(p: Int): Double {
    val rr = p ushr 16 and 0xFF
    val gg = p ushr 8 and 0xFF
    val bb = p and 0xFF
    return 0.299 * rr + 0.587 * gg + 0.114 * bb
  }

  private fun verticalContrastInCardRegion(img: java.awt.image.BufferedImage): Double {
    val r = cardRect(img.width, img.height)
    var sum = 0.0
    var n = 0
    for (y in r.top until r.bottom - 1) {
      for (x in r.left until r.right) {
        sum += kotlin.math.abs(lum(img.getRGB(x, y)) - lum(img.getRGB(x, y + 1)))
        n++
      }
    }
    return if (n == 0) 0.0 else sum / n
  }
}
