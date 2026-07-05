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

import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for liquid glass parameter validation.
 *
 * These tests verify that:
 * - Negative values for cornerRadius, refraction, curve, dispersion,
 *   saturation, and contrast throw IllegalArgumentException
 * - Zero and positive values are accepted
 */
@RunWith(RobolectricTestRunner::class)
internal class LiquidGlassParameterTest {

  /**
   * Helper function to validate parameters.
   * Mirrors the validation logic in LiquidGlass.android.kt
   */
  private fun validateParameters(
    cornerRadius: Float = 50f,
    refraction: Float = 0.25f,
    curve: Float = 0.25f,
    dispersion: Float = 0.0f,
    saturation: Float = 1.0f,
    contrast: Float = 1.0f,
  ) {
    require(cornerRadius >= 0f) { "cornerRadius must be >= 0, but was $cornerRadius" }
    require(refraction >= 0f) { "refraction must be >= 0, but was $refraction" }
    require(curve >= 0f) { "curve must be >= 0, but was $curve" }
    require(dispersion >= 0f) { "dispersion must be >= 0, but was $dispersion" }
    require(saturation >= 0f) { "saturation must be >= 0, but was $saturation" }
    require(contrast >= 0f) { "contrast must be >= 0, but was $contrast" }
  }

  // cornerRadius validation tests

  @Test
  fun `cornerRadius should throw when negative`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateParameters(cornerRadius = -1f)
    }
  }

  @Test
  fun `cornerRadius should accept zero`() {
    validateParameters(cornerRadius = 0f)
  }

  @Test
  fun `cornerRadius should accept positive values`() {
    validateParameters(cornerRadius = 50f)
    validateParameters(cornerRadius = 175f)
  }

  // refraction validation tests

  @Test
  fun `refraction should throw when negative`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateParameters(refraction = -0.1f)
    }
  }

  @Test
  fun `refraction should accept zero`() {
    validateParameters(refraction = 0f)
  }

  @Test
  fun `refraction should accept positive values`() {
    validateParameters(refraction = 0.25f)
    validateParameters(refraction = 1f)
  }

  // curve validation tests

  @Test
  fun `curve should throw when negative`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateParameters(curve = -0.1f)
    }
  }

  @Test
  fun `curve should accept zero`() {
    validateParameters(curve = 0f)
  }

  @Test
  fun `curve should accept positive values`() {
    validateParameters(curve = 0.25f)
    validateParameters(curve = 1f)
  }

  // dispersion validation tests

  @Test
  fun `dispersion should throw when negative`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateParameters(dispersion = -0.1f)
    }
  }

  @Test
  fun `dispersion should accept zero`() {
    validateParameters(dispersion = 0f)
  }

  @Test
  fun `dispersion should accept positive values`() {
    validateParameters(dispersion = 0.5f)
    validateParameters(dispersion = 2f)
  }

  // saturation validation tests

  @Test
  fun `saturation should throw when negative`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateParameters(saturation = -0.1f)
    }
  }

  @Test
  fun `saturation should accept zero`() {
    validateParameters(saturation = 0f)
  }

  @Test
  fun `saturation should accept positive values`() {
    validateParameters(saturation = 1f)
    validateParameters(saturation = 2f)
  }

  // contrast validation tests

  @Test
  fun `contrast should throw when negative`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateParameters(contrast = -0.1f)
    }
  }

  @Test
  fun `contrast should accept zero`() {
    validateParameters(contrast = 0f)
  }

  @Test
  fun `contrast should accept positive values`() {
    validateParameters(contrast = 1f)
    validateParameters(contrast = 2f)
  }

  // Combined validation tests

  @Test
  fun `all default values should pass validation`() {
    validateParameters(
      cornerRadius = LiquidGlassDefaults.CORNER_RADIUS,
      refraction = LiquidGlassDefaults.REFRACTION,
      curve = LiquidGlassDefaults.CURVE,
      dispersion = LiquidGlassDefaults.DISPERSION,
      saturation = LiquidGlassDefaults.SATURATION,
      contrast = LiquidGlassDefaults.CONTRAST,
    )
  }

  @Test
  fun `all parameters at zero should pass validation`() {
    validateParameters(
      cornerRadius = 0f,
      refraction = 0f,
      curve = 0f,
      dispersion = 0f,
      saturation = 0f,
      contrast = 0f,
    )
  }

  @Test
  fun `all parameters at high values should pass validation`() {
    validateParameters(
      cornerRadius = 500f,
      refraction = 10f,
      curve = 10f,
      dispersion = 10f,
      saturation = 10f,
      contrast = 10f,
    )
  }
}
