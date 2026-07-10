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
package com.skydoves.cloudy.internal

/**
 * How a blur renders on Android, resolved per draw. Replaces the old when-3-branch/strategy-factory
 * dispatch (API 31+ RenderEffect, API < 31 CPU legacy, else scrim). Skiko has no ladder — it is always
 * the RenderEffect (Clear) path.
 *
 *  - [Clear]:    GPU RenderEffect (API 31+).
 *  - [Hazy]:     CPU legacy blur (API < 31 with cpuBlurEnabled).
 *  - [Overcast]: scrim fallback (API < 31 without cpuBlurEnabled).
 */
internal enum class Visibility {
  Clear,
  Hazy,
  Overcast,
  ;

  companion object {
    fun resolve(isApi31Plus: Boolean, cpuBlurEnabled: Boolean): Visibility = when {
      isApi31Plus -> Clear
      cpuBlurEnabled -> Hazy
      else -> Overcast
    }
  }
}
