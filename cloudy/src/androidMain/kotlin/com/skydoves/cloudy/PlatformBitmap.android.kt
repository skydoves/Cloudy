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
import androidx.compose.runtime.Immutable

/**
 * Android implementation of [PlatformBitmap] that wraps Android's [Bitmap].
 */
@Immutable
public actual class PlatformBitmap(
  /**
   * The underlying Android bitmap.
   */
  public val bitmap: Bitmap
) {

  public actual val width: Int
    get() = bitmap.width

  public actual val height: Int
    get() = bitmap.height

  public actual val isRecyclable: Boolean
    get() = !bitmap.isRecycled && bitmap.isMutable
}

/**
 * Creates a compatible Android bitmap with the same dimensions and configuration.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  return PlatformBitmap(
    Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
  )
}

/**
 * Recycles the Android bitmap to free up memory.
 */
public actual fun PlatformBitmap.dispose() {
  if (!bitmap.isRecycled) {
    bitmap.recycle()
  }
}

/**
 * Converts Android [Bitmap] to [PlatformBitmap].
 */
public fun Bitmap.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Converts [PlatformBitmap] to Android [Bitmap].
 */
public fun PlatformBitmap.toAndroidBitmap(): Bitmap = bitmap
