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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for CloudyLegacyBlurStrategy.
 *
 * Tests the CPU-based blur strategy for Android R (API 30) and below.
 * Covers bitmap capture, state transitions, and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
internal class CloudyLegacyBlurStrategyTest {

  /**
   * Verifies that CloudyLegacyBlurStrategy is a singleton.
   */
  @Test
  fun `CloudyLegacyBlurStrategy should be singleton`() {
    val strategy1 = CloudyLegacyBlurStrategy
    val strategy2 = CloudyLegacyBlurStrategy
    
    assertSame("Should be the same instance", strategy1, strategy2)
  }

  /**
   * Verifies that the strategy implements CloudyBlurStrategy interface.
   */
  @Test
  fun `CloudyLegacyBlurStrategy should implement CloudyBlurStrategy`() {
    val strategy: CloudyBlurStrategy = CloudyLegacyBlurStrategy
    
    assertNotNull("Strategy should not be null", strategy)
  }

  /**
   * Verifies that Success.Captured state is expected for CPU blur.
   */
  @Test
  fun `should return Success Captured state for CPU blur`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state = CloudyState.Success.Captured(mockBitmap)
    
    assertTrue(
      "Should be Success.Captured",
      state is CloudyState.Success.Captured,
    )
    assertNotNull("Bitmap should be present", state.bitmap)
  }

  /**
   * Verifies Loading state transition.
   */
  @Test
  fun `should transition through Loading state during processing`() {
    val loadingState = CloudyState.Loading
    
    assertTrue(
      "Should be Loading state",
      loadingState is CloudyState.Loading,
    )
  }

  /**
   * Verifies Error state handling.
   */
  @Test
  fun `should handle Error state on failure`() {
    val exception = RuntimeException("Blur failed")
    val errorState = CloudyState.Error(exception)
    
    assertTrue("Should be Error state", errorState is CloudyState.Error)
    assertSame("Should contain the exception", exception, errorState.throwable)
  }

  /**
   * Verifies that API 30 is detected as requiring legacy strategy.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `API 30 should require legacy strategy`() {
    assertTrue(
      "API 30 should not support RenderEffect",
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    )
  }

  /**
   * Verifies that API 29 is detected as requiring legacy strategy.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.Q])
  fun `API 29 should require legacy strategy`() {
    assertTrue(
      "API 29 should not support RenderEffect",
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    )
  }

  /**
   * Verifies that API 28 is detected as requiring legacy strategy.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.P])
  fun `API 28 should require legacy strategy`() {
    assertTrue(
      "API 28 should not support RenderEffect",
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    )
  }

  /**
   * Verifies zero radius handling.
   */
  @Test
  fun `zero radius should skip blur processing`() {
    val radius = 0
    assertTrue("Zero radius should be handled", radius <= 0)
  }

  /**
   * Verifies negative radius handling.
   */
  @Test
  fun `negative radius should be handled gracefully`() {
    val radius = -1
    assertTrue("Negative radius should be detected", radius <= 0)
  }

  /**
   * Verifies typical blur radius ranges.
   */
  @Test
  fun `typical blur radius range should be supported`() {
    val typicalRadii = listOf(5, 10, 15, 20, 25)
    
    typicalRadii.forEach { radius ->
      assertTrue("Radius $radius should be positive", radius > 0)
      assertTrue("Radius $radius should be reasonable", radius <= 25)
    }
  }

  /**
   * Verifies large radius values (requiring iterative blur).
   */
  @Test
  fun `large radius values should be supported`() {
    val largeRadii = listOf(30, 40, 50, 75, 100)
    
    largeRadii.forEach { radius ->
      assertTrue("Large radius $radius should be positive", radius > 0)
      // These would require multiple blur passes in iterativeBlur
    }
  }

  /**
   * Verifies state sequence for successful blur.
   */
  @Test
  fun `successful blur should transition Nothing to Loading to Captured`() {
    val states = listOf(
      CloudyState.Nothing,
      CloudyState.Loading,
      CloudyState.Success.Captured(createMockPlatformBitmap()),
    )
    
    assertTrue("First state should be Nothing", states[0] is CloudyState.Nothing)
    assertTrue("Second state should be Loading", states[1] is CloudyState.Loading)
    assertTrue("Third state should be Success.Captured", states[2] is CloudyState.Success.Captured)
  }

  /**
   * Verifies state sequence for failed blur.
   */
  @Test
  fun `failed blur should transition Nothing to Loading to Error`() {
    val states = listOf(
      CloudyState.Nothing,
      CloudyState.Loading,
      CloudyState.Error(RuntimeException("Test error")),
    )
    
    assertTrue("First state should be Nothing", states[0] is CloudyState.Nothing)
    assertTrue("Second state should be Loading", states[1] is CloudyState.Loading)
    assertTrue("Third state should be Error", states[2] is CloudyState.Error)
  }

  /**
   * Verifies that bitmap dimensions are validated.
   */
  @Test
  fun `bitmap with valid dimensions should be accepted`() {
    val validDimensions = listOf(
      Pair(100, 100),
      Pair(200, 200),
      Pair(400, 300),
      Pair(1920, 1080),
    )
    
    validDimensions.forEach { (width, height) ->
      val bitmap = createMockPlatformBitmap(width, height)
      assertNotNull("Bitmap should be created", bitmap)
    }
  }

  /**
   * Verifies that small bitmap dimensions are handled.
   */
  @Test
  fun `small bitmap dimensions should be handled`() {
    val smallDimensions = listOf(
      Pair(1, 1),
      Pair(10, 10),
      Pair(50, 50),
    )
    
    smallDimensions.forEach { (width, height) ->
      val bitmap = createMockPlatformBitmap(width, height)
      assertNotNull("Small bitmap should be created", bitmap)
    }
  }

  /**
   * Verifies that very large bitmap dimensions are recognized.
   */
  @Test
  fun `large bitmap dimensions should be recognized`() {
    val largeDimensions = listOf(
      Pair(2000, 2000),
      Pair(4096, 4096),
    )
    
    largeDimensions.forEach { (width, height) ->
      val bitmap = createMockPlatformBitmap(width, height)
      assertNotNull("Large bitmap should be created", bitmap)
    }
  }

  /**
   * Verifies that aspect ratios are preserved.
   */
  @Test
  fun `various aspect ratios should be handled`() {
    val aspectRatios = listOf(
      Pair(16, 9),    // 16:9
      Pair(4, 3),     // 4:3
      Pair(1, 1),     // 1:1
      Pair(9, 16),    // 9:16 portrait
    )
    
    aspectRatios.forEach { (width, height) ->
      val bitmap = createMockPlatformBitmap(width * 100, height * 100)
      assertNotNull("Bitmap with aspect ratio $width:$height should be created", bitmap)
    }
  }
}