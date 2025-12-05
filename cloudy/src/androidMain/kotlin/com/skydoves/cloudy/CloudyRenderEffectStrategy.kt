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

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Blur strategy that uses RenderEffect (API 31+) for GPU-accelerated blur.
 */
internal object CloudyRenderEffectStrategy : CloudyBlurStrategy {

  /**
   * Applies a GPU-accelerated blur to the given [Modifier] using RenderEffect on Android S (API 31) and above,
   * and reports when the blur has been applied.
   *
   * @param radius The blur radius in pixels; a value of 0 skips applying any blur and returns the original modifier.
   * @param onStateChanged Callback invoked with `CloudyState.Success.Applied` when the blur application is triggered.
   * @param debugTag Optional tag for debugging (not used to alter behavior).
   * @return The original modifier when `radius` is 0 or when RenderEffect is unavailable on the device; otherwise
   * a modifier augmented with a graphicsLayer containing the RenderEffect-based blur.
   */
  @Composable
  override fun apply(
    modifier: Modifier,
    radius: Int,
    onStateChanged: (CloudyState) -> Unit,
    debugTag: String,
  ): Modifier {
    LaunchedEffect(radius) {
      onStateChanged(CloudyState.Success.Applied)
    }

    if (radius == 0) {
      return modifier
    }

    val sigma = radius / 2.0f

    return modifier.graphicsLayer {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        renderEffect = RenderEffect
          .createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
          .asComposeRenderEffect()
      }
    }
  }
}