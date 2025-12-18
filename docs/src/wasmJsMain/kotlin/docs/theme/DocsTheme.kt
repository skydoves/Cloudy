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
package docs.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class DocsColors(
  val background: Color,
  val surface: Color,
  val surfaceVariant: Color,
  val primary: Color,
  val primaryVariant: Color,
  val secondary: Color,
  val onBackground: Color,
  val onSurface: Color,
  val onSurfaceVariant: Color,
  val codeBackground: Color,
  val codeForeground: Color,
  val codeKeyword: Color,
  val codeString: Color,
  val codeNumber: Color,
  val codeComment: Color,
  val sidebarBackground: Color,
  val sidebarHover: Color,
  val sidebarActive: Color,
  val divider: Color,
  val success: Color,
  val warning: Color,
  val error: Color,
)

val DarkDocsColors = DocsColors(
  background = Color(0xFF121212),
  surface = Color(0xFF1E1E1E),
  surfaceVariant = Color(0xFF2D2D2D),
  primary = Color(0xFFBB86FC),
  primaryVariant = Color(0xFF6200EE),
  secondary = Color(0xFF03DAC6),
  onBackground = Color(0xFFE0E0E0),
  onSurface = Color(0xFFE0E0E0),
  onSurfaceVariant = Color(0xFF9E9E9E),
  codeBackground = Color(0xFF1A1A1A),
  codeForeground = Color(0xFFE0E0E0),
  codeKeyword = Color(0xFFCC7832),
  codeString = Color(0xFF6A8759),
  codeNumber = Color(0xFF6897BB),
  codeComment = Color(0xFF808080),
  sidebarBackground = Color(0xFF1A1A1A),
  sidebarHover = Color(0xFF2D2D2D),
  sidebarActive = Color(0xFF6200EE).copy(alpha = 0.2f),
  divider = Color(0xFF333333),
  success = Color(0xFF4CAF50),
  warning = Color(0xFFFFC107),
  error = Color(0xFFF44336),
)

val LightDocsColors = DocsColors(
  background = Color(0xFFF5F5F5),
  surface = Color(0xFFFFFFFF),
  surfaceVariant = Color(0xFFE8E8E8),
  primary = Color(0xFF6200EE),
  primaryVariant = Color(0xFF3700B3),
  secondary = Color(0xFF018786),
  onBackground = Color(0xFF1C1B1F),
  onSurface = Color(0xFF1C1B1F),
  onSurfaceVariant = Color(0xFF49454F),
  codeBackground = Color(0xFFF0F0F0),
  codeForeground = Color(0xFF1C1B1F),
  codeKeyword = Color(0xFFAF5F00),
  codeString = Color(0xFF2E7D32),
  codeNumber = Color(0xFF1565C0),
  codeComment = Color(0xFF6B6B6B),
  sidebarBackground = Color(0xFFFFFFFF),
  sidebarHover = Color(0xFFE8E8E8),
  sidebarActive = Color(0xFF6200EE).copy(alpha = 0.12f),
  divider = Color(0xFFE0E0E0),
  success = Color(0xFF2E7D32),
  warning = Color(0xFFF9A825),
  error = Color(0xFFC62828),
)

data class DocsTypography(
  val h1: TextStyle = TextStyle(
    fontSize = 32.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 40.sp,
  ),
  val h2: TextStyle = TextStyle(
    fontSize = 24.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 32.sp,
  ),
  val h3: TextStyle = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 28.sp,
  ),
  val body: TextStyle = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 24.sp,
  ),
  val bodySmall: TextStyle = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 20.sp,
  ),
  val code: TextStyle = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 20.sp,
    fontFamily = FontFamily.Monospace,
  ),
  val caption: TextStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 16.sp,
  ),
)

val LocalDocsColors = staticCompositionLocalOf { DarkDocsColors }
val LocalDocsTypography = staticCompositionLocalOf { DocsTypography() }
val LocalIsDarkTheme = staticCompositionLocalOf { true }

object DocsTheme {
  val colors: DocsColors
    @Composable get() = LocalDocsColors.current

  val typography: DocsTypography
    @Composable get() = LocalDocsTypography.current

  val isDarkTheme: Boolean
    @Composable get() = LocalIsDarkTheme.current
}

@Composable
fun DocsTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
  val colors = if (darkTheme) DarkDocsColors else LightDocsColors
  val typography = DocsTypography()

  val materialColorScheme = if (darkTheme) {
    darkColorScheme(
      primary = colors.primary,
      secondary = colors.secondary,
      background = colors.background,
      surface = colors.surface,
      onPrimary = Color.White,
      onSecondary = Color.Black,
      onBackground = colors.onBackground,
      onSurface = colors.onSurface,
    )
  } else {
    lightColorScheme(
      primary = colors.primary,
      secondary = colors.secondary,
      background = colors.background,
      surface = colors.surface,
      onPrimary = Color.White,
      onSecondary = Color.White,
      onBackground = colors.onBackground,
      onSurface = colors.onSurface,
    )
  }

  CompositionLocalProvider(
    LocalDocsColors provides colors,
    LocalDocsTypography provides typography,
    LocalIsDarkTheme provides darkTheme,
  ) {
    MaterialTheme(
      colorScheme = materialColorScheme,
      content = content,
    )
  }
}
