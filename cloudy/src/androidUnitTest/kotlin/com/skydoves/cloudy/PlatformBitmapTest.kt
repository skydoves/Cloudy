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
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

internal class PlatformBitmapTest {

  @Test
  fun `PlatformBitmap should have correct width and height`() {
    val width = 100
    val height = 200
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(width)
    `when`(mockBitmap.height).thenReturn(height)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    assertEquals(width, platformBitmap.width)
    assertEquals(height, platformBitmap.height)
  }

  @Test
  fun `PlatformBitmap should be recyclable when bitmap is mutable`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    assertTrue(platformBitmap.isRecyclable)
  }

  @Test
  fun `PlatformBitmap should not be recyclable when bitmap is recycled`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(true)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    assertFalse(platformBitmap.isRecyclable)
  }

  @Test
  fun `PlatformBitmap should not be recyclable when bitmap is immutable`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(false)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    assertFalse(platformBitmap.isRecyclable)
  }

  @Test
  fun `createCompatible should create new bitmap with same dimensions`() {
    val mockOriginalBitmap = mock(Bitmap::class.java)
    `when`(mockOriginalBitmap.width).thenReturn(100)
    `when`(mockOriginalBitmap.height).thenReturn(200)
    `when`(mockOriginalBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
    `when`(mockOriginalBitmap.isRecycled).thenReturn(false)
    `when`(mockOriginalBitmap.isMutable).thenReturn(true)
    
    val mockCompatibleBitmap = mock(Bitmap::class.java)
    `when`(mockCompatibleBitmap.width).thenReturn(100)
    `when`(mockCompatibleBitmap.height).thenReturn(200)
    `when`(mockCompatibleBitmap.config).thenReturn(Bitmap.Config.ARGB_8888)
    `when`(mockCompatibleBitmap.isRecycled).thenReturn(false)
    `when`(mockCompatibleBitmap.isMutable).thenReturn(true)
    
    // Mock Bitmap.createBitmap static method
    mockStatic(Bitmap::class.java).use { mockedStatic ->
      mockedStatic.`when`<Bitmap> { Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888) }
        .thenReturn(mockCompatibleBitmap)
      
      val platformBitmap = PlatformBitmap(mockOriginalBitmap)
      val compatibleBitmap = platformBitmap.createCompatible()
      
      assertEquals(platformBitmap.width, compatibleBitmap.width)
      assertEquals(platformBitmap.height, compatibleBitmap.height)
      assertNotSame(platformBitmap, compatibleBitmap)
    }
  }

  @Test
  fun `createCompatible should create new bitmap with same config`() {
    val mockOriginalBitmap = mock(Bitmap::class.java)
    `when`(mockOriginalBitmap.width).thenReturn(100)
    `when`(mockOriginalBitmap.height).thenReturn(100)
    `when`(mockOriginalBitmap.config).thenReturn(Bitmap.Config.RGB_565)
    `when`(mockOriginalBitmap.isRecycled).thenReturn(false)
    `when`(mockOriginalBitmap.isMutable).thenReturn(true)
    
    val mockCompatibleBitmap = mock(Bitmap::class.java)
    `when`(mockCompatibleBitmap.width).thenReturn(100)
    `when`(mockCompatibleBitmap.height).thenReturn(100)
    `when`(mockCompatibleBitmap.config).thenReturn(Bitmap.Config.RGB_565)
    `when`(mockCompatibleBitmap.isRecycled).thenReturn(false)
    `when`(mockCompatibleBitmap.isMutable).thenReturn(true)
    
    // Mock Bitmap.createBitmap static method
    mockStatic(Bitmap::class.java).use { mockedStatic ->
      mockedStatic.`when`<Bitmap> { Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565) }
        .thenReturn(mockCompatibleBitmap)
      
      val platformBitmap = PlatformBitmap(mockOriginalBitmap)
      val compatibleBitmap = platformBitmap.createCompatible()
      
      assertEquals(mockOriginalBitmap.config, compatibleBitmap.bitmap.config)
    }
  }

  @Test
  fun `dispose should recycle the underlying bitmap`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    assertFalse(mockBitmap.isRecycled)
    
    platformBitmap.dispose()
    
    verify(mockBitmap).recycle()
  }

  @Test
  fun `dispose should not throw exception when bitmap is already recycled`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(true)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    platformBitmap.dispose()
    platformBitmap.dispose() // Should not throw exception
    
    verify(mockBitmap, times(0)).recycle() // Already recycled, so recycle() should not be called
  }

  @Test
  fun `toPlatformBitmap extension should wrap Bitmap correctly`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = mockBitmap.toPlatformBitmap()
    
    assertEquals(mockBitmap, platformBitmap.bitmap)
    assertEquals(mockBitmap.width, platformBitmap.width)
    assertEquals(mockBitmap.height, platformBitmap.height)
  }

  @Test
  fun `toAndroidBitmap extension should return underlying Bitmap`() {
    val mockBitmap = mock(Bitmap::class.java)
    `when`(mockBitmap.width).thenReturn(100)
    `when`(mockBitmap.height).thenReturn(100)
    `when`(mockBitmap.isRecycled).thenReturn(false)
    `when`(mockBitmap.isMutable).thenReturn(true)
    
    val platformBitmap = PlatformBitmap(mockBitmap)
    
    assertSame(mockBitmap, platformBitmap.toAndroidBitmap())
  }
} 