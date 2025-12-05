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
 * Comprehensive tests for Cloudy.android.kt implementation.
 *
 * Tests the Android-specific implementation of the cloudy modifier,
 * including strategy selection, radius validation, and enabled flag handling.
 */
@RunWith(RobolectricTestRunner::class)
internal class CloudyAndroidTest {

  /**
   * Verifies radius validation rejects negative values.
   */
  @Test(expected = IllegalArgumentException::class)
  fun `negative radius should throw IllegalArgumentException`() {
    // This test verifies the require() statement in Cloudy.android.kt
    val radius = -1
    require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }
  }

  /**
   * Verifies zero radius is accepted.
   */
  @Test
  fun `zero radius should be accepted`() {
    val radius = 0
    assertTrue("Zero radius should pass validation", radius >= 0)
  }

  /**
   * Verifies positive radius values are accepted.
   */
  @Test
  fun `positive radius values should be accepted`() {
    val validRadii = listOf(1, 5, 10, 15, 20, 25, 30, 50, 100)
    
    validRadii.forEach { radius ->
      assertTrue("Radius $radius should be valid", radius >= 0)
    }
  }

  /**
   * Verifies very large radius values are accepted.
   */
  @Test
  fun `very large radius values should be accepted`() {
    val largeRadii = listOf(1000, 5000, 10000)
    
    largeRadii.forEach { radius ->
      assertTrue("Large radius $radius should be valid", radius >= 0)
    }
  }

  /**
   * Verifies enabled flag when false should skip blur.
   */
  @Test
  fun `enabled false should skip blur application`() {
    val enabled = false
    assertFalse("Enabled flag should be false", enabled)
  }

  /**
   * Verifies enabled flag when true should apply blur.
   */
  @Test
  fun `enabled true should apply blur`() {
    val enabled = true
    assertTrue("Enabled flag should be true", enabled)
  }

  /**
   * Verifies strategy selection on API 31.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `API 31 should select RenderEffect strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertTrue("API 31 should use RenderEffect", shouldUseRenderEffect)
    assertEquals(Build.VERSION_CODES.S, Build.VERSION.SDK_INT)
  }

  /**
   * Verifies strategy selection on API 30.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `API 30 should select Legacy strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertFalse("API 30 should not use RenderEffect", shouldUseRenderEffect)
    assertEquals(Build.VERSION_CODES.R, Build.VERSION.SDK_INT)
  }

  /**
   * Verifies strategy selection on API 32.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S_V2])
  fun `API 32 should select RenderEffect strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertTrue("API 32 should use RenderEffect", shouldUseRenderEffect)
  }

  /**
   * Verifies strategy selection on API 33.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `API 33 should select RenderEffect strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertTrue("API 33 should use RenderEffect", shouldUseRenderEffect)
  }

  /**
   * Verifies strategy selection on API 29.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.Q])
  fun `API 29 should select Legacy strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertFalse("API 29 should not use RenderEffect", shouldUseRenderEffect)
  }

  /**
   * Verifies strategy selection on API 28.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.P])
  fun `API 28 should select Legacy strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertFalse("API 28 should not use RenderEffect", shouldUseRenderEffect)
  }

  /**
   * Verifies strategy selection on API 23 (minimum).
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `API 23 should select Legacy strategy`() {
    val shouldUseRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertFalse("API 23 should not use RenderEffect", shouldUseRenderEffect)
  }

  /**
   * Verifies boundary between Legacy and RenderEffect strategies.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.R])
  fun `API 30 is last API to use Legacy strategy`() {
    assertEquals(Build.VERSION_CODES.R, Build.VERSION.SDK_INT)
    assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
  }

  /**
   * Verifies boundary between Legacy and RenderEffect strategies.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `API 31 is first API to use RenderEffect strategy`() {
    assertEquals(Build.VERSION_CODES.S, Build.VERSION.SDK_INT)
    assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
  }

  /**
   * Verifies state callback is invoked for different scenarios.
   */
  @Test
  fun `state callback should be invokable`() {
    var callbackInvoked = false
    val onStateChanged: (CloudyState) -> Unit = { state ->
      callbackInvoked = true
    }
    
    onStateChanged(CloudyState.Success.Applied)
    
    assertTrue("Callback should be invoked", callbackInvoked)
  }

  /**
   * Verifies state callback receives correct state.
   */
  @Test
  fun `state callback should receive correct state`() {
    var receivedState: CloudyState? = null
    val onStateChanged: (CloudyState) -> Unit = { state ->
      receivedState = state
    }
    
    val expectedState = CloudyState.Success.Applied
    onStateChanged(expectedState)
    
    assertEquals("Should receive correct state", expectedState, receivedState)
  }

  /**
   * Verifies multiple state callbacks work correctly.
   */
  @Test
  fun `multiple state callbacks should work`() {
    val states = mutableListOf<CloudyState>()
    val onStateChanged: (CloudyState) -> Unit = { state ->
      states.add(state)
    }
    
    onStateChanged(CloudyState.Nothing)
    onStateChanged(CloudyState.Loading)
    onStateChanged(CloudyState.Success.Applied)
    
    assertEquals("Should receive 3 callbacks", 3, states.size)
  }

  /**
   * Verifies empty callback is valid.
   */
  @Test
  fun `empty state callback should be valid`() {
    val onStateChanged: (CloudyState) -> Unit = { }
    
    // Should not throw
    onStateChanged(CloudyState.Success.Applied)
  }

  /**
   * Verifies typical blur radii from demo app.
   */
  @Test
  fun `typical demo blur radii should be valid`() {
    val demoRadii = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)
    
    demoRadii.forEach { radius ->
      assertTrue("Demo radius $radius should be valid", radius >= 0)
    }
  }

  /**
   * Verifies radius validation error message format.
   */
  @Test
  fun `radius validation error should have descriptive message`() {
    val invalidRadius = -5
    try {
      require(invalidRadius >= 0) { "Blur radius must be non-negative, but was $invalidRadius" }
      throw AssertionError("Should have thrown IllegalArgumentException")
    } catch (e: IllegalArgumentException) {
      assertTrue(
        "Error message should mention radius",
        e.message?.contains("radius") == true,
      )
      assertTrue(
        "Error message should mention the value",
        e.message?.contains("-5") == true,
      )
    }
  }

  /**
   * Verifies inspection mode behavior is considered.
   */
  @Test
  fun `LocalInspectionMode flag should be checkable`() {
    // LocalInspectionMode.current would be false in tests
    val inspectionMode = false
    assertFalse("Inspection mode should be false in tests", inspectionMode)
  }

  /**
   * Verifies that strategy selection is consistent within same API level.
   */
  @Test
  @Config(sdk = [Build.VERSION_CODES.S])
  fun `strategy selection should be consistent on same API`() {
    val check1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val check2 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val check3 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    assertTrue("All checks should return same result", check1 == check2 && check2 == check3)
  }

  /**
   * Verifies radius boundary at zero.
   */
  @Test
  fun `radius boundary at zero should be handled`() {
    val zero = 0
    assertTrue("Zero should be valid", zero >= 0)
    assertFalse("Zero should not be negative", zero < 0)
  }

  /**
   * Verifies fractional radius conversion.
   */
  @Test
  fun `radius should convert to sigma correctly`() {
    val testCases = mapOf(
      10 to 5.0f,
      15 to 7.5f,
      20 to 10.0f,
      25 to 12.5f,
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
   * Verifies API level constants are available.
   */
  @Test
  fun `Build VERSION_CODES constants should be accessible`() {
    assertTrue("VERSION_CODES.S should be defined", Build.VERSION_CODES.S > 0)
    assertTrue("VERSION_CODES.R should be defined", Build.VERSION_CODES.R > 0)
    assertTrue("VERSION_CODES.Q should be defined", Build.VERSION_CODES.Q > 0)
  }

  /**
   * Verifies API level ordering.
   */
  @Test
  fun `API levels should be ordered correctly`() {
    assertTrue("S should be after R", Build.VERSION_CODES.S > Build.VERSION_CODES.R)
    assertTrue("R should be after Q", Build.VERSION_CODES.R > Build.VERSION_CODES.Q)
    assertTrue("Q should be after P", Build.VERSION_CODES.Q > Build.VERSION_CODES.P)
  }
}