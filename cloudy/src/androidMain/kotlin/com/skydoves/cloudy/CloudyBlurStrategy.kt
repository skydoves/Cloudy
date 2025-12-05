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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Strategy interface for applying blur effects on Android.
 *
 * Implementations are platform-specific (RenderEffect for API 31+,
 * legacy bitmap capture for API 30 and below).
 */
internal interface CloudyBlurStrategy {

  /**
   * Builds a Modifier that applies a blur effect using the platform-specific strategy.
   *
   * The returned modifier applies the requested blur radius to the provided base modifier
   * and reports blur state changes via the `onStateChanged` callback. The `debugTag`
   * is used to identify this blur instance in diagnostics.
   *
   * @param modifier The base Modifier to augment with the blur effect.
   * @param radius Blur radius in pixels.
   * @param onStateChanged Callback invoked when the blur state changes.
   * @param debugTag Identifier used for debugging and diagnostics.
   * @return A Modifier that applies the configured blur effect on top of the given modifier.
   */
  @Composable
  fun apply(
    modifier: Modifier,
    radius: Int,
    onStateChanged: (CloudyState) -> Unit,
    debugTag: String,
  ): Modifier
}