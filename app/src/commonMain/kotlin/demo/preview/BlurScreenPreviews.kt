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
package demo.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cloudydemo.app.generated.resources.Res
import cloudydemo.app.generated.resources.poster
import demo.screen.BlurPreviewCard
import demo.screen.BlurSliderCard
import demo.screen.RadiusDetailCardLayout
import demo.screen.RadiusItemLayout
import demo.theme.PosterTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun RadiusItemPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusItemLayout(
        radius = 15,
        onClick = {},
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun RadiusItemNoBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusItemLayout(
        radius = 0,
        onClick = {},
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun BlurPreviewCardPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      BlurPreviewCard(radius = 20) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun BlurSliderCardPreview() {
  PosterTheme {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      BlurSliderCard(
        sliderValue = 50f,
        onSliderValueChange = {},
      )
    }
  }
}

@Preview
@Composable
private fun RadiusDetailCardStaticPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusDetailCardLayout(
        title = "Static Blur",
        radius = 25,
        animated = false,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun RadiusDetailCardAnimatedPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusDetailCardLayout(
        title = "Animated Blur (0 -> 25)",
        radius = 25,
        animated = true,
      ) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview
@Composable
private fun BlurPreviewCardDarkPreview() {
  PosterTheme(darkTheme = true) {
    Box(modifier = Modifier.padding(16.dp)) {
      BlurPreviewCard(radius = 15) { modifier ->
        Image(
          painter = painterResource(Res.drawable.poster),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}
