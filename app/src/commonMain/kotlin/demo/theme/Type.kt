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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A proper type scale: real weights, line heights and letter spacing instead
// of everything being FontWeight.Normal at three sizes.
private val PosterTypography: Typography = Typography(
  headlineLarge = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.5).sp,
  ),
  headlineSmall = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
  ),
  titleLarge = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.sp,
  ),
  titleMedium = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.1.sp,
  ),
  bodyMedium = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
  ),
  labelLarge = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.3.sp,
  ),
  labelSmall = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.8.sp,
  ),
)

internal val LightTypography: Typography = PosterTypography

internal val DarkTypography: Typography = PosterTypography
