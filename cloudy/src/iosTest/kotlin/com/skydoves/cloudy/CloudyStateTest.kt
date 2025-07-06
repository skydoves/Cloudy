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

import kotlinx.cinterop.ExperimentalForeignApi
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
  fun successStateShouldContainBitmap() {
    val bitmap = createTestPlatformBitmap(100, 100)
    val state = CloudyState.Success(bitmap)
    assertEquals(bitmap, state.bitmap)
  }

  @Test
  fun errorStateShouldContainThrowable() {
    val exception = RuntimeException("Test error")
    val state = CloudyState.Error(exception)
    assertEquals(exception, state.throwable)
  }

  @Test
  fun successStatesWithSameBitmapShouldBeEqual() {
    val bitmap = createTestPlatformBitmap(100, 100)
    val state1 = CloudyState.Success(bitmap)
    val state2 = CloudyState.Success(bitmap)
    assertEquals(state1, state2)
  }

  @Test
  fun successStatesWithDifferentBitmapsShouldNotBeEqual() {
    val bitmap1 = createTestPlatformBitmap(100, 100)
    val bitmap2 = createTestPlatformBitmap(200, 200)
    val state1 = CloudyState.Success(bitmap1)
    val state2 = CloudyState.Success(bitmap2)
    assertFalse(state1 == state2)
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun createTestPlatformBitmap(width: Int, height: Int): PlatformBitmap {
  return PlatformBitmap(createTestUIImage(width, height))
}

@OptIn(ExperimentalForeignApi::class)
private fun createTestUIImage(width: Int, height: Int): platform.UIKit.UIImage {
  val size = platform.CoreGraphics.CGSizeMake(width.toDouble(), height.toDouble())
  platform.UIKit.UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
  val image = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
  platform.UIKit.UIGraphicsEndImageContext()
  return image ?: platform.UIKit.UIImage()
}
