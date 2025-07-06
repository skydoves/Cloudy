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
   * @property bitmap The resulting blurred bitmap. May be null if the blur
   *                 operation completed but no bitmap was generated (e.g., in preview mode).
   */
  @Immutable
  public data class Success(public val bitmap: PlatformBitmap?) : CloudyState

  /**
   * Represents a failed blur processing operation.
   * @property throwable The exception that caused the blur operation to fail.
   *                    This can be used for error reporting and debugging.
   */
  @Immutable
  public data class Error(val throwable: Throwable) : CloudyState
}
