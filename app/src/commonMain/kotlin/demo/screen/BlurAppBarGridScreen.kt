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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import demo.component.BackButton
import demo.component.GridPosterItem
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

/**
 * Grid screen with a frosted glass app bar overlay demonstrating background blur (backdrop blur).
 * Content is constrained to a maximum width for better appearance on large screens.
 *
 * This screen demonstrates the new background blur feature using [rememberSky], [sky], and [cloudy].
 * The app bar blurs the grid content behind it, creating a glassmorphism effect.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
fun BlurAppBarGridScreen(onBackClick: () -> Unit) {
  val posters = remember { MockUtil.getMockPosters() }
  val sky = rememberSky()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    // Background content that will be captured for blur
    MaxWidthContainer(
      modifier = Modifier.sky(sky),
    ) {
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          top = 72.dp,
          start = Dimens.itemSpacing,
          end = Dimens.itemSpacing,
          bottom = Dimens.itemSpacing,
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
      ) {
        items(posters + posters) { poster ->
          GridPosterItem(poster = poster, blurRadius = 0)
        }
      }
    }

    // Frosted glass app bar with background blur
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .cloudy(
          sky = sky,
          radius = 20,
          tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        )
        .background(Color.Transparent),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BackButton(
          onClick = onBackClick,
          tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Blur AppBar",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
