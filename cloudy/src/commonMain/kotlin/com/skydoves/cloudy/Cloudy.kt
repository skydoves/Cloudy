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
 * `Modifier.cloudy()` is a blur modifier that applies blur effects to composables,
 * compatible with all supported platforms.
 *
 * @param radius Radius of the blur along both the x and y axis. Must be non-negative.
 *               On Android, values > 25 are achieved through iterative passes which may affect performance.
 * @param enabled Enabling the blur effects.
 * @param onStateChanged Lambda function that will be invoked when the blur process has been updated.
 */
/**
 * Applies a cross-platform blur effect to the current modifier.
 *
 * @param radius The blur radius in pixels for both the x and y axes. Must be non-negative. On Android, values above 25 may impact performance due to iterative passes.
 * @param enabled If false, disables the blur effect.
 * @param onStateChanged Callback invoked when the blur state changes.
 * @return A [Modifier] with the blur effect applied.
 */
@Composable
public expect fun Modifier.cloudy(
  @androidx.annotation.IntRange(from = 0) radius: Int = 10,
  enabled: Boolean = true,
  onStateChanged: (CloudyState) -> Unit = {}
): Modifier
