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
package demo

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import demo.screen.BlurAppBarGridScreen
import demo.screen.BlurLightScreen
import demo.screen.GridListScreen
import demo.screen.GyroLightScreen
import demo.screen.InteractiveSliderScreen
import demo.screen.Issue112BottomNavScreen
import demo.screen.LiquidGlassDemoScreen
import demo.screen.MenuHomeScreen
import demo.screen.MirageScreen
import demo.screen.MirageSkyScreen
import demo.screen.ProgressiveBlurDemoScreen
import demo.screen.RadiusDetailScreen
import demo.screen.RadiusItemsScreen
import demo.screen.TransformLightScreen
import demo.theme.PosterTheme

/**
 * Hosts the Cloudy demo UI with menu-driven navigation to multiple blur demo scenarios.
 *
 * Uses Navigation3's NavDisplay for type-safe navigation between screens.
 * The content is wrapped in PosterTheme and transitions between views use animated enter/exit transitions.
 */
@Composable
fun CloudyDemoApp() {
  PosterTheme {
    val backStack = remember { mutableStateListOf<Route>(Route.MenuHome) }

    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryProvider = { route ->
        when (route) {
          is Route.MenuHome -> NavEntry(route) {
            MenuHomeScreen(
              onGridListClick = { backStack.add(Route.GridList) },
              onRadiusItemsClick = { backStack.add(Route.RadiusItems) },
              onBlurAppBarGridClick = { backStack.add(Route.BlurAppBarGrid) },
              onInteractiveSliderClick = { backStack.add(Route.InteractiveSlider) },
              onProgressiveBlurClick = { backStack.add(Route.ProgressiveBlur) },
              onLiquidGlassClick = { backStack.add(Route.LiquidGlass) },
              onIssue112Click = { backStack.add(Route.Issue112BottomNav) },
              onGyroLightClick = { backStack.add(Route.GyroLight) },
              onTransformLightClick = { backStack.add(Route.TransformLight) },
              onBlurLightClick = { backStack.add(Route.BlurLight) },
              onMirageClick = { backStack.add(Route.Mirage) },
              onMirageSkyClick = { backStack.add(Route.MirageSky) },
            )
          }

          is Route.GridList -> NavEntry(route) {
            GridListScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.RadiusItems -> NavEntry(route) {
            RadiusItemsScreen(
              onRadiusSelected = { radius -> backStack.add(Route.RadiusDetail(radius)) },
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.RadiusDetail -> NavEntry(route) {
            RadiusDetailScreen(
              radius = route.radius,
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.BlurAppBarGrid -> NavEntry(route) {
            BlurAppBarGridScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.InteractiveSlider -> NavEntry(route) {
            InteractiveSliderScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.ProgressiveBlur -> NavEntry(route) {
            ProgressiveBlurDemoScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.LiquidGlass -> NavEntry(route) {
            LiquidGlassDemoScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.Issue112BottomNav -> NavEntry(route) {
            Issue112BottomNavScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.GyroLight -> NavEntry(route) {
            GyroLightScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.TransformLight -> NavEntry(route) {
            TransformLightScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.BlurLight -> NavEntry(route) {
            BlurLightScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.Mirage -> NavEntry(route) {
            MirageScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }

          is Route.MirageSky -> NavEntry(route) {
            MirageSkyScreen(
              onBackClick = { backStack.removeLastOrNull() },
            )
          }
        }
      },
      transitionSpec = {
        slideInHorizontally { it } + fadeIn() togetherWith
          slideOutHorizontally { -it } + fadeOut()
      },
      popTransitionSpec = {
        slideInHorizontally { -it } + fadeIn() togetherWith
          slideOutHorizontally { it } + fadeOut()
      },
    )
  }
}
