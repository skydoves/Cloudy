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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for API version branching logic in the liquid glass modifier.
 *
 * These tests verify that:
 * - API 33+ should use RuntimeShader path (full effect)
 * - API 32 and below should use Fallback path (tint + edge + shape)
 *
 * Note: These tests verify the branching logic condition, not the actual
 * Compose modifier behavior. Full Compose tests require instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
internal class LiquidGlassModifierTest {

  /**
   * Verifies API version check condition for API 33 (RuntimeShader path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `API 33 should satisfy RuntimeShader condition`() {
    val shouldUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    assertTrue(
      "API 33 should satisfy RuntimeShader condition (SDK_INT >= TIRAMISU)",
      shouldUseRuntimeShader,
    )
  }

  /**
   * Verifies API version check condition for API 34 (RuntimeShader path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
  fun `API 34 should satisfy RuntimeShader condition`() {
    val shouldUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    assertTrue(
      "API 34 should satisfy RuntimeShader condition",
      shouldUseRuntimeShader,
    )
  }

  /**
   * Verifies API version check condition for API 32 (Fallback path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S_V2])
  fun `API 32 should NOT satisfy RuntimeShader condition`() {
    val shouldUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    assertFalse(
      "API 32 should NOT satisfy RuntimeShader condition (SDK_INT < TIRAMISU)",
      shouldUseRuntimeShader,
    )
  }

  /**
   * Verifies API version check condition for API 31 (Fallback path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `API 31 should NOT satisfy RuntimeShader condition`() {
    val shouldUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    assertFalse(
      "API 31 should NOT satisfy RuntimeShader condition",
      shouldUseRuntimeShader,
    )
  }

  /**
   * Verifies API version check condition for API 30 (Fallback path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `API 30 should NOT satisfy RuntimeShader condition`() {
    val shouldUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    assertFalse(
      "API 30 should NOT satisfy RuntimeShader condition",
      shouldUseRuntimeShader,
    )
  }

  /**
   * Verifies API version check condition for API 29 (Fallback path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.Q])
  fun `API 29 should NOT satisfy RuntimeShader condition`() {
    val shouldUseRuntimeShader = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    assertFalse(
      "API 29 should NOT satisfy RuntimeShader condition",
      shouldUseRuntimeShader,
    )
  }

  /**
   * Verifies that MIN_ANDROID_API_FULL matches the RuntimeShader requirement.
   */
  @Test
  fun `MIN_ANDROID_API_FULL should be API 33`() {
    assertTrue(
      "MIN_ANDROID_API_FULL should be 33 (TIRAMISU)",
      LiquidGlassDefaults.MIN_ANDROID_API_FULL == Build.VERSION_CODES.TIRAMISU,
    )
  }

  /**
   * Verifies that MIN_ANDROID_API_FALLBACK matches minimum supported API.
   */
  @Test
  fun `MIN_ANDROID_API_FALLBACK should be API 23`() {
    assertTrue(
      "MIN_ANDROID_API_FALLBACK should be 23 (M)",
      LiquidGlassDefaults.MIN_ANDROID_API_FALLBACK == Build.VERSION_CODES.M,
    )
  }
}
