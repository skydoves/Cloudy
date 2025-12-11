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

import androidx.compose.ui.graphics.Color

/**
 * Default values and constants for Cloudy blur effects.
 */
public object CloudyDefaults {

  /**
   * Default blur radius for background blur effects.
   *
   * This value provides a moderate blur suitable for glassmorphism effects
   * without excessive performance overhead.
   */
  public const val BackgroundRadius: Int = 20

  /**
   * Default end position for progressive blur fade.
   *
   * For [CloudyProgressive.TopToBottom], blur fades from 0% to this value (50%).
   * For [CloudyProgressive.BottomToTop], blur fades from 100% to (1 - this value) (50%).
   */
  public const val ProgressiveFadeEnd: Float = 0.5f

  /**
   * Default fade distance for [CloudyProgressive.Edges].
   *
   * Represents the normalized distance from each edge where blur
   * transitions from full intensity to zero.
   */
  public const val EdgesFadeDistance: Float = 0.2f

  /**
   * Default setting for CPU-based blur on Android 30 and below.
   *
   * When `false` (default), CPU blur is disabled and a scrim overlay is shown instead.
   * This follows the Haze library approach for better performance on older devices.
   *
   * When `true`, CPU-based blur is enabled, which may impact performance
   * on older devices with large blur radii or frequent updates.
   *
   * This setting only affects Android API 30 and below. GPU-accelerated blur
   * on API 31+ is always enabled regardless of this setting.
   */
  public const val CpuBlurEnabled: Boolean = false

  /**
   * Default scrim color used when CPU blur is disabled.
   *
   * This semi-transparent overlay provides a visual effect similar to blur
   * without the performance overhead of CPU-based blur processing.
   */
  public val DefaultScrimColor: Color = Color.Black.copy(alpha = 0.3f)
}
