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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
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
 * Creates a compatible iOS image with the same dimensions.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  val size = CGSizeMake(width.toDouble(), height.toDouble())
  UIGraphicsBeginImageContextWithOptions(size, false, image.scale)
  val newImage = UIGraphicsGetImageFromCurrentImageContext()
  UIGraphicsEndImageContext()

  return PlatformBitmap(newImage ?: image)
}

/**
 * Disposes the iOS image. In iOS, this is mostly a no-op as ARC handles memory management.
 */
public actual fun PlatformBitmap.dispose() {
  // iOS uses ARC for memory management, so we don't need to manually dispose
  // This method is kept for API consistency across platforms
}

/**
 * Converts UIImage to [PlatformBitmap].
 */
public fun UIImage.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Converts [PlatformBitmap] to UIImage.
 */
public fun PlatformBitmap.toUIImage(): UIImage = image

/**
 * Converts Compose [ImageBitmap] to iOS [UIImage].
 *
 * ⚠️ IMPORTANT: This is currently a placeholder implementation that creates a representative
 * image based on sampled pixels, not a pixel-perfect conversion. For production use,
 * implement proper pixel data extraction using native Swift/Objective-C helpers.
 *
 * Current limitations:
 * - Does not extract actual pixel data from Skia bitmap
 * - Creates pattern-based approximation instead of real content
 * - May cause incorrect blur results as the actual image content is not preserved
 *
 * Production TODO: Implement native helpers that can:
 * - Extract raw pixel data from Skia bitmap using proper color space conversion
 * - Handle different pixel formats (RGBA, BGRA, etc.) correctly
 * - Support HDR and wide color gamut images
 * - Optimize memory usage for large images
 */
public fun ImageBitmap.toUIImage(): UIImage? {
  return try {
    val width = this.width
    val height = this.height

    // Create UIImage context with correct dimensions
    val size = CGSizeMake(width.toDouble(), height.toDouble())
    UIGraphicsBeginImageContextWithOptions(size, false, 1.0)

    val context = UIGraphicsGetCurrentContext()
    context?.let { ctx ->
      // Extract basic color information from the ImageBitmap
      // Note: This is a simplified approach - full pixel extraction would require native interop
      val pixelMap = this.toPixelMap()

      // Sample a few pixels to get representative colors
      val sampleColor = if (width > 0 && height > 0) {
        val centerPixel = pixelMap[width / 2, height / 2]

        // Extract normalized color components (already 0.0-1.0 range)
        val alpha = centerPixel.alpha
        val red = centerPixel.red
        val green = centerPixel.green
        val blue = centerPixel.blue

        arrayOf(red, green, blue, alpha)
      } else {
        arrayOf(0.5, 0.5, 0.5, 0.8) // Fallback color
      }

      // Create a more representative image using the sampled color
      platform.CoreGraphics.CGContextSetRGBFillColor(
        ctx,
        sampleColor[0].toDouble(),
        sampleColor[1].toDouble(),
        sampleColor[2].toDouble(),
        sampleColor[3].toDouble()
      )
      platform.CoreGraphics.CGContextFillRect(
        ctx,
        CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
      )

      // Add subtle texture pattern to better represent content
      platform.CoreGraphics.CGContextSetRGBFillColor(
        ctx,
        sampleColor[0].toDouble() * 0.9,
        sampleColor[1].toDouble() * 0.9,
        sampleColor[2].toDouble() * 0.9,
        sampleColor[3].toDouble() * 0.5
      )

      // Create a pattern that varies based on image characteristics
      val patternSize = maxOf(10, minOf(width, height) / 20)
      for (i in 0 until width step patternSize) {
        for (j in 0 until height step patternSize) {
          if ((i / patternSize + j / patternSize) % 2 == 0) {
            platform.CoreGraphics.CGContextFillRect(
              ctx,
              CGRectMake(
                i.toDouble(),
                j.toDouble(),
                (patternSize / 2).toDouble(),
                (patternSize / 2).toDouble()
              )
            )
          }
        }
      }
    }

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    image
  } catch (_: Exception) {
    // Fallback to creating a basic placeholder
    createBasicPlaceholderUIImage(this.width, this.height)
  }
}

