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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `Modifier.liquidGlass` golden (API 33 RuntimeShader/AGSL path).
 *
 * ## DEFERRED TO PHASE-2 (EMULATOR) -- RuntimeShader is a no-op under Robolectric host capture.
 *
 * The Android liquid-glass effect compiles an AGSL `RuntimeShader` and wraps it in a
 * `RenderEffect` (`createRuntimeShaderEffect`, API 33+). Empirically verified during PR-5
 * development that this path does NOT execute under Robolectric 4.16 native graphics +
 * `pixelCopyRenderMode=hardware`:
 *  - The captured golden was **byte-identical** to the same gradient backdrop rendered with NO
 *    `liquidGlass` modifier at all (0.0% pixel diff).
 *  - Even with extreme parameters (refraction=1.0, curve=1.0, dispersion=0.5, saturation=2.0,
 *    contrast=2.0, edge=0.5) the output was STILL byte-identical to the plain gradient -- the
 *    shader/RenderEffect contributes nothing on host.
 *  - Per-row horizontal luminance variance over a vertical-gradient backdrop was ~0 everywhere,
 *    i.e. no lens refraction/distortion/edge-lighting was present.
 *
 * A naive "non-blank" guard is INSUFFICIENT here: the gradient backdrop itself is colorful, so a
 * blank-effect capture still passes a distinct-color check. The effect simply requires a real
 * RenderThread / RuntimeShader runtime, which Robolectric does not provide. Validating liquid glass
 * therefore belongs on the Phase-2 emulator (instrumented `captureRoboImage` or androidTest
 * screenshot). The fixture below is kept (compiled, `@Ignore`d) as the Phase-2 spec; its gate
 * deliberately checks for horizontal lens distortion, the signal that was absent on host.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@Ignore(
  "liquidGlass RuntimeShader/AGSL RenderEffect is a no-op under Robolectric host capture: the " +
    "golden is byte-identical to the un-lensed backdrop even with extreme params. Deferred to " +
    "Phase-2 emulator. See class KDoc.",
)
internal class LiquidGlassScreenshotTest {

  @Composable
  private fun LiquidGlassFixture() {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF222244), Color(0xFFEEAA33))))
        .liquidGlass(
          lensCenter = Offset(280f, 280f),
          lensSize = Size(280f, 280f),
        ),
    )
  }

  @Test
  fun a_record_liquidglass_default() {
    captureCloudyGolden("liquidglass_default.png") { LiquidGlassFixture() }
  }

  /**
   * Phase-2 gate: the lens must produce horizontal distortion over a (horizontally uniform)
   * vertical gradient -- i.e. some rows must vary across x. On host this was ~0 (no effect).
   */
  @Test
  fun b_gate_liquidglass_lens_distorts_backdrop() {
    val img = readGolden("liquidglass_default.png")
    assertTrue("liquidglass golden is empty", img.width > 0 && img.height > 0)

    fun lum(p: Int) =
      0.299 * (p ushr 16 and 0xFF) + 0.587 * (p ushr 8 and 0xFF) + 0.114 * (p and 0xFF)

    var maxRowXVar = 0.0
    for (y in 0 until img.height step 2) {
      var sum = 0.0
      var sumSq = 0.0
      var n = 0
      for (x in 0 until img.width step 2) {
        val l = lum(img.getRGB(x, y))
        sum += l
        sumSq += l * l
        n++
      }
      val mean = sum / n
      val variance = sumSq / n - mean * mean
      if (variance > maxRowXVar) maxRowXVar = variance
    }
    assertTrue(
      "no horizontal lens distortion (maxRowXVar=$maxRowXVar): liquidGlass did not render -- a " +
        "vertical gradient with a working lens must vary across x in the lens band",
      maxRowXVar > 5.0,
    )
  }
}
