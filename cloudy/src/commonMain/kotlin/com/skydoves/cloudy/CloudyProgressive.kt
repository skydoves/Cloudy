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

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * Configuration for progressive (gradient) blur effects.
 *
 * Progressive blur varies the blur intensity based on position within the composable,
 * creating effects like scroll edge fades, vignettes, or directional blurs.
 *
 * ## Platform Support
 *
 * | Platform | API Level | Progressive Blur Support |
 * |----------|-----------|-------------------------|
 * | Android | 33+ | Full (AGSL shader) |
 * | Android | 32- | Falls back to uniform blur with warning |
 * | iOS/macOS/Desktop/WASM | - | Full (Skia shader) |
 *
 * ## Usage
 *
 * ```kotlin
 * // Uniform blur (default)
 * Modifier.cloudy(sky = sky, progressive = CloudyProgressive.None)
 *
 * // Top fades to clear (for top scroll edge)
 * Modifier.cloudy(sky = sky, progressive = CloudyProgressive.TopToBottom())
 *
 * // Bottom fades to clear (for bottom scroll edge)
 * Modifier.cloudy(sky = sky, progressive = CloudyProgressive.BottomToTop())
 *
 * // Vignette effect (blur at edges, clear in center)
 * Modifier.cloudy(sky = sky, progressive = CloudyProgressive.Edges())
 * ```
 *
 * @see CloudyProgressive.None
 * @see CloudyProgressive.TopToBottom
 * @see CloudyProgressive.BottomToTop
 * @see CloudyProgressive.Edges
 */
@Stable
public sealed interface CloudyProgressive {

  /**
   * Uniform blur across the entire region.
   *
   * This is the default behavior where blur intensity is constant
   * throughout the composable.
   */
  @Immutable
  public data object None : CloudyProgressive

  /**
   * Blur intensity decreases from top to bottom.
   *
   * Creates a "fog lifting" or "scroll fade" effect where the top
   * of the composable is fully blurred and gradually becomes clear
   * towards the bottom.
   *
   * ## Example
   *
   * ```kotlin
   * // Blur starts at top (0%) and fades to clear at 50%
   * CloudyProgressive.TopToBottom(start = 0f, end = 0.5f)
   *
   * // Blur starts at 20% and fades to clear at 80%
   * CloudyProgressive.TopToBottom(start = 0.2f, end = 0.8f)
   * ```
   *
   * @property start Normalized position where blur is at full intensity (0.0 = top).
   *                 Must be in range 0..1 and less than [end].
   * @property end Normalized position where blur reaches zero intensity.
   *              Must be in range 0..1 and greater than [start].
   * @property easing Easing function for the blur transition.
   *                  Defaults to [FastOutSlowInEasing].
   * @throws IllegalArgumentException if [start] >= [end] or values are out of range.
   */
  @Immutable
  public data class TopToBottom(
    val start: Float = 0f,
    val end: Float = CloudyDefaults.PROGRESSIVE_FADE_END,
    val easing: Easing = FastOutSlowInEasing,
  ) : CloudyProgressive {
    init {
      require(start in 0f..1f) { "start must be in 0..1, but was $start" }
      require(end in 0f..1f) { "end must be in 0..1, but was $end" }
      require(start < end) { "start ($start) must be less than end ($end)" }
    }
  }

  /**
   * Blur intensity decreases from bottom to top.
   *
   * Creates a "rising from mist" or "bottom scroll fade" effect where
   * the bottom of the composable is fully blurred and gradually becomes
   * clear towards the top.
   *
   * ## Example
   *
   * ```kotlin
   * // Blur starts at bottom (100%) and fades to clear at 50%
   * CloudyProgressive.BottomToTop(start = 1f, end = 0.5f)
   *
   * // Blur starts at 80% and fades to clear at 20%
   * CloudyProgressive.BottomToTop(start = 0.8f, end = 0.2f)
   * ```
   *
   * @property start Normalized position where blur is at full intensity (1.0 = bottom).
   *                 Must be in range 0..1 and greater than [end].
   * @property end Normalized position where blur reaches zero intensity.
   *              Must be in range 0..1 and less than [start].
   * @property easing Easing function for the blur transition.
   *                  Defaults to [FastOutSlowInEasing].
   * @throws IllegalArgumentException if [start] <= [end] or values are out of range.
   */
  @Immutable
  public data class BottomToTop(
    val start: Float = 1f,
    val end: Float = 1f - CloudyDefaults.PROGRESSIVE_FADE_END,
    val easing: Easing = FastOutSlowInEasing,
  ) : CloudyProgressive {
    init {
      require(start in 0f..1f) { "start must be in 0..1, but was $start" }
      require(end in 0f..1f) { "end must be in 0..1, but was $end" }
      require(start > end) { "start ($start) must be greater than end ($end) for BottomToTop" }
    }
  }

  /**
   * Blur intensity at edges, clear in center.
   *
   * Creates a vignette-like effect where all edges of the composable
   * are blurred and the center is clear.
   *
   * ## Example
   *
   * ```kotlin
   * // 20% fade distance from each edge
   * CloudyProgressive.Edges(fadeDistance = 0.2f)
   *
   * // 30% fade distance with custom easing
   * CloudyProgressive.Edges(fadeDistance = 0.3f, easing = LinearEasing)
   * ```
   *
   * @property fadeDistance Normalized distance from each edge where blur
   *                        transitions from full to zero. Must be in range 0..0.5
   *                        (0.5 means edges meet in the center).
   * @property easing Easing function for the blur transition.
   *                  Defaults to [FastOutSlowInEasing].
   * @throws IllegalArgumentException if [fadeDistance] is out of range.
   */
  @Immutable
  public data class Edges(
    val fadeDistance: Float = CloudyDefaults.EDGES_FADE_DISTANCE,
    val easing: Easing = FastOutSlowInEasing,
  ) : CloudyProgressive {
    init {
      require(fadeDistance in 0f..0.5f) {
        "fadeDistance must be in 0..0.5, but was $fadeDistance"
      }
    }
  }
}
