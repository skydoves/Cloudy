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

import androidx.compose.ui.graphics.Color

// "Magic hour" palette — Disney-inspired, but tuned down from neon so the
// blur effect stays the hero of every screen.

// Primary — a softer magic blue (calmer than the old Disney+ 0063E5).
internal val disneyBluePrimary: Color = Color(0xFF5B9BFF)
internal val disneyBlueDark: Color = Color(0xFF1B3A6B)

// Secondary — warm "pixie dust" gold, no longer a harsh neon yellow.
internal val disneyGold: Color = Color(0xFFF4C66E)

// Tertiary — starlight cyan for subtle sparkle accents.
internal val pixieCyan: Color = Color(0xFF8FD0FF)

// Backgrounds — deep night-sky indigo (dark) / airy daydream (light).
internal val backgroundDark: Color = Color(0xFF0A0E24)
internal val backgroundLight: Color = Color(0xFFF3F6FD)

// Night-sky gradient stops (dark theme app background).
internal val nightSkyTop: Color = Color(0xFF1C2150) // royal indigo
internal val nightSkyBottom: Color = Color(0xFF080B1E) // near-black navy

// Daydream gradient stops (light theme app background).
internal val daySkyTop: Color = Color(0xFFFBFCFF)
internal val daySkyBottom: Color = Color(0xFFE6EEFB)

// Surfaces — glassy elevated panels.
internal val surfaceDark: Color = Color(0xFF161B3C)
internal val surfaceLight: Color = Color(0xFFFFFFFF)

// Hairline borders that give the glass cards a crisp edge.
internal val borderDark: Color = Color(0x24FFFFFF)
internal val borderLight: Color = Color(0x14123A6B)

// On-colors (text / icons).
internal val onBackgroundLight: Color = Color(0xFF131A2E) // dark navy on light
internal val onBackgroundDark: Color = Color(0xFFEAF0FF) // soft white on dark
