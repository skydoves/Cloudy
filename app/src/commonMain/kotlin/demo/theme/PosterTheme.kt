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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme as Material3Theme

// Material 2 color palettes
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

// Material 3 color schemes
private val DarkColorScheme = darkColorScheme(
  background = backgroundDark,
  surface = surfaceDark,
  surfaceContainer = surfaceDark,
  primary = disneyBluePrimary,
  secondary = disneyGold,
  tertiary = disneyGold,
  onBackground = onBackgroundDark,
  onSurface = onBackgroundDark,
  onPrimary = onBackgroundDark,
  onSecondary = backgroundDark,
)

private val LightColorScheme = lightColorScheme(
  background = backgroundLight,
  surface = surfaceLight,
  surfaceContainer = surfaceLight,
  primary = disneyBluePrimary,
  secondary = disneyGold,
  tertiary = disneyGold,
  onBackground = onBackgroundLight,
  onSurface = onBackgroundLight,
  onPrimary = surfaceLight,
  onSecondary = backgroundDark,
)

/**
 * Applies the app's poster styling (colors and typography) to the given composable content.
 * Includes both Material 2 and Material 3 themes for compatibility.
 *
 * When `darkTheme` is true the dark color palette and typography are used; otherwise the light
 * palette and typography are applied.
 *
 * @param darkTheme Controls whether dark-theme colors and typography are applied. Defaults to the
 * system dark theme setting.
 * @param content Composable content to render within the themed Material surface.
 */
@Composable
internal fun PosterTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = if (darkTheme) DarkColorPalette else LightColorPalette
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val typography = if (darkTheme) DarkTypography else LightTypography

  Material3Theme(colorScheme = colorScheme) {
    MaterialTheme(
      colors = colors,
      typography = typography,
      content = content,
    )
  }
}
