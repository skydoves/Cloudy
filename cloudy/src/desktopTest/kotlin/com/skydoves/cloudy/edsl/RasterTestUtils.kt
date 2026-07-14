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
package com.skydoves.cloudy.edsl

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import kotlin.math.abs

/** Draws [shader] over an opaque [size]x[size] raster and returns its RGBA_8888 bytes. */
internal fun rasterize(shader: Shader, size: Int): ByteArray {
  val info = ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
  return Surface.makeRaster(info).use { surface ->
    Paint().use { paint ->
      paint.shader = shader
      surface.canvas.drawPaint(paint)
    }
    Bitmap().use { bitmap ->
      bitmap.allocPixels(info)
      surface.readPixels(bitmap, 0, 0)
      bitmap.readPixels() ?: error("readPixels returned null")
    }
  }
}

/** Mean absolute per-byte difference between two equally sized RGBA buffers (0..255 scale). */
internal fun meanAbsDiff(a: ByteArray, b: ByteArray): Double {
  require(a.size == b.size) { "buffers differ in size: ${a.size} vs ${b.size}" }
  var sum = 0L
  for (i in a.indices) sum += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
  return sum.toDouble() / a.size
}
