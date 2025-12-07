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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.model.MockUtil

private val radiusList = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

/**
 * Radius items screen showing a list of blur radius options with Disney thumbnail previews.
 *
 * @param onRadiusSelected Callback with the selected radius when a list item is tapped.
 * @param onBackClick Callback when the back button is pressed.
 */
@Composable
fun RadiusItemsScreen(onRadiusSelected: (Int) -> Unit, onBackClick: () -> Unit) {
  Scaffold(
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    topBar = {
      TopAppBar(
        title = { Text("Radius Items") },
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
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(16.dp),
    ) {
      items(radiusList) { radius ->
        RadiusItem(
          radius = radius,
          onClick = { onRadiusSelected(radius) },
        )
      }
    }
  }
}

/**
 * A clickable card representing a blur radius with a preview image and descriptive text.
 */
@Composable
private fun RadiusItem(radius: Int, onClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    elevation = 4.dp,
    shape = RoundedCornerShape(12.dp),
    backgroundColor = MaterialTheme.colors.surface,
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(RoundedCornerShape(8.dp)),
      ) {
        CoilImage(
          modifier = Modifier
            .fillMaxSize()
            .cloudy(radius = radius),
          imageModel = { poster.image },
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Radius: $radius",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colors.onSurface,
        )
        Text(
          text = if (radius == 0) "No blur" else "Sigma: ${radius / 2.0f}",
          fontSize = 14.sp,
          color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
      }

      Text(
        text = "->",
        fontSize = 24.sp,
        color = MaterialTheme.colors.primary,
      )
    }
  }
}
