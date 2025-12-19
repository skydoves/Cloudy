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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.theme.Dimens

private val radiusList = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

/**
 * Radius items screen showing a list of blur radius options with Disney thumbnail previews.
 * Content is constrained to a maximum width for better appearance on large screens.
 * Features a collapsing large app bar that shrinks on scroll.
 *
 * @param onRadiusSelected Callback with the selected radius when a list item is tapped.
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadiusItemsScreen(onRadiusSelected: (Int) -> Unit, onBackClick: () -> Unit) {
  CollapsingAppBarScaffold(
    title = "Radius Items",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.itemSpacing),
        contentPadding = PaddingValues(Dimens.contentPadding),
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
}

/**
 * A clickable card representing a blur radius with a preview image and descriptive text.
 * Features ChevronRight icon for navigation indication.
 */
@Composable
private fun RadiusItem(radius: Int, onClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }

  RadiusItemLayout(
    radius = radius,
    onClick = onClick,
  ) { modifier ->
    CoilImage(
      modifier = modifier,
      imageModel = { poster.image },
    )
  }
}

/**
 * Layout component for radius item, separated for preview support.
 *
 * @param radius The blur radius to display and apply.
 * @param onClick Callback when the item is clicked.
 * @param imageContent Slot for the thumbnail image with blur modifier applied.
 */
@Composable
internal fun RadiusItemLayout(
  radius: Int,
  onClick: () -> Unit,
  imageContent: @Composable (Modifier) -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
    shape = RoundedCornerShape(Dimens.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Row(
      modifier = Modifier.padding(Dimens.itemSpacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(RoundedCornerShape(Dimens.itemSpacing)),
      ) {
        imageContent(
          Modifier
            .fillMaxSize()
            .cloudy(radius = radius),
        )
      }

      Spacer(modifier = Modifier.width(Dimens.contentPadding))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Radius: $radius",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = if (radius == 0) "No blur" else "Sigma: ${radius / 2.0f}",
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
      }

      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Navigate",
        tint = MaterialTheme.colorScheme.secondary,
      )
    }
  }
}