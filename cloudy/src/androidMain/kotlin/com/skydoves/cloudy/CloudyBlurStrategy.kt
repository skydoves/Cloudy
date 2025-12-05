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
   * Applies the blur implementation to the provided [modifier].
   *
   * @param modifier The base modifier.
   * @param radius Blur radius in pixels.
   * @param onStateChanged Callback for blur state updates.
   * @param debugTag Optional identifier for debugging logs.
   */
  @Composable
  fun apply(
    modifier: Modifier,
    radius: Int,
    onStateChanged: (CloudyState) -> Unit,
    debugTag: String,
  ): Modifier
}
