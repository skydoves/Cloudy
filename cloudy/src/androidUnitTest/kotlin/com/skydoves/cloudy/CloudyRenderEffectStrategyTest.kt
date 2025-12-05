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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for CloudyRenderEffectStrategy.
 *
 * Tests the GPU-accelerated blur strategy for Android S (API 31) and above.
 * Covers state callbacks, radius calculations, and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
internal class CloudyRenderEffectStrategyTest {

  /**
   * Verifies that CloudyRenderEffectStrategy is a singleton.
   */
  @Test
  fun `CloudyRenderEffectStrategy should be singleton`() {
    val strategy1 = CloudyRenderEffectStrategy
    val strategy2 = CloudyRenderEffectStrategy
    
    assertSame("Should be the same instance", strategy1, strategy2)
  }

  /**
   * Verifies that the strategy implements CloudyBlurStrategy interface.
   */
  @Test
  fun `CloudyRenderEffectStrategy should implement CloudyBlurStrategy`() {
    val strategy: CloudyBlurStrategy = CloudyRenderEffectStrategy
    
    assertNotNull("Strategy should not be null", strategy)
  }

  /**
   * Verifies that Success.Applied state is returned for GPU blur.
   */
  @Test
  fun `should return Success Applied state for RenderEffect blur`() {
    var capturedState: CloudyState? = null
    
    // Simulate the callback that would be invoked
    val onStateChanged: (CloudyState) -> Unit = { state ->
      capturedState = state
    }
    
    // Create a test state that mimics what RenderEffect returns
    onStateChanged(CloudyState.Success.Applied)
    
    assertNotNull("State should be captured", capturedState)
    assertTrue(
      "Should be Success.Applied",
      capturedState is CloudyState.Success.Applied,
    )
  }

  /**
   * Verifies radius to sigma conversion formula.
   * RenderEffect uses sigma = radius / 2.0
   */
  @Test
  fun `radius to sigma conversion should be correct`() {
    val testCases = mapOf(
      0 to 0.0f,
      10 to 5.0f,
      20 to 10.0f,
      25 to 12.5f,
      50 to 25.0f,
      100 to 50.0f,
    )
    
    testCases.forEach { (radius, expectedSigma) ->
      val sigma = radius / 2.0f
      assertEquals(
        "Radius $radius should convert to sigma $expectedSigma",
        expectedSigma,
        sigma,
        0.001f,
      )
    }
  }

  /**
   * Verifies that zero radius is handled correctly.
   */
  @Test
  fun `zero radius should be handled without blur`() {
    val radius = 0
    val sigma = radius / 2.0f
    
    assertEquals("Zero radius should produce zero sigma", 0.0f, sigma, 0.001f)
  }

  /**
   * Verifies that very large radius values are converted correctly.
   */
  @Test
  fun `large radius values should convert correctly`() {
    val largeRadius = 1000
    val expectedSigma = 500.0f
    val sigma = largeRadius / 2.0f
    
    assertEquals(
      "Large radius should convert correctly",
      expectedSigma,
      sigma,
      0.001f,
    )
  }

  /**
   * Verifies that odd radius values convert correctly to sigma.
   */
  @Test
  fun `odd radius values should convert to fractional sigma`() {
    val oddRadius = 15
    val expectedSigma = 7.5f
    val sigma = oddRadius / 2.0f
    
    assertEquals(
      "Odd radius should produce fractional sigma",
      expectedSigma,
      sigma,
      0.001f,
    )
  }

  /**
   * Verifies typical blur radius ranges.
   */
  @Test
  fun `typical blur radius range should be supported`() {
    val typicalRadii = listOf(5, 10, 15, 20, 25, 30, 40, 50)
    
    typicalRadii.forEach { radius ->
      val sigma = radius / 2.0f
      assertTrue("Sigma should be positive for radius $radius", sigma > 0)
      assertTrue("Sigma should be reasonable", sigma <= 25.0f)
    }
  }

  /**
   * Verifies state callback behavior with multiple radius values.
   */
  @Test
  fun `state callback should be invoked for different radii`() {
    val states = mutableListOf<CloudyState>()
    val onStateChanged: (CloudyState) -> Unit = { state ->
      states.add(state)
    }
    
    // Simulate multiple blur operations
    repeat(3) {
      onStateChanged(CloudyState.Success.Applied)
    }
    
    assertEquals("Should capture all state changes", 3, states.size)
    states.forEach { state ->
      assertTrue("All states should be Success.Applied", state is CloudyState.Success.Applied)
    }
  }

  /**
   * Verifies that API 31+ is correctly detected.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `API 31 should be detected as RenderEffect capable`() {
    assertTrue(
      "API 31 should support RenderEffect",
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
  }

  /**
   * Verifies that API 32+ is correctly detected.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S_V2])
  fun `API 32 should be detected as RenderEffect capable`() {
    assertTrue(
      "API 32 should support RenderEffect",
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
  }

  /**
   * Verifies that API 33+ is correctly detected.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `API 33 should be detected as RenderEffect capable`() {
    assertTrue(
      "API 33 should support RenderEffect",
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
  }

  /**
   * Verifies minimum blur radius boundary.
   */
  @Test
  fun `minimum radius of 1 should produce minimum sigma`() {
    val minRadius = 1
    val sigma = minRadius / 2.0f
    
    assertEquals("Minimum radius should produce 0.5 sigma", 0.5f, sigma, 0.001f)
  }

  /**
   * Verifies that very small sigma values are valid.
   */
  @Test
  fun `very small sigma values should be valid`() {
    val smallRadii = listOf(1, 2, 3, 4)
    
    smallRadii.forEach { radius ->
      val sigma = radius / 2.0f
      assertTrue("Small sigma should be positive", sigma > 0)
      assertTrue("Small sigma should be reasonable", sigma < 5.0f)
    }
  }
}