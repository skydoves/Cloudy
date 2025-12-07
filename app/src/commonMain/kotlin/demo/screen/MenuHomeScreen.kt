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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.model.MockUtil
import demo.model.Poster

/**
 * Menu home screen displaying a list of blur demo scenarios.
 *
 * @param onGridListClick Callback when the Grid List demo is selected.
 * @param onRadiusItemsClick Callback when the Radius Items demo is selected.
 * @param onBlurAppBarGridClick Callback when the Blur AppBar Grid demo is selected.
 */
@Composable
fun MenuHomeScreen(
  onGridListClick: () -> Unit,
  onRadiusItemsClick: () -> Unit,
  onBlurAppBarGridClick: () -> Unit,
) {
  val posters = remember { MockUtil.getMockPosters() }

  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Cloudy Demos") },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary,
      )
    },
    backgroundColor = MaterialTheme.colors.background,
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(16.dp),
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
    }
  }
}

/**
 * A clickable menu card with a poster background and title/description overlay.
 */
@Composable
private fun MenuCard(title: String, description: String, poster: Poster, onClick: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(180.dp)
      .clickable(onClick = onClick),
    elevation = 4.dp,
    shape = RoundedCornerShape(12.dp),
  ) {
    Box {
      CoilImage(
        modifier = Modifier
          .fillMaxSize()
          .cloudy(radius = 10),
        imageModel = { poster.image },
      )

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colors.surface.copy(alpha = 0.7f)),
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = title,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = description,
          fontSize = 14.sp,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        )
      }

      Text(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 16.dp),
        text = "->",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.primary,
      )
    }
  }
}
