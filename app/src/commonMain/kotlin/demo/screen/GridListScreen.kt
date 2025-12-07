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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import demo.component.GridPosterItem
import demo.model.MockUtil

/**
 * Grid list screen showing Disney posters in a 2-column grid with static blur.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
fun GridListScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }

  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Grid List") },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
        navigationIcon = {
          TextButton(onClick = onBackClick) {
            Text(
              text = "<-",
              fontSize = 20.sp,
              color = MaterialTheme.colors.onPrimary,
            )
          }
        },
      )
    },
    backgroundColor = MaterialTheme.colors.background,
  ) { paddingValues ->
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(posters) { poster ->
        GridPosterItem(poster = poster, blurRadius = 15)
      }
    }
  }
}
