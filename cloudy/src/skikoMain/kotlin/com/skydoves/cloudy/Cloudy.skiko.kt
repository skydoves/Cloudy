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

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Skiko implementation using GPU-accelerated BlurEffect.
 * This implementation is shared across iOS, macOS, JVM Desktop, and WASM platforms.
 */
@Composable
public actual fun Modifier.cloudy(
  @IntRange(from = 0) radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  // Notify state change only when radius changes to avoid infinite recomposition loops
  LaunchedEffect(radius) {
    if (radius > 0) {
      onStateChanged(CloudyState.Success.Applied)
    }
  }

  if (radius == 0) {
    return this
  }

  // BlurEffect's radiusX/radiusY are blur *radii* in pixels, not Gaussian sigmas.
  // Compose's Skiko backend converts the radius to a sigma internally (using the same
  // sigma = 0.57735 * radius + 0.5 as HWUI) before handing it to Skia, so pass the
  // user-supplied radius through directly to match Modifier.blur and the Android path.
  val blurRadius = radius.toFloat()

  // Apply GPU-accelerated blur using Skia's BlurEffect
  // Skia handles large radius values internally via progressive downsampling
  return this.graphicsLayer {
    renderEffect = BlurEffect(
      radiusX = blurRadius,
      radiusY = blurRadius,
      edgeTreatment = TileMode.Clamp,
    )
  }
}
