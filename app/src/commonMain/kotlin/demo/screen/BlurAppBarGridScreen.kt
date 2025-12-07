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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import demo.component.GridPosterItem
import demo.model.MockUtil

/**
 * Grid screen with a blurred app bar overlay demonstrating real-time background blur.
 *
 * NOTE: True backdrop blur (blurring content behind the app bar) is not yet supported in Cloudy.
 * The `cloudy` modifier only blurs content inside the composable, not content behind it.
 * This screen currently demonstrates the limitation and serves as a placeholder for future
 * backdrop blur functionality.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
fun BlurAppBarGridScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(top = 72.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(posters + posters) { poster ->
        GridPosterItem(poster = poster, blurRadius = 0)
      }
    }

    // TODO: Replace with backdrop blur when Cloudy supports it.
    // Currently, cloudy() only blurs content inside this Box, not the grid content behind it.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .cloudy(radius = 15)
        .background(MaterialTheme.colors.surface.copy(alpha = 0.3f)),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onBackClick) {
          Text(
            text = "<-",
            fontSize = 20.sp,
            color = MaterialTheme.colors.onSurface,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Blur AppBar",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
        )
      }
    }
  }
}
