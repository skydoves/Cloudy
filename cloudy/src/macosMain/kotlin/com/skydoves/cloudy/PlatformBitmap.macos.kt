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
import platform.AppKit.NSBitmapImageRep
import platform.AppKit.NSImage
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.posix.free
import platform.posix.malloc
import kotlin.math.roundToInt

/**
 * macOS implementation of [PlatformBitmap] that wraps macOS NSImage.
 */
@Immutable
public actual class PlatformBitmap(
  /**
   * The underlying macOS NSImage.
   */
  public val image: NSImage,
) {

  public actual val width: Int
    get() = image.size.useContents { width }.roundToInt()

  public actual val height: Int
    get() = image.size.useContents { height }.roundToInt()

  public actual val isRecyclable: Boolean
    get() = true
}

/**
 * Creates a new [PlatformBitmap] with the same dimensions as the original image.
 *
 * @return A new [PlatformBitmap] instance compatible in size with the original.
 */
public actual fun PlatformBitmap.createCompatible(): PlatformBitmap {
  val size = CGSizeMake(width.toDouble(), height.toDouble())
  val newImage = NSImage(size = size)
  return PlatformBitmap(newImage)
}

/**
 * Releases resources associated with this bitmap.
 *
 * On macOS, this function performs no action since memory management is handled by ARC.
 */
public actual fun PlatformBitmap.dispose() {
  // macOS uses ARC for memory management
}

/**
 * Wraps this [NSImage] in a [PlatformBitmap].
 *
 * @return A [PlatformBitmap] containing the current [NSImage].
 */
public fun NSImage.toPlatformBitmap(): PlatformBitmap = PlatformBitmap(this)

/**
 * Returns the underlying [NSImage] associated with this [PlatformBitmap].
 *
 * @return The native [NSImage] instance wrapped by this [PlatformBitmap].
 */
public fun PlatformBitmap.toNSImage(): NSImage = image

/**
 * Converts a Compose [ImageBitmap] to a macOS [NSImage].
 *
 * @return An [NSImage] with the same content as the [ImageBitmap], or null if conversion fails.
 */
public fun ImageBitmap.toNSImage(): NSImage? {
  return try {
    val width = this.width
    val height = this.height

    if (width <= 0 || height <= 0) return null

    val pixelMap = this.toPixelMap()

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or
      kCGBitmapByteOrder32Big

    val bytesPerPixel = 4
    val bytesPerRow = width * bytesPerPixel
    val totalBytes = height * bytesPerRow
    val pixelData = malloc(totalBytes.toULong())

    pixelData?.let { data ->
      val pixels = data.reinterpret<ByteVar>()

      for (y in 0 until height) {
        for (x in 0 until width) {
          val pixel = pixelMap[x, y]
          val offset = (y * width + x) * bytesPerPixel

          pixels[offset] = (pixel.red * 255).toInt().toByte()
          pixels[offset + 1] = (pixel.green * 255).toInt().toByte()
          pixels[offset + 2] = (pixel.blue * 255).toInt().toByte()
          pixels[offset + 3] = (pixel.alpha * 255).toInt().toByte()
        }
      }

      val context = CGBitmapContextCreate(
        data = data,
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = bytesPerRow.toULong(),
        space = colorSpace,
        bitmapInfo = bitmapInfo,
      )

      context?.let { ctx ->
        val cgImage = CGBitmapContextCreateImage(ctx)
        cgImage?.let {
          val size = CGSizeMake(width.toDouble(), height.toDouble())
          val nsImage = NSImage(size = size)
          val rep = NSBitmapImageRep(cGImage = it)
          nsImage.addRepresentation(rep)

          CGContextRelease(ctx)
          CGImageRelease(it)
          free(data)
          CGColorSpaceRelease(colorSpace)

          return nsImage
        }
      }

      free(data)
    }

    CGColorSpaceRelease(colorSpace)
    null
  } catch (_: Exception) {
    null
  }
}

/**
 * Converts an [NSImage] to a Compose [ImageBitmap].
 *
 * @return An [ImageBitmap] with the same content as the [NSImage], or null if conversion fails.
 */
@Suppress("UNCHECKED_CAST")
public fun NSImage.asImageBitmap(): ImageBitmap? {
  return try {
    val representations = this.representations as? List<NSBitmapImageRep> ?: return null
    val rep = representations.firstOrNull() ?: return null
    val cgImage = rep.CGImage ?: return null

    val width = this.size.useContents { width }.roundToInt()
    val height = this.size.useContents { height }.roundToInt()

    if (width <= 0 || height <= 0) return null

    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bytesPerPixel = 4
    val bytesPerRow = width * bytesPerPixel
    val totalBytes = height * bytesPerRow

    val pixelData = malloc(totalBytes.toULong())

    pixelData?.let { data ->
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
        bitmapInfo = bitmapInfo,
      )

      context?.let { ctx ->
        val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
        CGContextDrawImage(ctx, rect, cgImage)

        val imageInfo = ImageInfo.makeN32Premul(width, height)
        val skiaBitmap = Bitmap()
        skiaBitmap.allocPixels(imageInfo)

        val pixels = data.reinterpret<ByteVar>()
        val byteArray = ByteArray(totalBytes)

        for (y in 0 until height) {
          for (x in 0 until width) {
            val srcOffset = (y * width + x) * bytesPerPixel
            val dstOffset = (y * width + x) * bytesPerPixel

            byteArray[dstOffset] = pixels[srcOffset + 2]
            byteArray[dstOffset + 1] = pixels[srcOffset + 1]
            byteArray[dstOffset + 2] = pixels[srcOffset]
            byteArray[dstOffset + 3] = pixels[srcOffset + 3]
          }
        }

        skiaBitmap.installPixels(imageInfo, byteArray, width * 4)

        CGContextRelease(ctx)
        free(data)
        CGColorSpaceRelease(colorSpace)

        return skiaBitmap.asComposeImageBitmap()
      }

      free(data)
    }

    CGColorSpaceRelease(colorSpace)
    null
  } catch (_: Exception) {
    null
  }
}
