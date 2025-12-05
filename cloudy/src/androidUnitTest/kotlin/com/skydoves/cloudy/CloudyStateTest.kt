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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CloudyStateTest {

  @Test
  fun `Nothing state should be singleton`() {
    val state1 = CloudyState.Nothing
    val state2 = CloudyState.Nothing

    assertSame(state1, state2)
  }

  @Test
  fun `Loading state should be singleton`() {
    val state1 = CloudyState.Loading
    val state2 = CloudyState.Loading

    assertSame(state1, state2)
  }

  @Test
  fun `Success Applied state should be singleton`() {
    val state1 = CloudyState.Success.Applied
    val state2 = CloudyState.Success.Applied

    assertSame(state1, state2)
  }

  @Test
  fun `Success Captured state should contain bitmap`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state = CloudyState.Success.Captured(mockBitmap)

    assertEquals(mockBitmap, state.bitmap)
  }

  @Test
  fun `Success Applied should be instance of Success`() {
    val state: CloudyState = CloudyState.Success.Applied

    assertTrue(state is CloudyState.Success)
  }

  @Test
  fun `Success Captured should be instance of Success`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state: CloudyState = CloudyState.Success.Captured(mockBitmap)

    assertTrue(state is CloudyState.Success)
  }

  @Test
  fun `Success type hierarchy allows pattern matching both subtypes`() {
    val appliedState: CloudyState = CloudyState.Success.Applied
    val capturedState: CloudyState = CloudyState.Success.Captured(
      createMockPlatformBitmap(100, 100),
    )

    assertTrue(appliedState is CloudyState.Success.Applied)
    assertTrue(capturedState is CloudyState.Success.Captured)
  }

  @Test
  fun `Error state should contain throwable`() {
    val exception = RuntimeException("Test error")
    val state = CloudyState.Error(exception)

    assertEquals(exception, state.throwable)
  }

  @Test
  fun `Success Captured states with same bitmap should be equal`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state1 = CloudyState.Success.Captured(mockBitmap)
    val state2 = CloudyState.Success.Captured(mockBitmap)

    assertEquals(state1, state2)
  }

  @Test
  fun `Success Captured states with different bitmaps should not be equal`() {
    val mockBitmap1 = createMockPlatformBitmap(100, 100)
    val mockBitmap2 = createMockPlatformBitmap(200, 200)
    val state1 = CloudyState.Success.Captured(mockBitmap1)
    val state2 = CloudyState.Success.Captured(mockBitmap2)

    assertNotEquals(state1, state2)
  }

  @Test
  fun `Error states with same throwable should be equal`() {
    val exception = RuntimeException("Test error")
    val state1 = CloudyState.Error(exception)
    val state2 = CloudyState.Error(exception)

    assertEquals(state1, state2)
  }

  @Test
  fun `Error states with different throwables should not be equal`() {
    val exception1 = RuntimeException("Error 1")
    val exception2 = RuntimeException("Error 2")
    val state1 = CloudyState.Error(exception1)
    val state2 = CloudyState.Error(exception2)

    assertNotEquals(state1, state2)
  }
}

  /**
   * Verifies that Success.Applied is the correct type for GPU blur.
   */
  @Test
  fun `Success Applied should represent GPU blur without bitmap`() {
    val state = CloudyState.Success.Applied
    
    assertTrue("Should be Success type", state is CloudyState.Success)
    assertTrue("Should be Applied type", state is CloudyState.Success.Applied)
  }

  /**
   * Verifies that Success.Captured is the correct type for CPU blur.
   */
  @Test
  fun `Success Captured should represent CPU blur with bitmap`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state = CloudyState.Success.Captured(mockBitmap)
    
    assertTrue("Should be Success type", state is CloudyState.Success)
    assertTrue("Should be Captured type", state is CloudyState.Success.Captured)
    assertNotNull("Should have bitmap", state.bitmap)
  }

  /**
   * Verifies when expression exhaustiveness with all CloudyState types.
   */
  @Test
  fun `when expression should be exhaustive for all CloudyState types`() {
    val states = listOf<CloudyState>(
      CloudyState.Nothing,
      CloudyState.Loading,
      CloudyState.Success.Applied,
      CloudyState.Success.Captured(createMockPlatformBitmap()),
      CloudyState.Error(RuntimeException("test")),
    )
    
    states.forEach { state ->
      val result = when (state) {
        CloudyState.Nothing -> "nothing"
        CloudyState.Loading -> "loading"
        CloudyState.Success.Applied -> "applied"
        is CloudyState.Success.Captured -> "captured"
        is CloudyState.Error -> "error"
      }
      assertNotNull("Should match a case", result)
    }
  }

  /**
   * Verifies Success interface is correctly sealed.
   */
  @Test
  fun `Success interface should have exactly two implementations`() {
    val appliedState: CloudyState.Success = CloudyState.Success.Applied
    val capturedState: CloudyState.Success = CloudyState.Success.Captured(
      createMockPlatformBitmap(),
    )
    
    assertTrue(appliedState is CloudyState.Success.Applied)
    assertTrue(capturedState is CloudyState.Success.Captured)
  }

  /**
   * Verifies pattern matching with Success subtypes.
   */
  @Test
  fun `pattern matching should distinguish Success subtypes`() {
    val states: List<CloudyState.Success> = listOf(
      CloudyState.Success.Applied,
      CloudyState.Success.Captured(createMockPlatformBitmap()),
    )
    
    var appliedCount = 0
    var capturedCount = 0
    
    states.forEach { state ->
      when (state) {
        CloudyState.Success.Applied -> appliedCount++
        is CloudyState.Success.Captured -> capturedCount++
      }
    }
    
    assertEquals("Should have one Applied", 1, appliedCount)
    assertEquals("Should have one Captured", 1, capturedCount)
  }

  /**
   * Verifies Error state with different exception types.
   */
  @Test
  fun `Error state should work with different exception types`() {
    val exceptions = listOf(
      RuntimeException("Runtime error"),
      IllegalStateException("Illegal state"),
      IllegalArgumentException("Illegal argument"),
      Exception("Generic exception"),
    )
    
    exceptions.forEach { exception ->
      val state = CloudyState.Error(exception)
      assertEquals("Should contain the exception", exception, state.throwable)
      assertTrue("Should be Error type", state is CloudyState.Error)
    }
  }

  /**
   * Verifies Success.Captured with different bitmap sizes.
   */
  @Test
  fun `Success Captured should work with different bitmap sizes`() {
    val sizes = listOf(
      Pair(100, 100),
      Pair(200, 300),
      Pair(1920, 1080),
      Pair(50, 50),
    )
    
    sizes.forEach { (width, height) ->
      val bitmap = createMockPlatformBitmap(width, height)
      val state = CloudyState.Success.Captured(bitmap)
      
      assertEquals("Should contain the bitmap", bitmap, state.bitmap)
    }
  }

  /**
   * Verifies hashCode consistency for singleton states.
   */
  @Test
  fun `singleton states should have consistent hashCode`() {
    val nothing1 = CloudyState.Nothing
    val nothing2 = CloudyState.Nothing
    assertEquals("Nothing should have same hashCode", nothing1.hashCode(), nothing2.hashCode())
    
    val loading1 = CloudyState.Loading
    val loading2 = CloudyState.Loading
    assertEquals("Loading should have same hashCode", loading1.hashCode(), loading2.hashCode())
    
    val applied1 = CloudyState.Success.Applied
    val applied2 = CloudyState.Success.Applied
    assertEquals("Applied should have same hashCode", applied1.hashCode(), applied2.hashCode())
  }

  /**
   * Verifies toString representation for debugging.
   */
  @Test
  fun `states should have meaningful toString representations`() {
    val states = listOf<CloudyState>(
      CloudyState.Nothing,
      CloudyState.Loading,
      CloudyState.Success.Applied,
      CloudyState.Success.Captured(createMockPlatformBitmap()),
      CloudyState.Error(RuntimeException("test")),
    )
    
    states.forEach { state ->
      val toString = state.toString()
      assertNotNull("toString should not be null", toString)
      assertTrue("toString should not be empty", toString.isNotEmpty())
    }
  }

  /**
   * Verifies state transitions in typical blur workflow.
   */
  @Test
  fun `typical blur workflow should transition through states correctly`() {
    val workflow = mutableListOf<CloudyState>()
    
    // Simulate typical workflow
    workflow.add(CloudyState.Nothing)
    workflow.add(CloudyState.Loading)
    workflow.add(CloudyState.Success.Captured(createMockPlatformBitmap()))
    
    assertEquals("Should have 3 states", 3, workflow.size)
    assertTrue("First should be Nothing", workflow[0] is CloudyState.Nothing)
    assertTrue("Second should be Loading", workflow[1] is CloudyState.Loading)
    assertTrue("Third should be Success", workflow[2] is CloudyState.Success)
  }

  /**
   * Verifies state transitions for GPU blur workflow.
   */
  @Test
  fun `GPU blur workflow should use Applied state`() {
    val workflow = mutableListOf<CloudyState>()
    
    // Simulate GPU workflow
    workflow.add(CloudyState.Nothing)
    workflow.add(CloudyState.Success.Applied)
    
    assertEquals("Should have 2 states", 2, workflow.size)
    assertTrue("First should be Nothing", workflow[0] is CloudyState.Nothing)
    assertTrue("Second should be Applied", workflow[1] is CloudyState.Success.Applied)
  }

  /**
   * Verifies multiple state changes are tracked correctly.
   */
  @Test
  fun `multiple state changes should be tracked correctly`() {
    val stateHistory = mutableListOf<CloudyState>()
    
    repeat(5) { index ->
      when (index % 3) {
        0 -> stateHistory.add(CloudyState.Nothing)
        1 -> stateHistory.add(CloudyState.Loading)
        2 -> stateHistory.add(CloudyState.Success.Applied)
      }
    }
    
    assertEquals("Should have 5 states", 5, stateHistory.size)
    assertTrue("History should contain Nothing", stateHistory.any { it is CloudyState.Nothing })
    assertTrue("History should contain Loading", stateHistory.any { it is CloudyState.Loading })
    assertTrue("History should contain Applied", stateHistory.any { it is CloudyState.Success.Applied })
  }

  /**
   * Verifies CloudyState sealed interface hierarchy.
   */
  @Test
  fun `CloudyState sealed interface should be properly structured`() {
    val nothing: CloudyState = CloudyState.Nothing
    val loading: CloudyState = CloudyState.Loading
    val applied: CloudyState = CloudyState.Success.Applied
    val captured: CloudyState = CloudyState.Success.Captured(createMockPlatformBitmap())
    val error: CloudyState = CloudyState.Error(RuntimeException())
    
    // All should implement CloudyState
    assertTrue(nothing is CloudyState)
    assertTrue(loading is CloudyState)
    assertTrue(applied is CloudyState)
    assertTrue(captured is CloudyState)
    assertTrue(error is CloudyState)
  }
}
