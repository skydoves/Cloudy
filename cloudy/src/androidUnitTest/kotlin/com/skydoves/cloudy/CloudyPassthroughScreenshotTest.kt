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
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Permanent passthrough (`Modifier.cloudy(radius)`) golden set across a sweep of blur radii.
 *
 * Where [CloudyScreenshotSmokeTest] is the minimal gate (does the blur render radius-sensitively at
 * all?), this is the regression set: four goldens at radii 0/8/16/24 over the same synthetic hard-
 * edged-square fixture. The gate test here additionally asserts the perturbation is *ordered* --
 * a larger radius perturbs more pixels than a smaller one -- which catches a class of regression
 * (e.g. a clamped/ignored radius) that a single radius0-vs-radius24 check would miss.
 *
 * Determinism: `@GraphicsMode(NATIVE)` + `robolectric.pixelCopyRenderMode=hardware` (build.gradle),
 * fixed size/colors, no text/image/network/animation, no `Thread.sleep`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
internal class CloudyPassthroughScreenshotTest {

  @Test
  fun a_record_passthrough_radius0() {
    captureCloudyGolden("passthrough_radius0.png") { BlurSquareFixture(radius = 0) }
  }

  @Test
  fun b_record_passthrough_radius8() {
    captureCloudyGolden("passthrough_radius8.png") { BlurSquareFixture(radius = 8) }
  }

  @Test
  fun c_record_passthrough_radius16() {
    captureCloudyGolden("passthrough_radius16.png") { BlurSquareFixture(radius = 16) }
  }

  @Test
  fun d_record_passthrough_radius24() {
    captureCloudyGolden("passthrough_radius24.png") { BlurSquareFixture(radius = 24) }
  }

  /**
   * GATE: all four goldens are non-blank, and the pixel perturbation relative to the sharp
   * `radius0` baseline grows with radius (radius24 diff > radius8 diff). This proves the radius
   * actually feeds the RenderEffect rather than being clamped/ignored.
   */
  @Test
  fun e_gate_radius_perturbation_is_ordered_and_non_blank() {
    val r0 = readGolden("passthrough_radius0.png")
    val r8 = readGolden("passthrough_radius8.png")
    val r16 = readGolden("passthrough_radius16.png")
    val r24 = readGolden("passthrough_radius24.png")

    for ((name, img) in listOf("r0" to r0, "r8" to r8, "r16" to r16, "r24" to r24)) {
      assertTrue("passthrough $name is blank (all one color)", distinctColors(img) >= 2)
    }

    val d8 = pixelDiffRatio(r0, r8)
    val d16 = pixelDiffRatio(r0, r16)
    val d24 = pixelDiffRatio(r0, r24)

    // radius0 vs itself is identically zero; every blurred radius must perturb something.
    assertTrue("radius8 did not perturb radius0 baseline (d8=$d8)", d8 > 0f)

    // Monotonic-ish: more radius => more perturbation. Strict between the extremes (8 vs 24) to
    // avoid flaking on near-ties between adjacent steps, while still proving radius-scaling.
    assertTrue(
      "perturbation is not radius-ordered: d8=$d8, d16=$d16, d24=$d24 " +
        "(expected d24 > d8, i.e. larger radius perturbs more)",
      d24 > d8,
    )
    assertTrue(
      "perturbation is not monotonic across the sweep: d8=$d8, d16=$d16, d24=$d24",
      d16 >= d8 && d24 >= d16,
    )
  }
}
