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
 * Applies a cross-platform blur effect to the current modifier.
 *
 * This modifier uses GPU-accelerated blur when available for optimal performance,
 * with a CPU-based fallback for older platforms.
 *
 * ## Platform Behavior
 *
 * | Platform | Implementation | State Returned |
 * |----------|----------------|----------------|
 * | iOS | Skia BlurEffect (Metal GPU) | [CloudyState.Success.Applied] |
 * | Android 31+ | RenderEffect (GPU) | [CloudyState.Success.Applied] |
 * | Android 30- | Native C++ (CPU) | [CloudyState.Success.Captured] |
 *
 * ## Success State Types
 *
 * - **[CloudyState.Success.Applied]**: GPU blur applied directly in rendering pipeline.
 *   No bitmap is available (GPU→CPU extraction avoided for performance).
 *
 * - **[CloudyState.Success.Captured]**: CPU blur completed with captured bitmap.
 *   The blurred bitmap is available via [CloudyState.Success.Captured.bitmap].
 *
 * ## Example Usage
 *
 * ```kotlin
 * Box(
 *   modifier = Modifier.cloudy(
 *     radius = 15,
 *     onStateChanged = { state ->
 *       when (state) {
 *         is CloudyState.Success.Applied -> {
 *           // GPU blur applied, no bitmap available
 *         }
 *         is CloudyState.Success.Captured -> {
 *           // CPU blur done, bitmap available: state.bitmap
 *         }
 *         is CloudyState.Loading -> { /* Processing */ }
 *         is CloudyState.Error -> { /* Handle error */ }
 *         CloudyState.Nothing -> { /* Initial state */ }
 *       }
 *     }
 *   )
 * )
 * ```
 *
 * @param radius The blur radius in pixels for both the x and y axes. Must be non-negative.
 *               Converted to sigma internally using `sigma = radius / 2.0`.
 *               On Android API 30 and below, values above 25 use iterative passes.
 * @param enabled If false, disables the blur effect and returns the original modifier.
 * @param onStateChanged Callback invoked when the blur state changes.
 *        Check the state type to determine if a bitmap is available.
 * @param debugTag Optional tag for identifying this modifier instance in debug traces.
 * @return A [Modifier] with the blur effect applied.
 *
 * @see CloudyState.Success.Applied
 * @see CloudyState.Success.Captured
 */
@Composable
public expect fun Modifier.cloudy(
  @androidx.annotation.IntRange(from = 0) radius: Int = 10,
  enabled: Boolean = true,
  onStateChanged: (CloudyState) -> Unit = {},
  debugTag: String = "",
): Modifier
