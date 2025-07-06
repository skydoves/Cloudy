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

/**
 * Platform-specific bitmap representation that abstracts the underlying bitmap implementation
 * across different platforms (Android Bitmap, iOS UIImage, etc.).
 */
@Immutable
public expect class PlatformBitmap {
  /**
   * The width of the bitmap in pixels.
   */
  public val width: Int

  /**
   * The height of the bitmap in pixels.
   */
  public val height: Int

  /**
   * Whether this bitmap is recyclable/mutable.
   */
  public val isRecyclable: Boolean
}

/**
 * Creates a compatible bitmap with the same dimensions and configuration.
 */
public expect fun PlatformBitmap.createCompatible(): PlatformBitmap

/**
 * Disposes/recycles the bitmap to free up memory.
 */
public expect fun PlatformBitmap.dispose()
