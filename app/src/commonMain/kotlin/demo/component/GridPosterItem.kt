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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.model.Poster
import demo.theme.Dimens

/**
 * A grid item displaying a poster with applied blur effect.
 * Features rounded corners and gradient overlay for text readability.
 *
 * @param poster The poster to display.
 * @param blurRadius The blur radius to apply to the poster image.
 */
@Composable
internal fun GridPosterItem(poster: Poster, blurRadius: Int) {
  GridPosterItemLayout(
    name = poster.name,
    blurRadius = blurRadius,
  ) { modifier ->
    CoilImage(
      modifier = modifier,
      imageModel = { poster.image },
    )
  }
}

/**
 * Layout component for grid poster item, separated for preview support.
 *
 * @param name The name to display on the poster.
 * @param blurRadius The blur radius to apply.
 * @param imageContent Slot for the image content with blur modifier applied.
 */
@Composable
internal fun GridPosterItemLayout(
  name: String,
  blurRadius: Int,
  imageContent: @Composable (Modifier) -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(0.7f),
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
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.7f),
              ),
            ),
          )
          .padding(Dimens.contentPadding),
      ) {
        Text(
          text = name,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color.White,
          maxLines = 2,
        )
      }
    }
  }
}
