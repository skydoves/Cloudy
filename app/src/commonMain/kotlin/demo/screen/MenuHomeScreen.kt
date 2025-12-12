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
package demo.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.component.MenuCard
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Menu home screen displaying a list of blur demo scenarios.
 * Content is constrained to a maximum width for better appearance on large screens.
 * Features a collapsing large app bar that shrinks on scroll.
 *
 * @param onGridListClick Callback when the Grid List demo is selected.
 * @param onRadiusItemsClick Callback when the Radius Items demo is selected.
 * @param onBlurAppBarGridClick Callback when the Blur AppBar Grid demo is selected.
 * @param onInteractiveSliderClick Callback when the Interactive Slider demo is selected.
 * @param onProgressiveBlurClick Callback when the Progressive Blur demo is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuHomeScreen(
  onGridListClick: () -> Unit,
  onRadiusItemsClick: () -> Unit,
  onBlurAppBarGridClick: () -> Unit,
  onInteractiveSliderClick: () -> Unit,
  onProgressiveBlurClick: () -> Unit,
) {
  val posters = remember { MockUtil.getMockPosters() }

  CollapsingAppBarScaffold(title = "Cloudy Demos") { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.contentPadding),
        contentPadding = PaddingValues(Dimens.contentPadding),
      ) {
        item {
          MenuCard(
            title = "Grid List",
            description = "Disney posters in a 2-column grid with static blur",
            poster = posters[0],
            onClick = onGridListClick,
          )
        }
        item {
          MenuCard(
            title = "Radius Items",
            description = "Explore different blur radii with animated detail view",
            poster = posters[1],
            onClick = onRadiusItemsClick,
          )
        }
        item {
          MenuCard(
            title = "Blur AppBar",
            description = "Grid with frosted glass app bar overlay",
            poster = posters[2],
            onClick = onBlurAppBarGridClick,
          )
        }
        item {
          MenuCard(
            title = "Interactive Slider",
            description = "Adjust blur radius in real-time with a slider",
            poster = posters[3],
            onClick = onInteractiveSliderClick,
          )
        }
        item {
          MenuCard(
            title = "Progressive Blur",
            description = "Gradient blur effects: Top-to-Bottom, Bottom-to-Top, Edges",
            poster = posters[4],
            onClick = onProgressiveBlurClick,
          )
        }
      }
    }
  }
}
