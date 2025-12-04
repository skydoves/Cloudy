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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
  background = backgroundDark,
  surface = surfaceDark,
  primary = disneyBluePrimary,
  primaryVariant = disneyBlueDark,
  secondary = disneyGold,
  onBackground = onBackgroundDark,
  onSurface = onBackgroundDark,
  onPrimary = onBackgroundDark,
  onSecondary = backgroundDark,
)

private val LightColorPalette = lightColors(
  background = backgroundLight,
  surface = surfaceLight,
  primary = disneyBluePrimary,
  primaryVariant = disneyBlueDark,
  secondary = disneyGold,
  onBackground = onBackgroundLight,
  onSurface = onBackgroundLight,
  onPrimary = surfaceLight,
  onSecondary = backgroundDark,
)

@Composable
internal fun PosterTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = if (darkTheme) {
    DarkColorPalette
  } else {
    LightColorPalette
  }

  val typography = if (darkTheme) {
    DarkTypography
  } else {
    LightTypography
  }

  MaterialTheme(
    colors = colors,
    typography = typography,
    content = content,
  )
}
