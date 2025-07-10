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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

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
  fun `Success state should contain bitmap`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state = CloudyState.Success(mockBitmap)

    assertEquals(mockBitmap, state.bitmap)
  }

  @Test
  fun `Success state can contain null bitmap`() {
    val state = CloudyState.Success(null)

    assertNull(state.bitmap)
  }

  @Test
  fun `Error state should contain throwable`() {
    val exception = RuntimeException("Test error")
    val state = CloudyState.Error(exception)

    assertEquals(exception, state.throwable)
  }

  @Test
  fun `Success states with same bitmap should be equal`() {
    val mockBitmap = createMockPlatformBitmap(100, 100)
    val state1 = CloudyState.Success(mockBitmap)
    val state2 = CloudyState.Success(mockBitmap)

    assertEquals(state1, state2)
  }

  @Test
  fun `Success states with different bitmaps should not be equal`() {
    val mockBitmap1 = createMockPlatformBitmap(100, 100)
    val mockBitmap2 = createMockPlatformBitmap(200, 200)
    val state1 = CloudyState.Success(mockBitmap1)
    val state2 = CloudyState.Success(mockBitmap2)

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

  private fun createMockPlatformBitmap(width: Int, height: Int): PlatformBitmap {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(width)
    `when`(mockBitmap.height).thenReturn(height)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(true)

    return PlatformBitmap(mockBitmap)
  }
}
