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
 * Creates a new `PlatformBitmap` with the same width, height, and configuration as the original.
 *
 * If the original bitmap's configuration is null, the new bitmap uses `ARGB_8888` as the default configuration.
 *
 * @return A new `PlatformBitmap` instance compatible with the original.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  return PlatformBitmap(
    Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
  )
}

/**
 * Releases the memory used by the underlying Android bitmap if it has not already been recycled.
 */
public actual fun PlatformBitmap.dispose() {
  if (!bitmap.isRecycled) {
    bitmap.recycle()
  }
}

/**
 * Wraps this Android [Bitmap] in a [PlatformBitmap] for platform-agnostic bitmap handling.
 *
 * @return A [PlatformBitmap] instance containing this [Bitmap].
 */
public fun Bitmap.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Returns the underlying Android [Bitmap] from this [PlatformBitmap] instance.
 *
 * @return The wrapped Android [Bitmap].
 */
public fun PlatformBitmap.toAndroidBitmap(): Bitmap = bitmap
