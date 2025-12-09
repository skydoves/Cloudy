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
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage

/**
 * JVM Desktop implementation of [PlatformBitmap] that wraps Java AWT BufferedImage.
 */
@Immutable
public actual class PlatformBitmap(
  /**
   * The underlying Java AWT BufferedImage.
   */
  public val image: BufferedImage,
) {

  public actual val width: Int
    get() = image.width

  public actual val height: Int
    get() = image.height

  public actual val isRecyclable: Boolean
    get() = true
}

/**
 * Creates a new [PlatformBitmap] with the same dimensions as the original image.
 *
 * @return A new [PlatformBitmap] instance compatible in size with the original.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  val newImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  return PlatformBitmap(newImage)
}

/**
 * Releases resources associated with this bitmap.
 *
 * On JVM, this function flushes the image resources.
 */
public actual fun PlatformBitmap.dispose() {
  image.flush()
}

/**
 * Wraps this [BufferedImage] in a [PlatformBitmap].
 *
 * @return A [PlatformBitmap] containing the current [BufferedImage].
 */
public fun BufferedImage.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Returns the underlying [BufferedImage] associated with this [PlatformBitmap].
 *
 * @return The native [BufferedImage] instance wrapped by this [PlatformBitmap].
 */
public fun PlatformBitmap.toBufferedImage(): BufferedImage = image

/**
 * Converts a Compose [ImageBitmap] to a JVM [BufferedImage].
 *
 * @return A [BufferedImage] with the same content as the [ImageBitmap].
 */
public fun ImageBitmap.toBufferedImage(): BufferedImage = this.toAwtImage()

/**
 * Converts a [BufferedImage] to a Compose [ImageBitmap].
 *
 * @return An [ImageBitmap] with the same content as the [BufferedImage].
 */
public fun BufferedImage.asImageBitmap(): ImageBitmap = this.toComposeImageBitmap()
