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

import android.graphics.Bitmap
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Creates a mock `PlatformBitmap` of the specified width and height for testing purposes.
 *
 * @param width The width of the bitmap in pixels. Defaults to 100.
 * @param height The height of the bitmap in pixels. Defaults to 100.
 * @return A `PlatformBitmap` instance wrapping a mocked Android Bitmap.
 */
/**
 * Creates a test PlatformBitmap that wraps a Mockito-mocked Android Bitmap with the given dimensions and a non-recycled, mutable state.
 *
 * @param width The mocked bitmap width in pixels.
 * @param height The mocked bitmap height in pixels.
 * @return A PlatformBitmap containing the configured mock Bitmap.
 */
internal fun createMockPlatformBitmap(width: Int = 100, height: Int = 100): PlatformBitmap {
  val mockBitmap = mock(Bitmap::class.java)
  `when`(mockBitmap.width).thenReturn(width)
  `when`(mockBitmap.height).thenReturn(height)
  `when`(mockBitmap.isRecycled).thenReturn(false)
  `when`(mockBitmap.isMutable).thenReturn(true)
  return PlatformBitmap(mockBitmap)
}
