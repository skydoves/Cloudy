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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class DocsColors(
  val background: Color = Color(0xFF121212),
  val surface: Color = Color(0xFF1E1E1E),
  val surfaceVariant: Color = Color(0xFF2D2D2D),
  val primary: Color = Color(0xFFBB86FC),
  val primaryVariant: Color = Color(0xFF6200EE),
  val secondary: Color = Color(0xFF03DAC6),
  val onBackground: Color = Color(0xFFE0E0E0),
  val onSurface: Color = Color(0xFFE0E0E0),
  val onSurfaceVariant: Color = Color(0xFF9E9E9E),
  val codeBackground: Color = Color(0xFF1A1A1A),
  val codeForeground: Color = Color(0xFFE0E0E0),
  val codeKeyword: Color = Color(0xFFCC7832),
  val codeString: Color = Color(0xFF6A8759),
  val codeNumber: Color = Color(0xFF6897BB),
  val codeComment: Color = Color(0xFF808080),
  val sidebarBackground: Color = Color(0xFF1A1A1A),
  val sidebarHover: Color = Color(0xFF2D2D2D),
  val sidebarActive: Color = Color(0xFF6200EE).copy(alpha = 0.2f),
  val divider: Color = Color(0xFF333333),
  val success: Color = Color(0xFF4CAF50),
  val warning: Color = Color(0xFFFFC107),
  val error: Color = Color(0xFFF44336),
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

val LocalDocsColors = staticCompositionLocalOf { DocsColors() }
val LocalDocsTypography = staticCompositionLocalOf { DocsTypography() }

object DocsTheme {
  val colors: DocsColors
    @Composable get() = LocalDocsColors.current

  val typography: DocsTypography
    @Composable get() = LocalDocsTypography.current
}

@Composable
fun DocsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colors = DocsColors()
  val typography = DocsTypography()

  val materialColorScheme = darkColorScheme(
    primary = colors.primary,
    secondary = colors.secondary,
    background = colors.background,
    surface = colors.surface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = colors.onBackground,
    onSurface = colors.onSurface,
  )

  CompositionLocalProvider(
    LocalDocsColors provides colors,
    LocalDocsTypography provides typography,
  ) {
    MaterialTheme(
      colorScheme = materialColorScheme,
      content = content,
    )
  }
}
