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
package demo.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.model.Poster
import demo.theme.Dimens

/**
 * A clickable menu card with a poster background, gradient overlay, and title/description.
 * Uses Material Design ChevronRight icon instead of text arrow.
 *
 * @param title The title to display on the card.
 * @param description A brief description of the menu item.
 * @param poster The poster to use as the background image.
 * @param onClick Callback when the card is clicked.
 * @param modifier Modifier to apply to the card.
 */
@Composable
internal fun MenuCard(
  title: String,
  description: String,
  poster: Poster,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  MenuCardLayout(
    title = title,
    description = description,
    onClick = onClick,
    modifier = modifier,
  ) { imageModifier ->
    CoilImage(
      modifier = imageModifier,
      imageModel = { poster.image },
    )
  }
}

/**
 * Layout component for menu card, separated for preview support.
 *
 * @param title The title to display on the card.
 * @param description A brief description of the menu item.
 * @param onClick Callback when the card is clicked.
 * @param modifier Modifier to apply to the card.
 * @param blurRadius The blur radius to apply to the background image.
 * @param imageContent Slot for the background image content with blur modifier applied.
 */
@Composable
internal fun MenuCardLayout(
  title: String,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  blurRadius: Int = 10,
  imageContent: @Composable (Modifier) -> Unit,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .height(180.dp)
      .clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.cardElevation),
    shape = RoundedCornerShape(Dimens.cardCornerRadius),
  ) {
    Box {
      imageContent(
        Modifier
          .fillMaxSize()
          .cloudy(radius = blurRadius),
      )

      // Gradient overlay for better text readability
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.3f),
                Color.Black.copy(alpha = 0.7f),
              ),
            ),
          ),
      )

      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(Dimens.contentPadding),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = description,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
          )
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = "Navigate",
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.padding(start = 8.dp),
        )
      }
    }
  }
}
