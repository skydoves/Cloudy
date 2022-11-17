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

import android.graphics.Bitmap

/** Represents the state of [Cloudy] composable function. */
public sealed interface CloudyState {

  /** Represents the state of [Cloudy] process doesn't started. */
  public object Nothing : CloudyState

  /** Represents the state of [Cloudy] process is ongoing. */
  public object Loading : CloudyState

  /** Represents the state of [Cloudy] process is successful. */
  public data class Success(public val bitmap: Bitmap?) : CloudyState

  /** Represents the state of [Cloudy] process is failed. */
  public data class Error(val throwable: Throwable) : CloudyState
}
