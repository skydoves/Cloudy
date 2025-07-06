package com.skydoves.cloudy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi

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
    bitmap.dispose() // 두 번 호출해도 예외 없어야 함
  }

  @Test
  fun toUIImageExtensionShouldReturnUnderlyingUIImage() {
    val uiImage = createTestUIImage(100, 100)
    val platformBitmap = PlatformBitmap(uiImage)
    assertEquals(uiImage, platformBitmap.toUIImage())
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
