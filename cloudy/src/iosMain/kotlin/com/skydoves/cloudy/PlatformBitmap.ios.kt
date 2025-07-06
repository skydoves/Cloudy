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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
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
 * This creates a UIImage with the same dimensions as the original bitmap.
 * Note: This is a simplified implementation for demonstration purposes.
 * In production, you would need proper pixel data extraction.
 */
public fun ImageBitmap.toUIImage(): UIImage? {
  return try {
    // Get dimensions from the ImageBitmap
    val width = this.width
    val height = this.height

    // Create a UIImage context with the same dimensions
    val size = CGSizeMake(width.toDouble(), height.toDouble())
    UIGraphicsBeginImageContextWithOptions(size, false, 1.0)

    // For this demo implementation, we'll create a semi-transparent gray image
    // that represents the content to be blurred
    val context = UIGraphicsGetCurrentContext()
    context?.let { ctx ->
      // Use a semi-transparent gray color to represent the captured content
      platform.CoreGraphics.CGContextSetRGBFillColor(ctx, 0.6, 0.6, 0.6, 0.8)
      platform.CoreGraphics.CGContextFillRect(
        ctx,
        platform.CoreGraphics.CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
      )
    }

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    image
  } catch (_: Exception) {
    null
  }
}

/**
 * Converts iOS [UIImage] to Compose [ImageBitmap].
 * This creates an ImageBitmap with the same dimensions as the UIImage.
 * Note: This is a simplified implementation for demonstration purposes.
 * In production, you would need proper pixel data extraction.
 */
public fun UIImage.asImageBitmap(): ImageBitmap? {
  return try {
    // Get dimensions from UIImage
    val width = this.size.useContents { width }.toInt()
    val height = this.size.useContents { height }.toInt()

    // Create a simple Skia bitmap with the same dimensions
    val imageInfo = ImageInfo.makeN32Premul(width, height)
    val skiaBitmap = Bitmap()
    skiaBitmap.allocPixels(imageInfo)

    // For this demo, fill with a gray color that represents the blurred content
    // In production, you would extract actual pixel data from the UIImage
    val pixels = ByteArray(width * height) { 0xFF999999.toByte() }
    skiaBitmap.installPixels(imageInfo, pixels, width * 4)

    skiaBitmap.asComposeImageBitmap()
  } catch (_: Exception) {
    null
  }
}
