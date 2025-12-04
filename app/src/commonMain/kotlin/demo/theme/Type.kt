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

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val LightTypography: Typography = Typography(
  h1 = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
  ),
  body1 = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
  ),
  caption = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
  ),
)

internal val DarkTypography: Typography = Typography(
  h1 = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
  ),
  body1 = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
  ),
  caption = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
  ),
)
