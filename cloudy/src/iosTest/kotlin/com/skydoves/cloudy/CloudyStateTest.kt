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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CloudyStateTest {

  @Test
  fun nothingStateShouldBeSingleton() {
    val state1 = CloudyState.Nothing
    val state2 = CloudyState.Nothing
    assertTrue(state1 === state2)
  }

  @Test
  fun loadingStateShouldBeSingleton() {
    val state1 = CloudyState.Loading
    val state2 = CloudyState.Loading
    assertTrue(state1 === state2)
  }

  @Test
  fun successAppliedStateShouldBeSingleton() {
    val state1 = CloudyState.Success.Applied
    val state2 = CloudyState.Success.Applied
    assertTrue(state1 === state2)
  }

  @Test
  fun successCapturedStateShouldContainBitmap() {
    val bitmap = createTestPlatformBitmap(100, 100)
    val state = CloudyState.Success.Captured(bitmap)
    assertEquals(bitmap, state.bitmap)
  }

  @Test
  fun successAppliedShouldBeInstanceOfSuccess() {
    val state: CloudyState = CloudyState.Success.Applied
    assertTrue(state is CloudyState.Success)
  }

  @Test
  fun successCapturedShouldBeInstanceOfSuccess() {
    val bitmap = createTestPlatformBitmap(100, 100)
    val state: CloudyState = CloudyState.Success.Captured(bitmap)
    assertTrue(state is CloudyState.Success)
  }

  @Test
  fun successTypeHierarchyAllowsPatternMatchingBothSubtypes() {
    val appliedState: CloudyState = CloudyState.Success.Applied
    val capturedState: CloudyState = CloudyState.Success.Captured(
      createTestPlatformBitmap(100, 100),
    )

    assertTrue(appliedState is CloudyState.Success.Applied)
    assertTrue(capturedState is CloudyState.Success.Captured)
  }

  @Test
  fun errorStateShouldContainThrowable() {
    val exception = RuntimeException("Test error")
    val state = CloudyState.Error(exception)
    assertEquals(exception, state.throwable)
  }

  @Test
  fun successCapturedStatesWithSameBitmapShouldBeEqual() {
    val bitmap = createTestPlatformBitmap(100, 100)
    val state1 = CloudyState.Success.Captured(bitmap)
    val state2 = CloudyState.Success.Captured(bitmap)
    assertEquals(state1, state2)
  }

  @Test
  fun successCapturedStatesWithDifferentBitmapsShouldNotBeEqual() {
    val bitmap1 = createTestPlatformBitmap(100, 100)
    val bitmap2 = createTestPlatformBitmap(200, 200)
    val state1 = CloudyState.Success.Captured(bitmap1)
    val state2 = CloudyState.Success.Captured(bitmap2)
    assertFalse(state1 == state2)
  }
}
