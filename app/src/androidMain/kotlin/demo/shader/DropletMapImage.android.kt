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

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android droplet-map upload: `Bitmap.setPixels` consumes exactly the `0xAARRGGBB` int layout the
 * generator produces (Android's own packed-int format), so no channel reorder is needed.
 *
 * The alpha channel here carries the coverage/height DATA, not transparency. ARGB_8888 bitmaps store
 * premultiplied by default, which would multiply R,G (the encoded normals) by A and zero them wherever
 * coverage is low — collapsing the normal field to `(0,0)` and killing the refraction. So this marks
 * the bitmap non-premultiplied BEFORE `setPixels`, matching the skiko actual's `UNPREMUL` install, so
 * the `BitmapShader` the mirage binder wraps samples the encoded channels verbatim.
 */
internal actual fun argbToImageBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap {
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  bitmap.setHasAlpha(true)
  bitmap.isPremultiplied = false
  bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
  return bitmap.asImageBitmap()
}