/**
 * Converts iOS [UIImage] to Compose [ImageBitmap].
 *
 * ⚠️ IMPORTANT: This is currently a placeholder implementation that generates a synthetic
 * gradient pattern instead of extracting actual CGImage pixels. For production use,
 * implement proper pixel data extraction using native Swift/Objective-C helpers.
 *
 * Current limitations:
 * - Does not extract actual pixel data from CGImage
 * - Creates gradient-based approximation instead of real content
 * - Will cause incorrect blur results as the actual image content is not preserved
 *
 * Production TODO: Implement native helpers that can:
 * - Extract CGImage pixel data efficiently using CGDataProviderCopyData, CGImageGetBytesPerRow
 * - Convert color spaces correctly (sRGB, Display P3, etc.)
 * - Handle different bit depths and alpha channels
 * - Process large images with memory optimization
 */
public fun UIImage.asImageBitmap(): ImageBitmap? {
  return try {
    val width = this.size.useContents { width }.toInt()
    val height = this.size.useContents { height }.toInt()

    if (width <= 0 || height <= 0) return null

    // Create Skia bitmap with proper dimensions
    val imageInfo = ImageInfo.makeN32Premul(width, height)
    val skiaBitmap = Bitmap()
    skiaBitmap.allocPixels(imageInfo)

    // Create a pattern that represents the UIImage content
    // This is a simplified approach - full conversion would extract actual CGImage pixels
    val pixels = IntArray(width * height) { index ->
      val x = index % width
      val y = index / width

      // Create a gradient pattern that varies across the image
      val normalizedX = x.toFloat() / width
      val normalizedY = y.toFloat() / height

      // Generate colors that vary spatially to represent content diversity
      val red = (128 + (normalizedX * 127)).toInt()
      val green = (128 + (normalizedY * 127)).toInt()
      val blue = (128 + ((normalizedX + normalizedY) * 63)).toInt()
      val alpha = 255

      // Add some texture variation
      val texture = if ((x / 8 + y / 8) % 2 == 0) 20 else -20
      val finalRed = (red + texture).coerceIn(0, 255)
      val finalGreen = (green + texture).coerceIn(0, 255)
      val finalBlue = (blue + texture).coerceIn(0, 255)

      // Compose ARGB color using infix bit operations
      (alpha shl 24) or (finalRed shl 16) or (finalGreen shl 8) or finalBlue
    }

    // Convert to ByteArray for Skia - iOS Skia expects BGRA format
    val byteArray = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
      val pixel = pixels[i]
      val baseIndex = i * 4
      // Skia expects BGRA format on iOS
      byteArray[baseIndex] = (pixel and 0xFF).toByte() // Blue
      byteArray[baseIndex + 1] = (pixel shr 8 and 0xFF).toByte() // Green
      byteArray[baseIndex + 2] = (pixel shr 16 and 0xFF).toByte() // Red
      byteArray[baseIndex + 3] = (pixel shr 24 and 0xFF).toByte() // Alpha
    }

    skiaBitmap.installPixels(imageInfo, byteArray, width * 4)
    skiaBitmap.asComposeImageBitmap()
  } catch (_: Exception) {
    null
  }
}

/**
 * Creates a basic placeholder UIImage with specified dimensions.
 */
private fun createBasicPlaceholderUIImage(width: Int, height: Int): UIImage? {
  if (width <= 0 || height <= 0) return null

  val size = CGSizeMake(width.toDouble(), height.toDouble())
  UIGraphicsBeginImageContextWithOptions(size, false, 1.0)

  val context = UIGraphicsGetCurrentContext()
  context?.let { ctx ->
    platform.CoreGraphics.CGContextSetRGBFillColor(ctx, 0.6, 0.6, 0.6, 0.8)
    platform.CoreGraphics.CGContextFillRect(
      ctx,
      CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
    )
  }

  val image = UIGraphicsGetImageFromCurrentImageContext()
  UIGraphicsEndImageContext()
  return image
}
