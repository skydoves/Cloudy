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

import androidx.compose.runtime.Immutable
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
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
