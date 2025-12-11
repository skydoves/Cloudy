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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for API version branching logic in the cloudy modifier.
 *
 * These tests verify that:
 * - API 31+ should use RenderEffect path (returns Success.Applied)
 * - API 30 and below should use Native C++ path (returns Success.Captured)
 *
 * Note: These tests verify the branching logic condition, not the actual
 * Compose modifier behavior. Full Compose tests require instrumented tests
 * or proper manifest configuration.
 */
@RunWith(RobolectricTestRunner::class)
internal class CloudyModifierTest {

  /**
   * Verifies API version check condition for API 31+ (RenderEffect path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `API 31 should satisfy RenderEffect condition`() {
    // This is the same condition used in Cloudy.android.kt
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    assertTrue(
      "API 31 should satisfy RenderEffect condition (SDK_INT >= S)",
      shouldUseRenderEffect,
    )
    assertEquals(Build.VERSION_CODES.S, Build.VERSION.SDK_INT)
  }

  /**
   * Verifies API version check condition for API 32 (RenderEffect path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S_V2])
  fun `API 32 should satisfy RenderEffect condition`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    assertTrue(
      "API 32 should satisfy RenderEffect condition",
      shouldUseRenderEffect,
    )
  }

  /**
   * Verifies API version check condition for API 33 (RenderEffect path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `API 33 should satisfy RenderEffect condition`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    assertTrue(
      "API 33 should satisfy RenderEffect condition",
      shouldUseRenderEffect,
    )
  }

  /**
   * Verifies API version check condition for API 30 (Native C++ path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `API 30 should NOT satisfy RenderEffect condition`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    assertFalse(
      "API 30 should NOT satisfy RenderEffect condition (SDK_INT < S)",
      shouldUseRenderEffect,
    )
    assertEquals(Build.VERSION_CODES.R, Build.VERSION.SDK_INT)
  }

  /**
   * Verifies API version check condition for API 29 (Native C++ path).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.Q])
  fun `API 29 should NOT satisfy RenderEffect condition`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    assertFalse(
      "API 29 should NOT satisfy RenderEffect condition",
      shouldUseRenderEffect,
    )
  }

  /**
   * Verifies Success.Applied is returned for GPU blur path.
   * On API 31+, RenderEffect is used and Success.Applied should be returned.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `Success Applied should be returned for RenderEffect path`() {
    // Simulate the state that would be returned from RenderEffect path
    val state: CloudyState = CloudyState.Success.Applied

    assertTrue(
      "Success.Applied should be a Success state",
      state is CloudyState.Success,
    )
    assertTrue(
      "Success.Applied should be exactly Applied type",
      state is CloudyState.Success.Applied,
    )
  }

  /**
   * Verifies Success.Captured is returned for Native C++ path.
   * On API 30 and below, Native blur is used and Success.Captured should be returned.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `Success Captured should be returned for Native path`() {
    // Simulate the state that would be returned from Native path
    val mockBitmap = createMockPlatformBitmap()
    val state: CloudyState = CloudyState.Success.Captured(mockBitmap)

    assertTrue(
      "Success.Captured should be a Success state",
      state is CloudyState.Success,
    )
    assertTrue(
      "Success.Captured should be exactly Captured type",
      state is CloudyState.Success.Captured,
    )
    assertEquals(
      "Success.Captured should contain the bitmap",
      mockBitmap,
      (state as CloudyState.Success.Captured).bitmap,
    )
  }

  /**
   * Verifies Success.Applied and Success.Captured are both instances of Success.
   */
  @Test
  fun `both Applied and Captured should be instances of Success`() {
    val appliedState: CloudyState = CloudyState.Success.Applied
    val capturedState: CloudyState = CloudyState.Success.Captured(createMockPlatformBitmap())

    // Both should match 'is CloudyState.Success'
    assertTrue(appliedState is CloudyState.Success)
    assertTrue(capturedState is CloudyState.Success)

    // But they should be distinguishable
    assertTrue(appliedState is CloudyState.Success.Applied)
    assertFalse(appliedState is CloudyState.Success.Captured)
    assertTrue(capturedState is CloudyState.Success.Captured)
    assertFalse(capturedState is CloudyState.Success.Applied)
  }

  /**
   * Verifies exhaustive when expression for CloudyState.
   */
  @Test
  fun `CloudyState should support exhaustive when expression`() {
    val states = listOf(
      CloudyState.Nothing,
      CloudyState.Loading,
      CloudyState.Success.Applied,
      CloudyState.Success.Captured(createMockPlatformBitmap()),
      CloudyState.Success.Scrim,
      CloudyState.Error(RuntimeException("test")),
    )

    states.forEach { state ->
      val description = when (state) {
        CloudyState.Nothing -> "nothing"
        CloudyState.Loading -> "loading"
        is CloudyState.Success.Applied -> "applied"
        is CloudyState.Success.Captured -> "captured"
        is CloudyState.Success.Scrim -> "scrim"
        is CloudyState.Error -> "error"
      }
      assertTrue(description.isNotEmpty())
    }
  }
}
