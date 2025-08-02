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
@file:OptIn(ExperimentalForeignApi::class)

package com.skydoves.cloudy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.posix.free
import platform.posix.malloc
import kotlin.math.roundToInt

/**
 * iOS implementation of [PlatformBitmap] that wraps iOS UIImage.
 */
@Immutable
public actual class PlatformBitmap(
  /**
   * The underlying iOS UIImage.
   */
  public val image: UIImage
) {

  public actual val width: Int
    get() = (image.size.useContents { width } * image.scale).roundToInt()

  public actual val height: Int
    get() = (image.size.useContents { height } * image.scale).roundToInt()

  public actual val isRecyclable: Boolean
    get() = true // UIImage doesn't have a direct recyclable concept
}

/**
 * Creates a new `PlatformBitmap` with the same dimensions and scale as the original image.
 *
 * If image creation fails, returns a `PlatformBitmap` wrapping the original image.
 *
 * @return A new `PlatformBitmap` instance compatible in size and scale with the original.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  val size = CGSizeMake(width.toDouble(), height.toDouble())
  UIGraphicsBeginImageContextWithOptions(size, false, image.scale)
  val newImage = UIGraphicsGetImageFromCurrentImageContext()
  UIGraphicsEndImageContext()

  return PlatformBitmap(newImage ?: image)
}

/**
 * Releases resources associated with this bitmap if necessary.
 *
 * On iOS, this function performs no action since memory management is handled automatically by ARC. Present for cross-platform API consistency.
 */
public actual fun PlatformBitmap.dispose() {
  // iOS uses ARC for memory management, so we don't need to manually dispose
  // This method is kept for API consistency across platforms
}

/****
 * Wraps this `UIImage` in a `PlatformBitmap`.
 *
 * @return A `PlatformBitmap` containing the current `UIImage`.
 */
public fun UIImage.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Returns the underlying UIImage associated with this PlatformBitmap.
 *
 * @return The native UIImage instance wrapped by this PlatformBitmap.
 */
public fun PlatformBitmap.toUIImage(): UIImage = image

/**
 * Converts a Compose [ImageBitmap] to an iOS [UIImage].
 *
 * This function performs a pixel-perfect conversion by extracting pixel data from the [ImageBitmap]
 * and creating a corresponding UIImage with the same dimensions and content.
 *
 * @return A [UIImage] with the same content as the [ImageBitmap], or null if conversion fails.
 */
public fun ImageBitmap.toUIImage(): UIImage? {
  return try {
    val width = this.width
    val height = this.height

    if (width <= 0 || height <= 0) return null

    // Extract pixel data from the ImageBitmap
    val pixelMap = this.toPixelMap()

    // Create a bitmap context with RGBA format
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or
      kCGBitmapByteOrder32Big

    // Allocate memory for pixel data (4 bytes per pixel: RGBA)
    val bytesPerPixel = 4
    val bytesPerRow = width * bytesPerPixel
    val totalBytes = height * bytesPerRow
    val pixelData = malloc(totalBytes.toULong())

    pixelData?.let { data ->
      // Convert PixelMap to raw pixel data
      val pixels = data.reinterpret<ByteVar>()

      for (y in 0 until height) {
        for (x in 0 until width) {
          val pixel = pixelMap[x, y]
          val offset = (y * width + x) * bytesPerPixel

          // Convert from normalized (0.0-1.0) to byte values (0-255)
          pixels[offset] = (pixel.red * 255).toInt().toByte()
          pixels[offset + 1] = (pixel.green * 255).toInt().toByte()
          pixels[offset + 2] = (pixel.blue * 255).toInt().toByte()
          pixels[offset + 3] = (pixel.alpha * 255).toInt().toByte()
        }
      }

      // Create CGContext with the pixel data
      val context = CGBitmapContextCreate(
        data = data,
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = bytesPerRow.toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo
      )

      context?.let { ctx ->
        // Create CGImage from the context
        val cgImage = CGBitmapContextCreateImage(ctx)
        cgImage?.let {
          // Create UIImage from CGImage
          val uiImage = UIImage.imageWithCGImage(it)

          // Clean up
          CGContextRelease(ctx)
          CGImageRelease(it)
          free(data)
          CGColorSpaceRelease(colorSpace)

          return uiImage
        }
      }

      // Clean up on failure
      free(data)
    }

    CGColorSpaceRelease(colorSpace)
    null
  } catch (_: Exception) {
    null
  }
}

/**
 * Converts a `UIImage` to a Compose `ImageBitmap` by extracting pixel data from the underlying CGImage.
 *
 * This function performs a pixel-perfect conversion by extracting the raw pixel data from the UIImage's
 * CGImage representation and creating a corresponding Compose ImageBitmap.
 *
 * @return An `ImageBitmap` with the same content as the `UIImage`, or `null` if conversion fails.
 */
public fun UIImage.asImageBitmap(): ImageBitmap? {
  return try {
    val cgImage = this.CGImage ?: return null

    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()

    if (width <= 0 || height <= 0) return null

    // Get pixel data from CGImage
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bytesPerPixel = 4
    val bytesPerRow = width * bytesPerPixel
    val totalBytes = height * bytesPerRow

    // Allocate memory for pixel data
    val pixelData = malloc(totalBytes.toULong())

    pixelData?.let { data ->
      // Create bitmap context to draw the CGImage
      val bitmapInfo =
        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or
          kCGBitmapByteOrder32Big

      val context = CGBitmapContextCreate(
        data = data,
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = bytesPerRow.toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo
      )

      context?.let { ctx ->
        // Draw the CGImage into our bitmap context
        val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
        CGContextDrawImage(ctx, rect, cgImage)

        // Create Skia bitmap
        val imageInfo = ImageInfo.makeN32Premul(width, height)
        val skiaBitmap = Bitmap()
        skiaBitmap.allocPixels(imageInfo)

        // Copy pixel data from CGContext to Skia bitmap
        val pixels = data.reinterpret<ByteVar>()
        val byteArray = ByteArray(totalBytes)

        for (y in 0 until height) {
          for (x in 0 until width) {
            val srcOffset = (y * width + x) * bytesPerPixel
            val dstOffset = (y * width + x) * bytesPerPixel

            // Convert from RGBA to BGRA format for Skia on iOS
            byteArray[dstOffset] = pixels[srcOffset + 2] // Blue
            byteArray[dstOffset + 1] = pixels[srcOffset + 1] // Green
            byteArray[dstOffset + 2] = pixels[srcOffset] // Red
            byteArray[dstOffset + 3] = pixels[srcOffset + 3] // Alpha
          }
        }

        // Install pixels in Skia bitmap
        skiaBitmap.installPixels(imageInfo, byteArray, width * 4)

        // Clean up
        CGContextRelease(ctx)
        free(data)
        CGColorSpaceRelease(colorSpace)

        return skiaBitmap.asComposeImageBitmap()
      }

      // Clean up on failure
      free(data)
    }

    CGColorSpaceRelease(colorSpace)
    null
  } catch (_: Exception) {
    null
  }
}
