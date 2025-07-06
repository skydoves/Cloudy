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

import kotlinx.cinterop.ExperimentalForeignApi

internal fun createTestPlatformBitmap(width: Int, height: Int): PlatformBitmap {
  return PlatformBitmap(createTestUIImage(width, height))
}

internal fun createTestUIImage(width: Int, height: Int): platform.UIKit.UIImage {
  val size = platform.CoreGraphics.CGSizeMake(width.toDouble(), height.toDouble())
  platform.UIKit.UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
  val image = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
  platform.UIKit.UIGraphicsEndImageContext()
  return image ?: platform.UIKit.UIImage()
}
