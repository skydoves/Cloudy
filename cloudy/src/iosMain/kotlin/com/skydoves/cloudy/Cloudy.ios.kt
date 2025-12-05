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
 * iOS implementation of the cloudy modifier that applies blur effects to composables.
 *
 * This implementation uses Skia's GPU-accelerated blur via [BlurEffect] which is backed
 * by Metal on iOS for optimal performance. The blur is applied directly in the rendering
 * pipeline without bitmap extraction.
 *
 * ## Performance
 * - Uses GPU acceleration via Skia's Metal backend
 * - No bitmap extraction (GPU→CPU readback) for maximum performance
 * - Returns [CloudyState.Success.Applied] to indicate GPU blur was applied
 *
 * ## Sigma Conversion
 * The blur radius is converted to sigma using: `sigma = radius / 2.0`
 *
 * @param radius The blur radius in pixels. Higher values create more blur.
 * @param enabled Whether the blur effect is enabled. When false, returns the original modifier unchanged.
 * @param onStateChanged Callback that receives updates about the blur processing state.
 *        On iOS, this will receive [CloudyState.Success.Applied] (no bitmap available).
 * @return Modified Modifier with blur effect applied.
 */
/**
 * Applies an iOS GPU-accelerated blur to this Modifier when enabled and radius is greater than zero.
 *
 * @param radius The blur radius (must be >= 0).
 * @param enabled If `false`, the original Modifier is returned unchanged.
 * @param onStateChanged Callback invoked with `CloudyState.Success.Applied` when the blur radius changes and the effect is applied.
 * @param debugTag Unused debug tag available for callers.
 * @return A Modifier with a GPU-accelerated blur renderEffect applied when `enabled` is `true` and `radius` > 0; otherwise the original Modifier.
 * @throws IllegalArgumentException if `radius` is negative.
 */
@Composable
public actual fun Modifier.cloudy(
  @IntRange(from = 0) radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit,
  debugTag: String,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  // Notify state change only when radius changes to avoid infinite recomposition loops
  LaunchedEffect(radius) {
    onStateChanged(CloudyState.Success.Applied)
  }

  if (radius == 0) {
    return this
  }

  // Convert radius to sigma: sigma = radius / 2.0
  val sigma = radius / 2.0f

  // Apply GPU-accelerated blur using Skia's BlurEffect
  // Skia handles large sigma values internally via progressive downsampling
  return this.graphicsLayer {
    renderEffect = BlurEffect(
      radiusX = sigma,
      radiusY = sigma,
      edgeTreatment = TileMode.Clamp,
    )
  }
}