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

import android.os.Build
import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

/**
 * Android implementation of the cloudy modifier that selects
 * the appropriate blur strategy based on the current API level.
 */
@Composable
public actual fun Modifier.cloudy(
  @IntRange(from = 0) radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit,
  debugTag: String,
): Modifier {
  require(radius >= 0) { "Blur radius must be non-negative, but was $radius" }

  if (!enabled) {
    return this
  }

  if (LocalInspectionMode.current) {
    return this.blur(radius = radius.dp)
  }

  val strategy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    CloudyRenderEffectStrategy
  } else {
    CloudyLegacyBlurStrategy
  }

  return strategy.apply(
    modifier = this,
    radius = radius,
    onStateChanged = onStateChanged,
    debugTag = debugTag,
  )
}
