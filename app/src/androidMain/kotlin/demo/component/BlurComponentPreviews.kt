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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.skydoves.cloudydemo.R
import demo.theme.PosterTheme

@Preview(
  name = "GridPosterItem - API 31+ (RenderEffect)",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun GridPosterItemApi31Preview() {
  PosterTheme {
    Box(modifier = Modifier.width(200.dp)) {
      GridPosterItemLayout(
        name = "Frozen (API 31+)",
        blurRadius = 10,
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
  name = "MenuCard - API 31+ (RenderEffect)",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun MenuCardApi31Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "RenderEffect Blur",
        description = "Hardware-accelerated blur on API 31+",
        onClick = {},
        blurRadius = 10,
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
  name = "GridPosterItem - API 30 (CPU Blur)",
  showBackground = true,
  apiLevel = 30,
)
@Composable
private fun GridPosterItemApi30Preview() {
  PosterTheme {
    Box(modifier = Modifier.width(200.dp)) {
      GridPosterItemLayout(
        name = "Moana (API 30)",
        blurRadius = 10,
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
  name = "MenuCard - API 30 (CPU Blur)",
  showBackground = true,
  apiLevel = 30,
)
@Composable
private fun MenuCardApi30Preview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "CPU Blur",
        description = "Software blur fallback on API 30-",
        onClick = {},
        blurRadius = 10,
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
  name = "GridPosterItem - No Blur",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun GridPosterItemNoBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.width(200.dp)) {
      GridPosterItemLayout(
        name = "No Blur",
        blurRadius = 0,
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
  name = "MenuCard - High Blur (radius=25)",
  showBackground = true,
  apiLevel = 31,
)
@Composable
private fun MenuCardHighBlurPreview() {
  PosterTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "High Blur",
        description = "Strong blur effect (radius=25)",
        onClick = {},
        blurRadius = 25,
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
  name = "MenuCard - Dark Theme API 31+",
  showBackground = true,
  backgroundColor = 0xFF1A1A2E,
  apiLevel = 31,
)
@Composable
private fun MenuCardDarkApi31Preview() {
  PosterTheme(darkTheme = true) {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "Dark Theme",
        description = "Menu card in dark mode",
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
  name = "MenuCard - Dark Theme API 30",
  showBackground = true,
  backgroundColor = 0xFF1A1A2E,
  apiLevel = 30,
)
@Composable
private fun MenuCardDarkApi30Preview() {
  PosterTheme(darkTheme = true) {
    Box(modifier = Modifier.padding(16.dp)) {
      MenuCardLayout(
        title = "Dark Theme",
        description = "Menu card in dark mode (API 30)",
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
