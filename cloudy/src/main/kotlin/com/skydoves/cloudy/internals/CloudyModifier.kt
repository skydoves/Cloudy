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
package com.skydoves.cloudy.internals

import android.graphics.Bitmap
import androidx.compose.ui.draw.DrawModifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

/**
 * A modifier that extends [DrawModifier] to draw the given [bitmap].
 *
 * @property bitmap A target bitmap to be drawn.
 */
internal class CloudyModifier(
  private val bitmap: Bitmap?
) : DrawModifier {

  override fun ContentDrawScope.draw() {
    if (bitmap != null) {
      drawImage(bitmap.asImageBitmap())
    }
  }
}
