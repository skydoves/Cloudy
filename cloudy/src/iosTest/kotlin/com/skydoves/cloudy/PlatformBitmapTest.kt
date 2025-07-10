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
import kotlin.test.assertTrue

internal class PlatformBitmapTest {

  @Test
  fun platformBitmapShouldHaveCorrectWidthAndHeight() {
    val width = 100
    val height = 200
    val bitmap = createTestPlatformBitmap(width, height)
    assertEquals(width, bitmap.width)
    assertEquals(height, bitmap.height)
  }

  @Test
  fun platformBitmapShouldBeRecyclable() {
    val bitmap = createTestPlatformBitmap(100, 100)
    assertTrue(bitmap.isRecyclable)
  }

  @Test
  fun createCompatibleShouldCreateNewBitmapWithSameDimensions() {
    val original = createTestPlatformBitmap(100, 200)
    val compatible = original.createCompatible()
    assertEquals(original.width, compatible.width)
    assertEquals(original.height, compatible.height)
  }

  @Test
  fun disposeShouldNotThrowException() {
    val bitmap = createTestPlatformBitmap(100, 100)
    bitmap.dispose()
    bitmap.dispose() // Should not throw exception even when called twice
  }

  @Test
  fun toUIImageExtensionShouldReturnUnderlyingUIImage() {
    val uiImage = createTestUIImage(100, 100)
    val platformBitmap = PlatformBitmap(uiImage)
    assertEquals(uiImage, platformBitmap.toUIImage())
  }
}
