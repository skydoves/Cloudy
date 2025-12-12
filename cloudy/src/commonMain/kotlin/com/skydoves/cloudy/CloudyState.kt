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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

/**
 * Represents the state of the blur processing operation in the Cloudy library.
 * This sealed interface provides different states that indicate the current status
 * of the blur effect processing.
 */
@Stable
public sealed interface CloudyState {

  /**
   * Represents the initial state where no blur processing has been initiated.
   * This is the default state before any blur operation begins.
   */
  @Stable
  public data object Nothing : CloudyState

  /**
   * Represents the state where blur processing is currently in progress.
   * This state indicates that the blur operation is running and the UI
   * should show appropriate loading indicators if needed.
   */
  @Stable
  public data object Loading : CloudyState

  /**
   * Represents the successful completion of the blur processing operation.
   *
   * This sealed interface has two subtypes:
   * - [Applied]: GPU-accelerated blur was applied in the rendering pipeline (no bitmap available)
   * - [Captured]: CPU-based blur completed with a captured bitmap
   *
   * ## Platform Behavior
   * - **iOS**: Returns [Applied] (Skia ImageFilter via Metal GPU)
   * - **Android 31+**: Returns [Applied] (RenderEffect GPU)
   * - **Android 30-**: Returns [Captured] (Native C++ CPU blur with bitmap)
   *
   * @see Applied
   * @see Captured
   */
  @Stable
  public sealed interface Success : CloudyState {

    /**
     * GPU-accelerated blur was applied directly in the rendering pipeline.
     *
     * This state is returned when using GPU-based blur implementations:
     * - iOS: Skia ImageFilter.makeBlur() with Metal backend
     * - Android 31+: RenderEffect.createBlurEffect()
     *
     * No bitmap is extracted for performance reasons (avoiding GPU→CPU readback).
     */
    @Immutable
    public data object Applied : Success

    /**
     * CPU-based blur completed with a captured bitmap.
     *
     * This state is returned when using CPU-based blur implementations:
     * - Android 30 and below: Native C++ RenderScriptToolkit
     *
     * Note: Uses @Stable instead of @Immutable because PlatformBitmap wraps
     * platform-specific bitmap types (e.g., Android Bitmap) that are mutable.
     *
     * @property bitmap The resulting blurred bitmap.
     */
    @Stable
    public data class Captured(public val bitmap: PlatformBitmap) : Success

    /**
     * Scrim-only fallback was applied (no blur processing).
     *
     * This state is returned when CPU-based blur is disabled on Android 30 and below:
     * - When [CloudyDefaults.CPP_BLUR_ENABLED] is `false` (default)
     * - A semi-transparent scrim overlay is shown instead of blur
     *
     * This follows the Haze library approach for better performance on older devices.
     */
    @Immutable
    public data object Scrim : Success
  }

  /**
   * Represents a failed blur processing operation.
   * @property throwable The exception that caused the blur operation to fail.
   *                    This can be used for error reporting and debugging.
   */
  @Immutable
  public data class Error(val throwable: Throwable) : CloudyState
}
