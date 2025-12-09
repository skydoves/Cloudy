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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo

/**
 * WASM implementation of [PlatformBitmap] that wraps Skia Bitmap.
 *
 * On WASM, we use Skia's Bitmap directly since there's no native image type.
 */
@Immutable
public actual class PlatformBitmap(
  /**
   * The underlying Skia Bitmap.
   */
  public val bitmap: Bitmap,
) {

  public actual val width: Int
    get() = bitmap.width

  public actual val height: Int
    get() = bitmap.height

  public actual val isRecyclable: Boolean
    get() = !bitmap.isNull
}

/**
 * Creates a new [PlatformBitmap] with the same dimensions as the original bitmap.
 *
 * @return A new [PlatformBitmap] instance compatible in size with the original.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  val imageInfo = ImageInfo.makeN32Premul(width, height)
  val newBitmap = Bitmap()
  newBitmap.allocPixels(imageInfo)
  return PlatformBitmap(newBitmap)
}

/**
 * Releases resources associated with this bitmap.
 *
 * On WASM, this closes the Skia bitmap.
 */
public actual fun PlatformBitmap.dispose() {
  bitmap.close()
}

/**
 * Wraps this Skia [Bitmap] in a [PlatformBitmap].
 *
 * @return A [PlatformBitmap] containing the current [Bitmap].
 */
public fun Bitmap.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Returns the underlying Skia [Bitmap] associated with this [PlatformBitmap].
 *
 * @return The Skia [Bitmap] instance wrapped by this [PlatformBitmap].
 */
public fun PlatformBitmap.toSkiaBitmap(): Bitmap = bitmap

/**
 * Converts a Compose [ImageBitmap] to a Skia [Bitmap].
 *
 * @return A Skia [Bitmap] with the same content as the [ImageBitmap].
 */
public fun ImageBitmap.toSkiaBitmap(): Bitmap {
  val width = this.width
  val height = this.height
  val pixelMap = this.toPixelMap()

  val imageInfo = ImageInfo.makeN32Premul(width, height)
  val skiaBitmap = Bitmap()
  skiaBitmap.allocPixels(imageInfo)

  val bytesPerPixel = 4
  val totalBytes = width * height * bytesPerPixel
  val byteArray = ByteArray(totalBytes)

  for (y in 0 until height) {
    for (x in 0 until width) {
      val pixel = pixelMap[x, y]
      val offset = (y * width + x) * bytesPerPixel

      // BGRA format for Skia
      byteArray[offset] = (pixel.blue * 255).toInt().toByte()
      byteArray[offset + 1] = (pixel.green * 255).toInt().toByte()
      byteArray[offset + 2] = (pixel.red * 255).toInt().toByte()
      byteArray[offset + 3] = (pixel.alpha * 255).toInt().toByte()
    }
  }

  skiaBitmap.installPixels(imageInfo, byteArray, width * 4)
  return skiaBitmap
}

/**
 * Converts a Skia [Bitmap] to a Compose [ImageBitmap].
 *
 * @return An [ImageBitmap] with the same content as the Skia [Bitmap].
 */
public fun Bitmap.asImageBitmap(): ImageBitmap = this.asComposeImageBitmap()
