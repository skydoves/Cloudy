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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import demo.component.CollapsingAppBarScaffold
import demo.component.GridPosterItem
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Grid list screen showing Disney posters in a 2-column grid with static blur.
 * Content is constrained to a maximum width for better appearance on large screens.
 * Features a collapsing large app bar that shrinks on scroll.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridListScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }

  CollapsingAppBarScaffold(
    title = "Grid List",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.itemSpacing),
        horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
      ) {
        items(posters) { poster ->
          GridPosterItem(poster = poster, blurRadius = 15)
        }
      }
    }
  }
}
