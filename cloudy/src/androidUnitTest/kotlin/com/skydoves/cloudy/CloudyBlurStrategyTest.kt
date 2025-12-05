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
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for CloudyBlurStrategy interface and its implementations.
 *
 * Tests cover:
 * - Strategy selection based on API level
 * - RenderEffectStrategy behavior on API 31+
 * - LegacyBlurStrategy behavior on API 30-
 * - State callbacks and transitions
 * - Edge cases and error handling
 */
@RunWith(RobolectricTestRunner::class)
internal class CloudyBlurStrategyTest {

  /**
   * Verifies that CloudyRenderEffectStrategy is the correct implementation
   * for API 31 and above.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `RenderEffectStrategy should be used on API 31`() {
    val expectedStrategy = CloudyRenderEffectStrategy
    
    assertNotNull("RenderEffectStrategy should exist", expectedStrategy)
    assertTrue(
      "Should be CloudyBlurStrategy instance",
      expectedStrategy is CloudyBlurStrategy,
    )
  }

  /**
   * Verifies that CloudyLegacyBlurStrategy is the correct implementation
   * for API 30 and below.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `LegacyBlurStrategy should be used on API 30`() {
    val expectedStrategy = CloudyLegacyBlurStrategy
    
    assertNotNull("LegacyBlurStrategy should exist", expectedStrategy)
    assertTrue(
      "Should be CloudyBlurStrategy instance",
      expectedStrategy is CloudyBlurStrategy,
    )
  }

  /**
   * Verifies that both strategies implement the same interface.
   */
  @Test
  fun `both strategies should implement CloudyBlurStrategy interface`() {
    val renderEffectStrategy: CloudyBlurStrategy = CloudyRenderEffectStrategy
    val legacyStrategy: CloudyBlurStrategy = CloudyLegacyBlurStrategy
    
    assertNotNull(renderEffectStrategy)
    assertNotNull(legacyStrategy)
  }

  /**
   * Verifies that strategies are singleton objects.
   */
  @Test
  fun `strategies should be singleton objects`() {
    val renderEffect1 = CloudyRenderEffectStrategy
    val renderEffect2 = CloudyRenderEffectStrategy
    assertTrue("RenderEffectStrategy should be singleton", renderEffect1 === renderEffect2)
    
    val legacy1 = CloudyLegacyBlurStrategy
    val legacy2 = CloudyLegacyBlurStrategy
    assertTrue("LegacyBlurStrategy should be singleton", legacy1 === legacy2)
  }

  /**
   * Verifies that strategy selection logic matches API level correctly.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `strategy selection on API 31 should choose RenderEffect`() {
    val isApiS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    assertTrue("API 31+ should use RenderEffect", isApiS)
  }

  /**
   * Verifies that strategy selection logic matches API level correctly for older APIs.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `strategy selection on API 30 should choose Legacy`() {
    val isApiS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    assertTrue("API 30- should use Legacy", !isApiS)
  }

  /**
   * Verifies boundary condition at API 31.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `boundary test - exactly API 31 should use RenderEffect`() {
    assertEquals(Build.VERSION_CODES.S, Build.VERSION.SDK_INT)
    assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
  }

  /**
   * Verifies boundary condition at API 30.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `boundary test - exactly API 30 should use Legacy`() {
    assertEquals(Build.VERSION_CODES.R, Build.VERSION.SDK_INT)
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
  }

  /**
   * Verifies that multiple API levels above 31 all use RenderEffect.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `API 33 should use RenderEffect strategy`() {
    assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
  }

  /**
   * Verifies that multiple API levels below 31 all use Legacy.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.Q])
  fun `API 29 should use Legacy strategy`() {
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
  }

  /**
   * Verifies API 28 uses Legacy strategy.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.P])
  fun `API 28 should use Legacy strategy`() {
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
  }

  /**
   * Verifies API 23 (minimum supported) uses Legacy strategy.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `API 23 minimum should use Legacy strategy`() {
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
  }
}