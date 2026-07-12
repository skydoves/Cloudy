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
package demo.shader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Skiko (desktop / iOS / macOS / wasmJs) droplet-map upload. The generator packs `0xAARRGGBB` ints;
 * skia's `RGBA_8888` wants bytes in R, G, B, A order, so this repacks each int into four bytes. The
 * alpha channel here carries the coverage/height DATA (not transparency), so the buffer is installed
 * `UNPREMUL` — a PREMUL install would multiply R/G/B by A and corrupt the encoded normals where
 * coverage is low.
 */
internal actual fun argbToImageBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap {
  val rgba = ByteArray(width * height * 4)
  var o = 0
  for (p in pixels) {
    rgba[o] = ((p ushr 16) and 0xFF).toByte() // R
    rgba[o + 1] = ((p ushr 8) and 0xFF).toByte() // G
    rgba[o + 2] = (p and 0xFF).toByte() // B
    rgba[o + 3] = ((p ushr 24) and 0xFF).toByte() // A
    o += 4
  }
  val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
  val bitmap = Bitmap()
  bitmap.allocPixels(info)
  bitmap.installPixels(info, rgba, width * 4)
  bitmap.setImmutable()
  return bitmap.asComposeImageBitmap()
}
