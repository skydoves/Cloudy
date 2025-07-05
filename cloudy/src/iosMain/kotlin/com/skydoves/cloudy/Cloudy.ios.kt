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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

@Composable
public actual fun Modifier.cloudy(
  radius: Int,
  enabled: Boolean,
  onStateChanged: (CloudyState) -> Unit
): Modifier {
  if (!enabled) {
    return this
  }

  // For iOS, we use the standard Compose blur modifier
  // In the future, this can be enhanced with Core Image blur for better performance
  LaunchedEffect(radius) {
    onStateChanged(CloudyState.Loading)
    // Simulate processing time
    kotlinx.coroutines.delay(10)
    onStateChanged(CloudyState.Success(null))
  }

  return this.blur(radius = radius.dp)
}
