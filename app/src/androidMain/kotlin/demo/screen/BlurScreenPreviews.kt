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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.skydoves.cloudydemo.R
import demo.theme.PosterTheme

@Preview(
  name = "RadiusItem - API 31+ (RenderEffect)",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun RadiusItemApi31Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusItemLayout(
        radius = 15,
        onClick = {},
      ) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "BlurPreviewCard - API 31+ (RenderEffect)",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun BlurPreviewCardApi31Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      BlurPreviewCard(radius = 20) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "RadiusDetailCard - API 31+ (RenderEffect)",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun RadiusDetailCardApi31Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusDetailCardLayout(
        title = "Static Blur (API 31+)",
        radius = 25,
        animated = false,
      ) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "RadiusItem - API 30 (CPU Blur)",
  showBackground = true,
  apiLevel = 30,
)
@Composable
private fun RadiusItemApi30Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusItemLayout(
        radius = 15,
        onClick = {},
      ) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "BlurPreviewCard - API 30 (CPU Blur)",
  showBackground = true,
  apiLevel = 30,
)
@Composable
private fun BlurPreviewCardApi30Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      BlurPreviewCard(radius = 20) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "RadiusDetailCard - API 30 (CPU Blur)",
  showBackground = true,
  apiLevel = 30,
)
@Composable
private fun RadiusDetailCardApi30Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusDetailCardLayout(
        title = "Static Blur (API 30)",
        radius = 25,
        animated = false,
      ) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "RadiusItem - No Blur",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun RadiusItemNoBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      RadiusItemLayout(
        radius = 0,
        onClick = {},
      ) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "BlurSliderCard",
  showBackground = true,
  apiLevel = 31,
)
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

@Preview(
  name = "RadiusDetailCard - Animated",
  showBackground = true,
  apiLevel = 31,
)
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
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "BlurPreviewCard - Dark API 31+",
  showBackground = true,
  backgroundColor = 0xFF1A1A2E,
  apiLevel = 31,
)
@Composable
private fun BlurPreviewCardDarkApi31Preview() {
  PosterTheme(darkTheme = true) {
    Box(modifier = Modifier.padding(16.dp)) {
      BlurPreviewCard(radius = 15) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Preview(
  name = "BlurPreviewCard - Dark API 30",
  showBackground = true,
  backgroundColor = 0xFF1A1A2E,
  apiLevel = 30,
)
@Composable
private fun BlurPreviewCardDarkApi30Preview() {
  PosterTheme(darkTheme = true) {
    Box(modifier = Modifier.padding(16.dp)) {
      BlurPreviewCard(radius = 15) { modifier ->
        Image(
          painter = painterResource(R.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = modifier,
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
}
