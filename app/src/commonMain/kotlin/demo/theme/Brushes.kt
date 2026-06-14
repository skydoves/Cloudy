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
package demo.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance

/**
 * The app's signature "sky" background — a soft vertical gradient that replaces
 * the old flat, saturated app-bar block. Night-sky indigo in dark mode, an airy
 * daydream wash in light mode. Derived from the active color scheme so it also
 * respects a force-set theme.
 */
@Composable
internal fun rememberSkyBackgroundBrush(): Brush {
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  return remember(isDark) {
    Brush.verticalGradient(
      colors = if (isDark) {
        listOf(nightSkyTop, nightSkyBottom)
      } else {
        listOf(daySkyTop, daySkyBottom)
      },
    )
  }
}
