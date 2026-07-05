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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Ignore
import org.junit.Test

/**
 * `Modifier.liquidGlass` screenshot spec (API 33 RuntimeShader/AGSL path) -- PHASE-2.
 *
 * ## Why this lives in androidInstrumentedTest (not androidUnitTest)
 *
 * The Android liquid-glass effect compiles an AGSL `RuntimeShader` and wraps it in a `RenderEffect`
 * (`createRuntimeShaderEffect`, API 33+) -- inherently GPU-dependent. Per the repo's
 * `.coderabbit.yaml`, androidUnitTest tests must NOT depend on a real GPU/RenderEffect, so this
 * spec belongs on an emulator, not the Robolectric host.
 *
 * ## Verified host limitation (why it must be on an emulator)
 *
 * During PR-5 development on Robolectric 4.16 native graphics + `pixelCopyRenderMode=hardware`, this
 * path was a complete no-op:
 *  - The captured output was **byte-identical** to the same gradient backdrop rendered with NO
 *    `liquidGlass` modifier at all (0.0% pixel diff).
 *  - Even extreme parameters (refraction=1.0, curve=1.0, dispersion=0.5, saturation=2.0,
 *    contrast=2.0, edge=0.5) produced output STILL byte-identical to the plain gradient.
 *  - Per-row horizontal luminance variance over a vertical-gradient backdrop was ~0 everywhere --
 *    no lens refraction/distortion/edge-lighting was present.
 *
 * A naive "non-blank" check is INSUFFICIENT here: the gradient backdrop itself is colorful, so a
 * blank-effect capture still passes a distinct-color check. The effect requires a real RuntimeShader
 * runtime, which Robolectric does not provide.
 *
 * ## Phase-2 implementation note
 *
 * The `LiquidGlassFixture` below is the ready-to-run spec. Implement the assertion on the emulator
 * with an instrumented screenshot tool reading pixels via `android.graphics.Bitmap` (NOT
 * `java.awt`/`javax.imageio`). Gate idea: over a (horizontally uniform) vertical gradient, a working
 * lens must introduce horizontal variation in the lens band -- the signal that was absent on host.
 */
@Ignore(
  "Phase-2 emulator spec: liquidGlass RuntimeShader/AGSL RenderEffect needs a real GPU; it is a " +
    "no-op under Robolectric host capture (byte-identical to the un-lensed backdrop). See KDoc.",
)
internal class LiquidGlassScreenshotTest {

  // Fixed surface so the absolute lens coordinates below resolve to a known, fully-visible region
  // during Phase-2 emulator capture (an unconstrained fillMaxSize() makes the lens placement depend
  // on the ambient capture size). 280dp comfortably contains the 280px-centered, 280px lens.
  private val surfaceDp = 280

  /** Fixed-size gradient backdrop with a liquid-glass lens centered over it. */
  @Composable
  private fun LiquidGlassFixture() {
    Box(
      modifier = Modifier
        .size(surfaceDp.dp)
        .background(Brush.verticalGradient(listOf(Color(0xFF222244), Color(0xFFEEAA33))))
        .liquidGlass(
          lensCenter = Offset(280f, 280f),
          lensSize = Size(280f, 280f),
        ),
    )
  }

  @Test
  @Ignore("Phase-2 emulator: capture LiquidGlassFixture and assert lens distorts the backdrop.")
  fun liquidglass_default() {
    // Phase-2: render LiquidGlassFixture() and snapshot on emulator; assert horizontal distortion.
  }
}
