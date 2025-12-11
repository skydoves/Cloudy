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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skydoves.cloudy.cloudy
import com.skydoves.landscapist.coil3.CoilImage
import demo.component.CollapsingAppBarScaffold
import demo.component.MaxWidthContainer
import demo.model.MockUtil
import demo.model.Poster
import demo.theme.Dimens

/**
 * Interactive slider screen that allows users to adjust blur radius in real-time.
 * Content is constrained to a maximum width for better appearance on large screens.
 * Features a collapsing large app bar that shrinks on scroll.
 *
 * @param onBackClick Callback when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveSliderScreen(onBackClick: () -> Unit) {
  val poster = remember { MockUtil.getMockPoster() }
  var sliderValue by remember { mutableFloatStateOf(15f) }
  val radius = sliderValue.toInt()

  CollapsingAppBarScaffold(
    title = "Interactive Slider",
    onBackClick = onBackClick,
  ) { paddingValues, _ ->
    MaxWidthContainer(modifier = Modifier.padding(paddingValues)) {
      InteractiveSliderContent(
        radius = radius,
        sliderValue = sliderValue,
        onSliderValueChange = { sliderValue = it },
      ) { modifier ->
        CoilImage(
          modifier = modifier,
          imageModel = { poster.image },
        )
      }
    }
  }
}

/**
 * Content layout for interactive slider screen, separated for preview support.
 *
 * @param radius Current blur radius value.
 * @param sliderValue Current slider value.
 * @param onSliderValueChange Callback when slider value changes.
 * @param imageContent Slot for the preview image with blur modifier applied.
 */
@Composable
internal fun InteractiveSliderContent(
  radius: Int,
  sliderValue: Float,
  onSliderValueChange: (Float) -> Unit,
  imageContent: @Composable (Modifier) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(Dimens.contentPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    BlurPreviewCard(radius = radius, imageContent = imageContent)

    Spacer(modifier = Modifier.height(24.dp))

    BlurSliderCard(
      sliderValue = sliderValue,
      onSliderValueChange = onSliderValueChange,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "Drag the slider to adjust the blur radius in real-time. " +
        "This demonstrates Cloudy's ability to dynamically change blur intensity.",
      fontSize = 14.sp,
      color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
      textAlign = TextAlign.Center,
    )
  }
}

/**
 * Card displaying a live blur preview image.
 *
 * @param radius The blur radius to apply and display.
 * @param imageContent Slot for the image content with blur modifier applied.
 */
@Composable
internal fun BlurPreviewCard(radius: Int, imageContent: @Composable (Modifier) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = Dimens.cardElevation,
    shape = RoundedCornerShape(Dimens.cardCornerRadius),
    backgroundColor = MaterialTheme.colors.surface,
  ) {
    Column(
      modifier = Modifier.padding(Dimens.contentPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "Live Blur Preview",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(Dimens.contentPadding))

      imageContent(
        Modifier
          .size(280.dp)
          .clip(RoundedCornerShape(Dimens.itemSpacing))
          .background(
            MaterialTheme.colors.surface,
            RoundedCornerShape(Dimens.itemSpacing),
          )
          .cloudy(radius = radius),
      )

      Spacer(modifier = Modifier.height(Dimens.contentPadding))

      Text(
        text = "Radius: $radius",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.secondary,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = if (radius == 0) "No blur effect" else "Sigma: ${radius / 2.0f}",
        fontSize = 14.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
      )
    }
  }
}

/**
 * Card with a slider control for adjusting blur radius.
 *
 * @param sliderValue Current slider value.
 * @param onSliderValueChange Callback when slider value changes.
 */
@Composable
internal fun BlurSliderCard(sliderValue: Float, onSliderValueChange: (Float) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = Dimens.cardElevation,
    shape = RoundedCornerShape(Dimens.cardCornerRadius),
    backgroundColor = MaterialTheme.colors.surface,
  ) {
    Column(
      modifier = Modifier.padding(Dimens.contentPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "Adjust Blur Radius",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colors.onSurface,
      )

      Spacer(modifier = Modifier.height(Dimens.itemSpacing))

      Slider(
        value = sliderValue,
        onValueChange = onSliderValueChange,
        valueRange = 0f..100f,
        steps = 19,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
          thumbColor = MaterialTheme.colors.secondary,
          activeTrackColor = MaterialTheme.colors.secondary,
          inactiveTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
        ),
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "0 - 100",
        fontSize = 12.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
      )
    }
  }
}
